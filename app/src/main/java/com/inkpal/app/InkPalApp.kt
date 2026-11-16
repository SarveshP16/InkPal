package com.inkpal.app

import android.app.Application
import com.inkpal.app.data.InkStore
import com.inkpal.app.data.LibraryScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-wide singletons: the saved library and the folder scanner. */
class InkPalApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var store: InkStore
        private set
    lateinit var scanner: LibraryScanner
        private set

    override fun onCreate() {
        super.onCreate()
        store = InkStore(this, appScope)
        scanner = LibraryScanner(this, store, appScope)
    }
}
