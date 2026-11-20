package com.inkpal.app.ui.reader

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.inkpal.app.data.AppSettings
import com.inkpal.app.data.ReaderTheme
import kotlinx.coroutines.delay

/**
 * Everything a reading screen does to the window and the system, shared by the EPUB and
 * PDF readers:
 * - logs a reading session from opening (or returning) to leaving,
 * - hides the system bars,
 * - applies the reader-only brightness,
 * - keeps the screen on until [lastTouch] is older than the idle timeout,
 * - turns pages with the volume keys while nothing is open on top ([overlayOpen]).
 */
@Composable
internal fun ReaderEffects(
    tracker: SessionTracker,
    settings: AppSettings,
    theme: ReaderTheme,
    lastTouch: Long,
    overlayOpen: Boolean,
    onTurn: (forward: Boolean) -> Unit,
) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    val lightBars = theme != ReaderTheme.TRUE_BLACK

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, tracker) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> tracker.begin()
                Lifecycle.Event.ON_STOP -> tracker.end()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            tracker.end()
        }
    }

    DisposableEffect(activity, lightBars) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.isAppearanceLightStatusBars = lightBars
        controller?.isAppearanceLightNavigationBars = lightBars
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            controller?.isAppearanceLightStatusBars = true
            controller?.isAppearanceLightNavigationBars = true
        }
    }

    DisposableEffect(activity, settings.brightness) {
        val window = activity?.window
        window?.attributes = window?.attributes?.apply {
            screenBrightness = if (settings.brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            else settings.brightness.coerceIn(0.01f, 1f)
        }
        onDispose {
            window?.attributes = window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    var idle by remember { mutableStateOf(false) }
    LaunchedEffect(lastTouch, settings.keepScreenOn, settings.idleTimeoutMin) {
        idle = false
        if (settings.keepScreenOn && settings.idleTimeoutMin > 0) {
            delay(settings.idleTimeoutMin * 60_000L)
            idle = true
        }
    }
    DisposableEffect(settings.keepScreenOn, idle) {
        view.keepScreenOn = settings.keepScreenOn && !idle
        onDispose { view.keepScreenOn = false }
    }

    val latestSettings by rememberUpdatedState(settings)
    val latestOverlay by rememberUpdatedState(overlayOpen)
    val latestTurn by rememberUpdatedState(onTurn)
    DisposableEffect(Unit) {
        ReaderKeys.active = { latestSettings.volumeKeysTurnPages && !latestOverlay }
        ReaderKeys.turn = { forward -> latestTurn(forward) }
        onDispose {
            ReaderKeys.active = null
            ReaderKeys.turn = null
        }
    }
}

/**
 * Recolours a rendered page (white paper, dark ink) for the reader theme: Sepia maps white to
 * warm paper and black to brown ink; True black inverts to soft grey on black.
 */
internal fun pageColorFilter(theme: ReaderTheme): ColorFilter? = when (theme) {
    ReaderTheme.WHITE -> null
    ReaderTheme.SEPIA -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                (0xF4 - 0x2E) / 255f, 0f, 0f, 0f, 0x2E.toFloat(),
                0f, (0xEC - 0x24) / 255f, 0f, 0f, 0x24.toFloat(),
                0f, 0f, (0xD8 - 0x16) / 255f, 0f, 0x16.toFloat(),
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    ReaderTheme.TRUE_BLACK -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -200f / 255f, 0f, 0f, 0f, 200f,
                0f, -200f / 255f, 0f, 0f, 200f,
                0f, 0f, -200f / 255f, 0f, 200f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}
