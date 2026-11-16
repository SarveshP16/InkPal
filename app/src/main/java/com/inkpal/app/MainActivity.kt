package com.inkpal.app

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.inkpal.app.ui.InkPalRoot
import com.inkpal.app.ui.reader.ReaderKeys
import com.inkpal.app.ui.theme.InkPalTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as InkPalApp

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            InkPalTheme {
                InkPalRoot(app)
            }
        }
    }

    /** Volume keys turn pages while the reader is open (and the setting is on). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val forward = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> true
            KeyEvent.KEYCODE_VOLUME_UP -> false
            else -> null
        }
        if (forward != null && ReaderKeys.active?.invoke() == true) {
            if (event.action == KeyEvent.ACTION_DOWN) ReaderKeys.turn?.invoke(forward)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        // Pick up books added to the folders since last time.
        app.scanner.scanIfStale()
    }
}
