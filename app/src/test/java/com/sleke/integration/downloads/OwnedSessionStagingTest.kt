package com.sleke.integration.downloads

import com.aurora.store.data.installer.base.OwnedPreAcceptanceException
import com.aurora.store.data.installer.base.withOwnedStaging
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedSessionStagingTest {
    @Test fun stagingIoFailureAbandonsOnlyCreatedSessionAndAllowsPromptFallback() {
        val abandoned = mutableListOf<Int>()
        val failure = IOException("No staging space")
        val result = runCatching {
            withOwnedStaging(true, { abandoned.add(42) }) { throw failure }
        }.exceptionOrNull()
        assertTrue(result is OwnedPreAcceptanceException)
        assertSame(failure, result?.cause)
        assertEquals(listOf(42), abandoned)
    }

    @Test fun legacyStagingKeepsOriginalExceptionAndCleanupContract() {
        val failure = IOException("legacy")
        var ownedCleanup = false
        assertSame(
            failure,
            runCatching {
                withOwnedStaging(false, { ownedCleanup = true }) { throw failure }
            }.exceptionOrNull()
        )
        assertFalse(ownedCleanup)
    }

    @Test fun permissionDenialNeverBecomesPromptFallback() {
        val failure = SecurityException("permission denied")
        assertSame(
            failure,
            runCatching {
                withOwnedStaging(true, {}) { throw failure }
            }.exceptionOrNull()
        )
    }
}
