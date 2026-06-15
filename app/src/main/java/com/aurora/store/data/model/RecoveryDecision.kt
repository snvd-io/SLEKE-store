/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.model

/**
 * Window (ms) during which a repeated load failure after a background restart shows the
 * manual "quit and reopen" message instead of restarting again. Outside this window the
 * restart guard is considered stale and a fresh restart cycle is allowed.
 */
const val RESTART_GUARD_WINDOW_MS = 60_000L

/**
 * Recovery action to take after an app-details page fails to load.
 */
enum class Recovery {
    /** Silently re-fetch the page in place (no disruption). */
    RETRY,

    /** Background quit + restart of the whole app process. */
    RESTART,

    /** Give up automatic recovery and show the user a message. */
    SHOW_MESSAGE
}

/**
 * Pure decision for how to recover from an app-details load failure. Kept free of Android
 * dependencies so the branching can be unit-tested without restarting the process.
 *
 * @param retryAttempted whether an in-place retry has already been tried this load
 * @param lastRestartMs epoch-millis of the last error-triggered restart, or 0 if none
 * @param nowMs current epoch-millis
 * @param windowMs guard window; failures within it after a restart fall through to a message
 */
fun decideRecovery(
    retryAttempted: Boolean,
    lastRestartMs: Long,
    nowMs: Long,
    windowMs: Long = RESTART_GUARD_WINDOW_MS
): Recovery = when {
    !retryAttempted -> Recovery.RETRY
    lastRestartMs != 0L && (nowMs - lastRestartMs) < windowMs -> Recovery.SHOW_MESSAGE
    else -> Recovery.RESTART
}
