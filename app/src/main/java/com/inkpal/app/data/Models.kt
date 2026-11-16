package com.inkpal.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class BookFormat(val label: String) {
    EPUB("EPUB"),
    M4B("M4B");

    val isAudio: Boolean get() = this == M4B

    companion object {
        fun fromFileName(name: String): BookFormat? = when (name.substringAfterLast('.', "").lowercase()) {
            "epub" -> EPUB
            "m4b" -> M4B
            else -> null
        }
    }
}

@Serializable
enum class BookStatus { NEW, READING, FINISHED }

/**
 * One file in the library. `uri` is the SAF document URI and is the real identity;
 * `id` is a short hash of it used for cover file names and lookups.
 */
@Serializable
data class Book(
    val id: String,
    val uri: String,
    val folderUri: String,
    val fileName: String,
    val format: BookFormat,
    val title: String,
    val author: String = "",
    val sizeBytes: Long = 0,
    val modifiedAt: Long = 0,
    val addedAt: Long = 0,
    val hasCover: Boolean = false,
    /** Audiobooks only. */
    val durationMs: Long = 0,
    val status: BookStatus = BookStatus.NEW,
    /** 0..1 through the whole book. */
    val progress: Float = 0f,
    /** Where to resume. EPUB: reader locator; M4B: milliseconds. Set by the reader/player. */
    val position: String = "",
    /** Human label for the position, e.g. "Chapter 3 · Page 41". */
    val positionLabel: String = "",
    val startedAt: Long = 0,
    val lastOpenedAt: Long = 0,
    val finishedAt: Long = 0,
) {
    /** "Atomic Habits: An Easy & Proven Way…" -> "Atomic Habits". Not saved; derived from [title]. */
    val shortTitle: String
        get() = splitTitle().first

    /** The part after "Title: ", or empty. */
    val subtitle: String
        get() = splitTitle().second

    private fun splitTitle(): Pair<String, String> {
        val i = title.indexOf(": ")
        if (i < 2 || i > title.length - 3) return title to ""
        return title.substring(0, i).trim() to title.substring(i + 2).trim()
    }

    val percent: Int
        get() = when {
            progress <= 0f -> 0
            progress >= 1f -> 100
            else -> (progress * 100).toInt().coerceIn(1, 99)
        }
}

@Serializable
data class LibraryFolder(
    val uri: String,
    val name: String,
    val addedAt: Long,
)

/** One stretch of reading or listening, logged by the reader/player. */
@Serializable
data class ReadingSession(
    val id: String,
    val bookId: String,
    /** Copied so history still reads well if the book is later removed. */
    val bookTitle: String,
    val format: BookFormat,
    val startedAt: Long,
    val endedAt: Long,
    val fromProgress: Float,
    val toProgress: Float,
    val fromLabel: String = "",
    val toLabel: String = "",
) {
    val durationMs: Long get() = (endedAt - startedAt).coerceAtLeast(0)
}

@Serializable
enum class ReaderTheme { WHITE, TRUE_BLACK }

@Serializable
data class AppSettings(
    val volumeKeysTurnPages: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** Minutes without a page turn before the screen may sleep. 0 = never. */
    val idleTimeoutMin: Int = 5,
    val audioSpeed: Float = 1.0f,
    val readerTheme: ReaderTheme = ReaderTheme.WHITE,
)

@Serializable
data class LibraryData(
    val books: List<Book> = emptyList(),
    val folders: List<LibraryFolder> = emptyList(),
    val sessions: List<ReadingSession> = emptyList(),
    /** URIs the user removed from the library; rescans skip them. */
    val hidden: List<String> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val lastScanAt: Long = 0,
)
