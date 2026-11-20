package com.inkpal.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.StringReader
import java.net.URLDecoder
import java.util.zip.ZipInputStream

/** What a scan can learn from a file without opening it in the reader. */
data class BookInfo(
    val title: String?,
    val author: String?,
    val cover: ByteArray?,
    val durationMs: Long = 0,
    val pages: Int = 0,
)

/**
 * Reads page count and a first-page cover from a PDF with Android's PdfRenderer, and the
 * title/author from the document's Info dictionary when they're stored as plain text.
 */
object PdfMeta {
    fun read(context: Context, uri: Uri): BookInfo? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return pfd.use { fd ->
            val (pages, cover) = android.graphics.pdf.PdfRenderer(fd).use { renderer ->
                val count = renderer.pageCount
                val bytes = if (count > 0) renderer.openPage(0).use { page ->
                    val w = 480
                    val h = (w * page.height.toFloat() / page.width.coerceAtLeast(1)).toInt().coerceIn(1, 1200)
                    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val out = java.io.ByteArrayOutputStream()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    bmp.recycle()
                    out.toByteArray()
                } else null
                count to bytes
            }
            val (title, author) = infoStrings(context, uri)
            BookInfo(title = title, author = author, cover = cover, pages = pages)
        }
    }

    /** Looks for /Title and /Author literal strings near the start and end of the file. */
    private fun infoStrings(context: Context, uri: Uri): Pair<String?, String?> = try {
        val chunk = 96 * 1024
        val text = context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).channel.use { ch ->
                val size = ch.size()
                fun read(at: Long, len: Int): ByteArray {
                    val buf = java.nio.ByteBuffer.allocate(len)
                    ch.position(at)
                    while (buf.hasRemaining() && ch.read(buf) > 0) Unit
                    return buf.array().copyOf(buf.position())
                }
                val head = read(0, minOf(chunk.toLong(), size).toInt())
                val tail = if (size > chunk) read(size - chunk, chunk) else ByteArray(0)
                String(head + tail, Charsets.ISO_8859_1)
            }
        }.orEmpty()
        fun field(name: String): String? {
            val m = Regex("/$name\\s*\\(((?:\\\\.|[^\\\\)])*)\\)").find(text) ?: return null
            return decodePdfString(m.groupValues[1]).trim().takeIf { it.isNotEmpty() && it.length < 300 }
        }
        field("Title") to field("Author")
    } catch (e: Exception) {
        null to null
    }

    private fun decodePdfString(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 1 < raw.length) {
                val n = raw[i + 1]
                when (n) {
                    'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                    '(', ')', '\\' -> sb.append(n)
                    in '0'..'7' -> {
                        var j = i + 1
                        var v = 0
                        while (j < raw.length && j < i + 4 && raw[j] in '0'..'7') { v = v * 8 + (raw[j] - '0'); j++ }
                        sb.append(v.toChar()); i = j; continue
                    }
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(ch); i++
            }
        }
        val s = sb.toString()
        // UTF-16BE with a byte-order mark, otherwise PDFDocEncoding (close enough to Latin-1).
        return if (s.length >= 2 && s[0] == 'þ' && s[1] == 'ÿ') {
            String(s.substring(2).map { it.code.toByte() }.toByteArray(), Charsets.UTF_16BE)
        } else s
    }
}

/** Reads title, author and cover from an EPUB (a zip with an OPF package file). */
object EpubMeta {
    private const val CONTAINER = "META-INF/container.xml"
    private const val MAX_COVER_BYTES = 10 * 1024 * 1024

    fun read(context: Context, uri: Uri): BookInfo? {
        // Pass 1: the container and package files (small XML).
        val texts = HashMap<String, String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name.equals(CONTAINER, ignoreCase = true) || name.endsWith(".opf", ignoreCase = true)) {
                        texts[name] = zip.readBytes().toString(Charsets.UTF_8)
                    }
                }
            }
        } ?: return null

        val container = texts.entries.firstOrNull { it.key.equals(CONTAINER, ignoreCase = true) }?.value
        val opfPath = container?.let(::rootFilePath)?.takeIf { texts.containsKey(it) }
            ?: texts.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            ?: return null
        val opf = parseOpf(texts.getValue(opfPath))

        // Pass 2: the cover image, if the package names one.
        val cover = opf.coverHref?.let { readEntry(context, uri, resolve(opfPath, it)) }
        return BookInfo(opf.title, opf.author, cover)
    }

    private fun rootFilePath(xml: String): String? = try {
        val p = parser(xml)
        var found: String? = null
        while (p.eventType != XmlPullParser.END_DOCUMENT && found == null) {
            if (p.eventType == XmlPullParser.START_TAG && local(p.name) == "rootfile") {
                found = p.getAttributeValue(null, "full-path")
            }
            p.next()
        }
        found
    } catch (e: Exception) {
        null
    }

    private class Item(val id: String, val href: String, val mediaType: String, val properties: String)
    private class Opf(val title: String?, val author: String?, val coverHref: String?)

    private fun parseOpf(xml: String): Opf {
        var title: String? = null
        val creators = mutableListOf<String>()
        var coverId: String? = null
        val items = mutableListOf<Item>()
        try {
            val p = parser(xml)
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG) {
                    when (local(p.name)) {
                        "title" -> {
                            val t = p.nextText().trim()
                            if (title == null && t.isNotEmpty()) title = t
                        }
                        "creator" -> p.nextText().trim().takeIf { it.isNotEmpty() }?.let { creators += it }
                        "meta" -> if (p.getAttributeValue(null, "name") == "cover") {
                            coverId = p.getAttributeValue(null, "content")
                        }
                        "item" -> items += Item(
                            id = p.getAttributeValue(null, "id").orEmpty(),
                            href = p.getAttributeValue(null, "href").orEmpty(),
                            mediaType = p.getAttributeValue(null, "media-type").orEmpty(),
                            properties = p.getAttributeValue(null, "properties").orEmpty(),
                        )
                    }
                }
                p.next()
            }
        } catch (e: Exception) {
            // Malformed package (often stray HTML entities): keep whatever was read so far.
        }
        val images = items.filter { it.mediaType.startsWith("image/") }
        val coverHref = items.firstOrNull { "cover-image" in it.properties.split(' ') }?.href
            ?: coverId?.let { id -> items.firstOrNull { it.id == id }?.href ?: id.takeIf { '.' in it } }
            ?: images.firstOrNull { it.id.contains("cover", true) || it.href.contains("cover", true) }?.href
        return Opf(title, creators.take(2).joinToString(" & ").ifEmpty { null }, coverHref)
    }

    private fun readEntry(context: Context, uri: Uri, path: String): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var bytes: ByteArray? = null
                while (bytes == null) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name.equals(path, ignoreCase = true)) {
                        val data = zip.readBytes()
                        bytes = if (data.size <= MAX_COVER_BYTES) data else null
                        break
                    }
                }
                bytes
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Resolves an href from the OPF against the OPF's own folder inside the zip. */
    private fun resolve(opfPath: String, href: String): String {
        val clean = try {
            URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), "UTF-8")
        } catch (e: Exception) {
            href.substringBefore('#')
        }
        val base = opfPath.substringBeforeLast('/', "")
        val parts = ArrayDeque<String>()
        (if (base.isEmpty()) clean else "$base/$clean").split('/').forEach { seg ->
            when (seg) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull()
                else -> parts.addLast(seg)
            }
        }
        return parts.joinToString("/")
    }

    private fun parser(xml: String): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(StringReader(xml))
    }

    private fun local(name: String?): String = name.orEmpty().substringAfter(':')
}

/** Reads title, author, length and cover art from an M4B audiobook. */
object AudioMeta {
    fun read(context: Context, uri: Uri): BookInfo? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            fun get(key: Int) = r.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            BookInfo(
                // Audiobooks usually carry the book title as the album; "title" is often a chapter.
                title = get(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: get(MediaMetadataRetriever.METADATA_KEY_TITLE),
                author = get(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: get(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?: get(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                    ?: get(MediaMetadataRetriever.METADATA_KEY_WRITER),
                cover = r.embeddedPicture,
                durationMs = get(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
            )
        } catch (e: Exception) {
            null
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }
}
