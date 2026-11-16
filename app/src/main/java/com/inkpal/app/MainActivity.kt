package com.inkpal.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.inkpal.app.ui.InkPalRoot
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

    override fun onStart() {
        super.onStart()
        // Pick up books added to the folders since last time.
        app.scanner.scanIfStale()
    }
}
