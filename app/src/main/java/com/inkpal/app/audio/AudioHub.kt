package com.inkpal.app.audio

import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.inkpal.app.InkPalApp
import com.inkpal.app.data.Book
import com.inkpal.app.data.Covers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the player screens show. */
data class AudioState(
    val bookId: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val ended: Boolean = false,
)

enum class SleepMode(val label: String, val minutes: Int) {
    OFF("Off", 0),
    MIN15("15 min", 15),
    MIN30("30 min", 30),
    MIN45("45 min", 45),
    MIN60("1 hour", 60),
    CHAPTER("End of chapter", 0),
}

/** An active sleep timer; [endsAt] is null for "end of chapter". */
data class SleepTimer(val mode: SleepMode, val endsAt: Long?)

/**
 * The app's side of audiobook playback. Screens talk to the [PlaybackService] through a
 * MediaController owned here; the service registers its player too, so the sleep timer can
 * pause it even when no screen is open. Everything here runs on the main thread.
 */
class AudioHub(private val app: InkPalApp) {
    private val _state = MutableStateFlow(AudioState())
    val state: StateFlow<AudioState> = _state.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()
    private val chapterCache = HashMap<String, List<Chapter>>()

    private val _sleep = MutableStateFlow<SleepTimer?>(null)
    val sleep: StateFlow<SleepTimer?> = _sleep.asStateFlow()
    private var sleepJob: Job? = null

    /** Set by the notification tap: the UI should show the player. */
    val openPlayerRequest = MutableStateFlow(false)

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var tickJob: Job? = null

    /** The service's own player (same process), used when no controller is connected. */
    var servicePlayer: Player? = null

    /** Starts (or resumes) [book] from its saved position. */
    fun play(book: Book) {
        loadChapters(book)
        withController { c ->
            if (c.currentMediaItem?.mediaId != book.id) {
                c.setMediaItem(mediaItem(book), book.position.toLongOrNull() ?: 0L)
                c.prepare()
            }
            c.setPlaybackSpeed(app.store.data.value.settings.audioSpeed)
            c.play()
            refresh()
        }
    }

    fun togglePlay() = withController { c -> if (c.isPlaying) c.pause() else c.play() }

    fun pause() {
        (controller ?: servicePlayer)?.pause()
    }

    fun seekTo(ms: Long) = withController { c ->
        c.seekTo(ms.coerceIn(0, c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        refresh()
    }

    fun seekBy(deltaMs: Long) = withController { c -> seekTo(c.currentPosition + deltaMs) }

    fun nextChapter() {
        val pos = state.value.positionMs
        chapters.value.firstOrNull { it.startMs > pos + 500 }?.let { seekTo(it.startMs) }
    }

    /** Back to the start of this chapter, or the previous one if we're just past its start. */
    fun prevChapter() {
        val pos = state.value.positionMs
        val list = chapters.value
        val i = list.indexOfLast { it.startMs <= pos }
        if (i < 0) return seekTo(0)
        val target = if (pos - list[i].startMs > 3_000 || i == 0) list[i].startMs else list[i - 1].startMs
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        app.store.updateSettings { it.copy(audioSpeed = speed) }
        withController { c ->
            c.setPlaybackSpeed(speed)
            refresh()
        }
    }

    fun setSleep(mode: SleepMode) {
        sleepJob?.cancel()
        _sleep.value = null
        if (mode == SleepMode.OFF) return
        val endsAt = if (mode.minutes > 0) System.currentTimeMillis() + mode.minutes * 60_000L else null
        _sleep.value = SleepTimer(mode, endsAt)
        sleepJob = app.appScope.launch(Dispatchers.Main) {
            if (endsAt != null) {
                delay(endsAt - System.currentTimeMillis())
            } else {
                val end = chapterAt(state.value.positionMs)?.let { chapterEnd(it) } ?: state.value.durationMs
                while (isActive) {
                    delay(1_000)
                    val pos = (controller ?: servicePlayer)?.currentPosition ?: break
                    if (pos >= end - 400) break
                }
            }
            pause()
            _sleep.value = null
        }
    }

    /** Stops playback and forgets the current book (the mini player disappears). */
    fun stop() {
        sleepJob?.cancel()
        _sleep.value = null
        withController { c ->
            c.stop()
            c.clearMediaItems()
            refresh()
        }
    }

    fun chapterAt(positionMs: Long): Chapter? = chapters.value.lastOrNull { it.startMs <= positionMs }

    fun chapterEnd(chapter: Chapter): Long {
        val list = chapters.value
        val i = list.indexOf(chapter)
        return list.getOrNull(i + 1)?.startMs ?: state.value.durationMs
    }

    fun chaptersFor(bookId: String): List<Chapter> = chapterCache[bookId].orEmpty()

    private fun loadChapters(book: Book) {
        chapterCache[book.id]?.let {
            _chapters.value = it
            return
        }
        _chapters.value = emptyList()
        app.appScope.launch {
            val list = withContext(Dispatchers.IO) { M4bChapters.read(app, Uri.parse(book.uri)) }
            chapterCache[book.id] = list
            if (state.value.bookId == book.id || state.value.bookId == null) _chapters.value = list
        }
    }

    private fun mediaItem(book: Book): MediaItem {
        val art = Covers.file(app, book.id).takeIf { book.hasCover && it.exists() }?.readBytes()
        val meta = MediaMetadata.Builder()
            .setTitle(book.shortTitle)
            .setArtist(book.author.ifEmpty { null })
            .setAlbumTitle(book.shortTitle)
            .apply { if (art != null) setArtworkData(art, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
            .build()
        return MediaItem.Builder()
            .setMediaId(book.id)
            .setUri(Uri.parse(book.uri))
            .setMediaMetadata(meta)
            .build()
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let { return block(it) }
        val f = future ?: MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .buildAsync()
            .also { future = it }
        f.addListener({
            val c = try {
                f.get()
            } catch (e: Exception) {
                future = null
                return@addListener
            }
            if (controller == null) {
                controller = c
                c.addListener(listener)
                startTicking()
            }
            block(c)
        }, ContextCompat.getMainExecutor(app))
    }

    /** Connects to a session that's already playing (e.g. after the app was reopened). */
    fun reconnect() {
        if (controller == null) withController { refresh() }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    private fun refresh() {
        val c = controller ?: return
        val id = c.currentMediaItem?.mediaId
        if (id != null && _state.value.bookId != id) {
            app.store.book(id)?.let { b -> loadChapters(b) }
        }
        _state.update {
            AudioState(
                bookId = id,
                playing = c.isPlaying,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = c.duration.takeIf { d -> d > 0 } ?: (id?.let { app.store.book(it)?.durationMs } ?: 0L),
                speed = c.playbackParameters.speed,
                ended = c.playbackState == Player.STATE_ENDED,
            )
        }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = app.appScope.launch(Dispatchers.Main) {
            while (isActive) {
                refresh()
                delay(if (state.value.playing) 500 else 2_000)
            }
        }
    }
}
