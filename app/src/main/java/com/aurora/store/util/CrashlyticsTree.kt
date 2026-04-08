/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.kimplify.cedar.logging.LogPriority
import org.kimplify.cedar.logging.LogTree
import timber.log.Timber

/**
 * Cedar [LogTree] that forwards warnings and errors to Firebase Crashlytics.
 *
 * WARNING → added to the Crashlytics log buffer as a breadcrumb (no new issue).
 * ERROR   → added to the log buffer AND reported as a non-fatal exception.
 */
class CrashlyticsTree : LogTree {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun isLoggable(tag: String?, priority: LogPriority): Boolean =
        priority.isAtLeast(LogPriority.WARNING)

    override fun log(
        priority: LogPriority,
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        crashlytics.log("${priority.short()}/$tag: $message")
        if (priority == LogPriority.ERROR) {
            crashlytics.recordException(throwable ?: CrashlyticsReport("$tag: $message"))
        }
    }

    private fun LogPriority.short(): Char = when (this) {
        LogPriority.WARNING -> 'W'
        LogPriority.ERROR -> 'E'
        else -> '?'
    }

    /** Synthetic throwable used when [org.kimplify.cedar.logging.Cedar.e] is called without one. */
    private class CrashlyticsReport(message: String) : Throwable(message)
}

class CrashlyticsTreeT : Timber.Tree() {

    private val crashlytics = FirebaseCrashlytics.getInstance()


    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        when (priority) {
            Log.VERBOSE, Log.DEBUG, Log.INFO -> return

            else -> {
                crashlytics.log("${priority.short()}/$tag: $message")
                if (t != null) { crashlytics.recordException(t) }
            }
        }
    }

    private fun Int.short(): Char = when (this) {
        LogPriority.WARNING.ordinal -> 'W'
        LogPriority.ERROR.ordinal -> 'E'
        else -> '?'
    }
}