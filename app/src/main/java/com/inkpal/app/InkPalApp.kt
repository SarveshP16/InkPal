package com.inkpal.app

import android.app.Application
import com.inkpal.app.audio.AudioHub
import com.inkpal.app.data.InkStore
import com.inkpal.app.data.LibraryScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-wide singletons: the saved library, the folder scanner and audiobook playback. */
class InkPalApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var store: InkStore
        private set
    lateinit var scanner: LibraryScanner
        private set
    lateinit var audio: AudioHub
        private set

    override fun onCreate() {
        super.onCreate()
        store = InkStore(this, appScope)
        scanner = LibraryScanner(this, store, appScope)
        audio = AudioHub(this)
    }
}
