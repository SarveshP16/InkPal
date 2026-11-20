package com.inkpal.app.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.inkpal.app.InkPalApp
import com.inkpal.app.MainActivity
import com.inkpal.app.R
import com.inkpal.app.data.Format
import com.inkpal.app.ui.reader.SessionTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays audiobooks in the background with a media notification and lock-screen controls.
 * It also saves the listening position every few seconds and logs listening sessions, so
 * progress is kept even when the app's screens are closed.
 */
class PlaybackService : MediaSessionService() {
    private val app get() = application as InkPalApp
    private val scope = MainScope()
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var saveJob: Job? = null
    private var tracker: SessionTracker? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(30_000)
            .setSeekForwardIncrementMs(30_000)
            .build()

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )
        app.audio.servicePlayer = player

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startSession()
                    startSaving()
                } else {
                    save()
                    endSession()
                    saveJob?.cancel()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) save(ended = true)
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away keeps a playing book going; a paused one stops the service. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        save()
        endSession()
        app.audio.servicePlayer = null
        session?.release()
        session = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private val currentBookId: String? get() = player.currentMediaItem?.mediaId

    private fun label(bookId: String, positionMs: Long): String {
        val chapters = app.audio.chaptersFor(bookId)
        return chapters.lastOrNull { it.startMs <= positionMs }?.title ?: Format.clockDuration(positionMs)
    }

    /** Saves the position as the book's progress ("position" holds milliseconds). */
    private fun save(ended: Boolean = false) {
        val id = currentBookId ?: return
        val duration = player.duration.takeIf { it > 0 } ?: app.store.book(id)?.durationMs ?: return
        val pos = if (ended) duration else player.currentPosition.coerceIn(0, duration)
        val progress = (pos.toDouble() / duration).toFloat().coerceIn(0f, 1f)
        val label = label(id, pos)
        tracker?.progress = progress
        tracker?.label = label
        app.store.savePosition(id, progress, pos.toString(), label)
    }

    private fun startSaving() {
        saveJob?.cancel()
        saveJob = scope.launch {
            while (isActive) {
                delay(5_000)
                save()
            }
        }
    }

    private fun startSession() {
        val book = currentBookId?.let { app.store.book(it) } ?: return
        if (tracker != null) return
        tracker = SessionTracker(app.store, book).also { it.begin() }
    }

    private fun endSession() {
        tracker?.end()
        tracker = null
    }
}
