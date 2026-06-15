/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecoveryDecisionTest {

    private val window = RESTART_GUARD_WINDOW_MS
    private val now = 1_000_000L

    @Test
    fun `first failure retries in place`() {
        val decision = decideRecovery(
            retryAttempted = false,
            lastRestartMs = 0L,
            nowMs = now,
            windowMs = window
        )
        assertThat(decision).isEqualTo(Recovery.RETRY)
    }

    @Test
    fun `first failure retries even if a restart happened recently`() {
        val decision = decideRecovery(
            retryAttempted = false,
            lastRestartMs = now - 1L,
            nowMs = now,
            windowMs = window
        )
        assertThat(decision).isEqualTo(Recovery.RETRY)
    }

    @Test
    fun `retry failed and no prior restart triggers restart`() {
        val decision = decideRecovery(
            retryAttempted = true,
            lastRestartMs = 0L,
            nowMs = now,
            windowMs = window
        )
        assertThat(decision).isEqualTo(Recovery.RESTART)
    }

    @Test
    fun `retry failed within guard window shows message`() {
        val decision = decideRecovery(
            retryAttempted = true,
            lastRestartMs = now - (window / 2),
            nowMs = now,
            windowMs = window
        )
        assertThat(decision).isEqualTo(Recovery.SHOW_MESSAGE)
    }

    @Test
    fun `retry failed with stale guard restarts again`() {
        val decision = decideRecovery(
            retryAttempted = true,
            lastRestartMs = now - (window + 1L),
            nowMs = now,
            windowMs = window
        )
        assertThat(decision).isEqualTo(Recovery.RESTART)
    }

    @Test
    fun `restart exactly at window boundary is treated as stale and restarts`() {
        val decision = decideRecovery(
            retryAttempted = true,
            lastRestartMs = now - window,
            nowMs = now,
            windowMs = window
        )
        assertThat(decision).isEqualTo(Recovery.RESTART)
    }
}
