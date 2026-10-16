package com.sleke.library.runtime

import android.content.Context
import com.google.firebase.FirebaseApp
import com.sleke.library.runtime.data.CrashlyticsTimberTree
import timber.log.Timber

object SlekeRuntime {
    private class OwnedDebugTree : Timber.DebugTree()

    private val initializer = RuntimeInitializer(object : LoggingRegistry {
        override fun isOwnedRegistered() = Timber.forest().any {
            it is OwnedDebugTree || it is CrashlyticsTimberTree
        }

        override fun registerOwned(debug: Boolean) {
            Timber.plant(if (debug) OwnedDebugTree() else CrashlyticsTimberTree())
        }
    })
    private var initialized = false

    @Synchronized
    fun initialize(context: Context, config: RuntimeConfig) {
        if (initialized) return
        FirebaseApp.initializeApp(context.applicationContext)
        initializer.initialize(config)
        initialized = true
    }
}
