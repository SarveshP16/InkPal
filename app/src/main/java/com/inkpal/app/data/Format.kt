package com.inkpal.app.data

import android.net.Uri
import android.provider.DocumentsContract
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Small text helpers shared by the data layer and screens. */
object Format {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val weekdayMonthDay = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val monthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    fun idFor(uri: String): String =
        MessageDigest.getInstance("SHA-1").digest(uri.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

    fun titleFromFileName(name: String): String =
        name.substringBeforeLast('.').replace('_', ' ').replace(Regex("\\s+"), " ").trim().ifEmpty { name }

    /** "primary:Books/Classics" -> "/Books/Classics"; other volumes are marked as SD card. */
    fun folderName(treeUri: Uri): String {
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return treeUri.lastPathSegment.orEmpty()
        }
        val volume = docId.substringBefore(':', "")
        val path = "/" + docId.substringAfter(':', docId).trim('/')
        return if (volume.isEmpty() || volume == "primary") path else "SD card $path"
    }

    fun fileSize(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "${bytes / 1_000} KB"
        else -> "$bytes B"
    }

    /** "38h 20m", "41 min", "<1 min". */
    fun duration(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            totalMin > 0 -> "$totalMin min"
            ms >= 1_000 -> "${ms / 1_000} sec"
            else -> "<1 min"
        }
    }

    private fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun dayKey(ms: Long): LocalDate = date(ms)

    /** "Today", "Yesterday", "Sat, Nov 14", or with the year when it isn't this year. */
    fun dayLabel(day: LocalDate): String {
        val today = LocalDate.now(zone)
        return when {
            day == today -> "Today"
            day == today.minusDays(1) -> "Yesterday"
            day.year == today.year -> day.format(weekdayMonthDay)
            else -> day.format(monthDayYear)
        }
    }

    /** "today", "yesterday", "Nov 14" (for "Last read …"). */
    fun relativeDay(ms: Long): String {
        val day = date(ms)
        val today = LocalDate.now(zone)
        return when {
            day == today -> "today"
            day == today.minusDays(1) -> "yesterday"
            day.year == today.year -> day.format(monthDay)
            else -> day.format(monthDayYear)
        }
    }

    /** "Nov 14" or "Nov 14, 2025". */
    fun shortDate(ms: Long): String {
        val day = date(ms)
        return if (day.year == LocalDate.now(zone).year) day.format(monthDay) else day.format(monthDayYear)
    }

    /** "Today, 22:10". */
    fun dayAndTime(ms: Long): String {
        val zoned = Instant.ofEpochMilli(ms).atZone(zone)
        return dayLabel(zoned.toLocalDate()) + ", " + zoned.format(time)
    }

    /** "12:41" or "1:02:41" for a playback position or length. */
    fun clockDuration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(time)
}
