package com.sleke.library.runtime.data

import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

internal class CrashlyticsTimberTree : Timber.Tree() {
    private val policy = DiagnosticPolicy(object : DiagnosticsSink {
        override fun breadcrumb(priority: DiagnosticPriority, message: String) {
            val level = if (priority == DiagnosticPriority.WARNING) "W" else "E"
            FirebaseCrashlytics.getInstance().log("$level/SLEKE: $message")
        }

        override fun record(throwable: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        }
    })

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        policy.log(priority, t)
    }
}
