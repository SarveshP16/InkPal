package com.inkpal.app.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** A chapter of an audiobook: its title and where it starts. */
data class Chapter(val title: String, val startMs: Long)

/**
 * Reads chapter markers from an M4B/MP4 file. Two layouts are in common use:
 * - a QuickTime chapter track: a text track the audio track points to (`tref/chap`), one
 *   text sample per chapter (what iTunes, ffmpeg and most converters write);
 * - a Nero `chpl` box in `moov/udta`.
 * Only the `moov` box and the chapter samples are read, never the audio.
 */
object M4bChapters {
    private const val TAG = "M4bChapters"
    private const val MAX_MOOV = 64 * 1024 * 1024

    fun read(context: Context, uri: Uri): List<Chapter> = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { parse(it) }
        }.orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read chapters", e)
        emptyList()
    }

    private class Box(val type: String, val data: ByteBuffer)

    /** Child boxes of a container payload. */
    private fun children(buf: ByteBuffer): List<Box> {
        val out = mutableListOf<Box>()
        val b = buf.duplicate().order(ByteOrder.BIG_ENDIAN)
        while (b.remaining() >= 8) {
            val start = b.position()
            var size = b.int.toLong() and 0xFFFFFFFFL
            val type = typeString(b)
            var header = 8
            if (size == 1L) {
                if (b.remaining() < 8) break
                size = b.long
                header = 16
            } else if (size == 0L) {
                size = (b.limit() - start).toLong()
            }
            if (size < header || start + size > b.limit()) break
            val payload = b.duplicate()
            payload.position(start + header)
            payload.limit((start + size).toInt())
            out += Box(type, payload.slice().order(ByteOrder.BIG_ENDIAN))
            b.position((start + size).toInt())
        }
        return out
    }

    private fun typeString(b: ByteBuffer): String {
        val bytes = ByteArray(4)
        b.get(bytes)
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun List<Box>.first(type: String) = firstOrNull { it.type == type }

    /** Finds the top-level `moov` box by walking the file's box headers. */
    private fun readMoov(ch: FileChannel): ByteBuffer? {
        val size = ch.size()
        var pos = 0L
        val header = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        while (pos + 8 <= size) {
            header.clear()
            ch.read(header, pos)
            header.flip()
            if (header.remaining() < 8) return null
            var boxSize = header.int.toLong() and 0xFFFFFFFFL
            val type = typeString(header)
            var headerLen = 8
            if (boxSize == 1L) {
                if (header.remaining() < 8) return null
                boxSize = header.long
                headerLen = 16
            } else if (boxSize == 0L) {
                boxSize = size - pos
            }
            if (boxSize < headerLen) return null
            if (type == "moov") {
                val len = boxSize - headerLen
                if (len > MAX_MOOV) return null
                val buf = ByteBuffer.allocate(len.toInt())
                var at = pos + headerLen
                while (buf.hasRemaining()) {
                    val n = ch.read(buf, at)
                    if (n <= 0) break
                    at += n
                }
                buf.flip()
                return buf.order(ByteOrder.BIG_ENDIAN)
            }
            pos += boxSize
        }
        return null
    }

    private fun parse(ch: FileChannel): List<Chapter> {
        val moov = readMoov(ch) ?: return emptyList()
        val boxes = children(moov)
        val fromTrack = chapterTrack(ch, boxes)
        if (fromTrack.size > 1) return fromTrack
        val fromChpl = boxes.first("udta")?.let { children(it.data).first("chpl") }?.let { nero(it.data) }.orEmpty()
        return if (fromChpl.size > 1) fromChpl else fromTrack
    }

    /** Nero chapters: version, flags, [reserved], count, then (start in 100ns, title) pairs. */
    private fun nero(b: ByteBuffer): List<Chapter> {
        val out = mutableListOf<Chapter>()
        if (b.remaining() < 5) return out
        val version = b.get().toInt()
        b.position(b.position() + 3)
        if (version == 1) b.position(b.position() + 4)
        val count = b.get().toInt() and 0xFF
        repeat(count) {
            if (b.remaining() < 9) return out
            val start = b.long
            val len = b.get().toInt() and 0xFF
            if (b.remaining() < len) return out
            val bytes = ByteArray(len)
            b.get(bytes)
            out += Chapter(String(bytes, Charsets.UTF_8).trim(), start / 10_000)
        }
        return out
    }

    private class Track(
        val id: Int,
        val handler: String,
        val timescale: Long,
        val chapterRefs: List<Int>,
        val stbl: List<Box>,
    )

    private fun track(trak: Box): Track? {
        val parts = children(trak.data)
        val tkhd = parts.first("tkhd")?.data ?: return null
        val version = tkhd.get(0).toInt()
        val id = tkhd.getInt(if (version == 1) 4 + 16 else 4 + 8)
        val refs = parts.first("tref")?.let { tref ->
            children(tref.data).first("chap")?.data?.let { chap ->
                (0 until chap.remaining() / 4).map { chap.getInt(it * 4) }
            }
        }.orEmpty()
        val mdia = parts.first("mdia")?.let { children(it.data) } ?: return null
        val mdhd = mdia.first("mdhd")?.data ?: return null
        val timescale = (if (mdhd.get(0).toInt() == 1) mdhd.getInt(4 + 16) else mdhd.getInt(4 + 8)).toLong() and 0xFFFFFFFFL
        val handler = mdia.first("hdlr")?.data?.let { h ->
            val bytes = ByteArray(4)
            h.position(8)
            h.get(bytes)
            String(bytes, Charsets.ISO_8859_1)
        }.orEmpty()
        val stbl = mdia.first("minf")?.let { children(it.data) }?.first("stbl")?.let { children(it.data) }.orEmpty()
        return Track(id, handler, timescale, refs, stbl)
    }

    private fun chapterTrack(ch: FileChannel, moov: List<Box>): List<Chapter> {
        val tracks = moov.filter { it.type == "trak" }.mapNotNull { track(it) }
        val refIds = tracks.flatMap { it.chapterRefs }.toSet()
        val text = tracks.firstOrNull { it.id in refIds }
            ?: tracks.firstOrNull { it.handler == "text" || it.handler == "sbtl" }
            ?: return emptyList()
        if (text.timescale <= 0) return emptyList()

        // Sample start times (stts).
        val starts = mutableListOf<Long>()
        text.stbl.first("stts")?.data?.let { b ->
            val count = b.getInt(4)
            var t = 0L
            for (i in 0 until count) {
                val n = b.getInt(8 + i * 8).toLong() and 0xFFFFFFFFL
                val delta = b.getInt(12 + i * 8).toLong() and 0xFFFFFFFFL
                for (k in 0 until n) {
                    starts += t
                    t += delta
                    if (starts.size > 5000) break
                }
            }
        } ?: return emptyList()

        // Sample sizes (stsz), chunk offsets (stco/co64) and samples per chunk (stsc).
        val sizes = text.stbl.first("stsz")?.data?.let { b ->
            val fixed = b.getInt(4)
            val count = b.getInt(8)
            List(count.coerceAtMost(5000)) { if (fixed != 0) fixed else b.getInt(12 + it * 4) }
        } ?: return emptyList()
        val offsets: List<Long> = text.stbl.first("stco")?.data?.let { b ->
            List(b.getInt(4).coerceAtMost(5000)) { b.getInt(8 + it * 4).toLong() and 0xFFFFFFFFL }
        } ?: text.stbl.first("co64")?.data?.let { b ->
            List(b.getInt(4).coerceAtMost(5000)) { b.getLong(8 + it * 8) }
        } ?: return emptyList()
        val stsc = text.stbl.first("stsc")?.data?.let { b ->
            List(b.getInt(4)) { b.getInt(8 + it * 12) to b.getInt(12 + it * 12) }
        } ?: listOf(1 to 1)

        val sampleOffsets = mutableListOf<Long>()
        var sample = 0
        for (chunk in offsets.indices) {
            val chunkNo = chunk + 1
            val perChunk = stsc.lastOrNull { it.first <= chunkNo }?.second ?: 1
            var at = offsets[chunk]
            repeat(perChunk) {
                if (sample < sizes.size) {
                    sampleOffsets += at
                    at += sizes[sample]
                    sample++
                }
            }
        }

        val out = mutableListOf<Chapter>()
        for (i in sampleOffsets.indices) {
            if (i >= starts.size) break
            val size = sizes[i]
            if (size < 2) continue
            val buf = ByteBuffer.allocate(size.coerceAtMost(4096))
            ch.read(buf, sampleOffsets[i])
            buf.flip()
            if (buf.remaining() < 2) continue
            val len = (buf.short.toInt() and 0xFFFF).coerceAtMost(buf.remaining())
            val bytes = ByteArray(len)
            buf.get(bytes)
            val title = if (len >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                String(bytes, 2, len - 2, Charsets.UTF_16BE)
            } else {
                String(bytes, Charsets.UTF_8)
            }.trim()
            out += Chapter(title.ifEmpty { "Chapter ${i + 1}" }, starts[i] * 1000 / text.timescale)
        }
        return out
    }
}
