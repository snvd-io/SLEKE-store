/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.sleke.library.recovery.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppDetailsRecoveryPolicyTest {

    private val window = RESTART_GUARD_WINDOW_MS
    private val now = 1_000_000L

    @Test
    fun `first failure retries in place`() {
        val decision = AppDetailsRecoveryPolicy.decide(
            retryAttempted = false,
            lastRestartMs = 0L,
            nowMs = now,
            windowMs = window
        )
        assertThat(decision).isEqualTo(Recovery.RETRY)
    }

    @Test
    fun `first failure retries even if a restart happened recently`() {
        val decision = AppDetailsRecoveryPolicy.decide(
            retryAttempted = false,
            lastRestartMs = now - 1L,
            nowMs = now,
            windowMs = window
        )
        assertThat(decision).isEqualTo(Recovery.RETRY)
    }

    @Test
    fun `retry failed and no prior restart triggers restart`() {
        val decision = AppDetailsRecoveryPolicy.decide(
            retryAttempted = true,
            lastRestartMs = 0L,
            nowMs = now,
            windowMs = window
        )
        assertThat(decision).isEqualTo(Recovery.RESTART)
    }

    @Test
    fun `retry failed within guard window shows message`() {
        val decision = AppDetailsRecoveryPolicy.decide(
            retryAttempted = true,
            lastRestartMs = now - (window / 2),
            nowMs = now,
            windowMs = window
        )
        assertThat(decision).isEqualTo(Recovery.SHOW_MESSAGE)
    }

    @Test
    fun `retry failed with stale guard restarts again`() {
        val decision = AppDetailsRecoveryPolicy.decide(
            retryAttempted = true,
            lastRestartMs = now - (window + 1L),
            nowMs = now,
            windowMs = window
        )
        assertThat(decision).isEqualTo(Recovery.RESTART)
    }

    @Test
    fun `restart exactly at window boundary is treated as stale and restarts`() {
        val decision = AppDetailsRecoveryPolicy.decide(
            retryAttempted = true,
            lastRestartMs = now - window,
            nowMs = now,
            windowMs = window
        )
        assertThat(decision).isEqualTo(Recovery.RESTART)
    }

    @Test
    fun `one millisecond before guard boundary shows message`() {
        assertThat(AppDetailsRecoveryPolicy.decide(true, now - 59_999L, now))
            .isEqualTo(Recovery.SHOW_MESSAGE)
    }

    @Test
    fun `future timestamp preserves existing message guard`() {
        assertThat(AppDetailsRecoveryPolicy.decide(true, now + 1L, now))
            .isEqualTo(Recovery.SHOW_MESSAGE)
    }

    @Test
    fun `retry delay remains one second`() {
        assertThat(RETRY_DELAY_MS).isEqualTo(1_000L)
    }
}
