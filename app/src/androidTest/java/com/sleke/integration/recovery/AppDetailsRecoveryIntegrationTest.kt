package com.sleke.integration.recovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.store.util.Preferences
import com.sleke.library.recovery.domain.AppDetailsRecoveryPolicy
import com.sleke.library.recovery.domain.Recovery
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual host storage adapter against disposable preferences. */
@RunWith(AndroidJUnit4::class)
class AppDetailsRecoveryIntegrationTest {
    @Test
    fun existingGuardKeyIsReadAndHealthyLoadClearsOnlyThatGuard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "recovery-integration-${System.nanoTime()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            preferences.edit().putLong(Preferences.PREFERENCE_ERROR_RESTART_TS, 100_000L)
                .putString("unrelated", "preserved").commit()
            val store = PreferenceRecoveryGuardStore(preferences)
            assertEquals(
                Recovery.SHOW_MESSAGE,
                AppDetailsRecoveryPolicy.decide(true, store.read(), 159_999L)
            )
            assertEquals(
                Recovery.RESTART,
                AppDetailsRecoveryPolicy.decide(true, store.read(), 160_000L)
            )
            store.record(160_000L)
            assertEquals(
                160_000L,
                preferences.getLong(Preferences.PREFERENCE_ERROR_RESTART_TS, -1L)
            )
            store.clear()
            assertEquals(0L, store.read())
            assertEquals("preserved", preferences.getString("unrelated", null))
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
