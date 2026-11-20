package com.inkpal.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Everything InkPal remembers, kept in memory as one [LibraryData] and written to
 * `files/library.json` shortly after each change (atomic replace, so a crash mid-write
 * never leaves a half file).
 */
class InkStore(private val context: Context, private val scope: CoroutineScope) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(context.filesDir, "library.json")
    private val _data = MutableStateFlow(load())
    val data: StateFlow<LibraryData> = _data.asStateFlow()

    private val saveLock = Mutex()
    private var saveJob: Job? = null

    private fun load(): LibraryData {
        if (!file.exists()) return LibraryData()
        return try {
            json.decodeFromString(LibraryData.serializer(), file.readText())
        } catch (e: Exception) {
            // Keep the unreadable file instead of overwriting it on the next save.
            Log.w(TAG, "library.json unreadable, starting fresh", e)
            file.copyTo(File(context.filesDir, "library.json.bad"), overwrite = true)
            LibraryData()
        }
    }

    fun update(change: (LibraryData) -> LibraryData) {
        _data.update(change)
        scheduleSave()
    }

    @Synchronized
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(250)
            saveNow()
        }
    }

    suspend fun saveNow() = saveLock.withLock {
        val text = json.encodeToString(LibraryData.serializer(), _data.value)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    // ---- Books ----

    fun book(id: String): Book? = _data.value.books.firstOrNull { it.id == id }

    fun updateBook(id: String, change: (Book) -> Book) = update { d ->
        d.copy(books = d.books.map { if (it.id == id) change(it) else it })
    }

    fun markFinished(id: String) = updateBook(id) {
        it.copy(status = BookStatus.FINISHED, finishedAt = System.currentTimeMillis())
    }

    fun markNotFinished(id: String) = updateBook(id) {
        val started = it.progress > 0f || it.startedAt > 0
        it.copy(status = if (started) BookStatus.READING else BookStatus.NEW, finishedAt = 0)
    }

    fun resetProgress(id: String) = updateBook(id) {
        it.copy(
            status = BookStatus.NEW,
            progress = 0f,
            position = "",
            positionLabel = "",
            startedAt = 0,
            finishedAt = 0,
        )
    }

    /** Takes the book out of the library; the file stays on the phone and rescans skip it. */
    fun removeBook(id: String) {
        val book = book(id) ?: return
        Covers.delete(context, id)
        update { d ->
            d.copy(books = d.books.filterNot { it.id == id }, hidden = (d.hidden + book.uri).distinct())
        }
    }

    fun restoreHidden() = update { it.copy(hidden = emptyList()) }

    /** Called when a book is opened in the reader or player. "Read again" starts a finished book over. */
    fun markOpened(id: String, restart: Boolean = false) = updateBook(id) {
        val now = System.currentTimeMillis()
        val base = if (restart) it.copy(progress = 0f, position = "", positionLabel = "", finishedAt = 0) else it
        base.copy(
            status = if (base.status == BookStatus.FINISHED && !restart) BookStatus.FINISHED else BookStatus.READING,
            startedAt = if (base.startedAt == 0L || restart) now else base.startedAt,
            lastOpenedAt = now,
        )
    }

    fun savePosition(id: String, progress: Float, position: String, label: String) = updateBook(id) {
        it.copy(progress = progress, position = position, positionLabel = label, lastOpenedAt = System.currentTimeMillis())
    }

    /**
     * Logs a reading/listening stretch. One that starts within two minutes of the previous
     * session for the same book is merged into it, so a quick app switch doesn't split it.
     */
    fun addSession(session: ReadingSession) = update { d ->
        val last = d.sessions.lastOrNull()
        if (last != null && last.bookId == session.bookId && session.startedAt - last.endedAt in 0..MERGE_GAP_MS) {
            val merged = last.copy(endedAt = session.endedAt, toProgress = session.toProgress, toLabel = session.toLabel)
            d.copy(sessions = d.sessions.dropLast(1) + merged)
        } else {
            d.copy(sessions = d.sessions + session)
        }
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) = update { it.copy(settings = change(it.settings)) }

    // ---- Wishlist ----

    fun addWish(title: String, author: String, note: String) = update { d ->
        val item = WishItem(
            id = java.util.UUID.randomUUID().toString(),
            title = title.trim(),
            author = author.trim(),
            note = note.trim(),
            addedAt = System.currentTimeMillis(),
        )
        d.copy(wishlist = d.wishlist + item)
    }

    fun editWish(id: String, title: String, author: String, note: String) = update { d ->
        d.copy(wishlist = d.wishlist.map { if (it.id == id) it.copy(title = title.trim(), author = author.trim(), note = note.trim()) else it })
    }

    fun toggleWishGot(id: String) = update { d ->
        d.copy(wishlist = d.wishlist.map {
            if (it.id == id) it.copy(gotAt = if (it.gotAt > 0) 0 else System.currentTimeMillis()) else it
        })
    }

    fun removeWish(id: String) = update { d -> d.copy(wishlist = d.wishlist.filterNot { it.id == id }) }

    // ---- Bookmarks ----

    fun addBookmark(bookmark: Bookmark) = update { d -> d.copy(bookmarks = d.bookmarks + bookmark) }

    fun removeBookmark(id: String) = update { d -> d.copy(bookmarks = d.bookmarks.filterNot { it.id == id }) }

    companion object {
        private const val TAG = "InkStore"
        private const val MERGE_GAP_MS = 2 * 60_000L
    }
}
