package com.sleke.library.enterprise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EnterpriseEntryPolicyTest {
    private val identity = EnterpriseIdentity("current")

    @Test fun allEntryStatesWaitUntilPlayIsReady() {
        states().forEach {
            assertEquals(EntryDestination.Wait, EnterpriseEntryPolicy.destination(it, false))
        }
    }

    @Test fun onlyResolvedCurrentIdentityCanEnter() {
        val expected = listOf(
            EntryDestination.Wait,
            EntryDestination.Wait,
            EntryDestination.Wait,
            EntryDestination.EnterpriseCatalog,
            EntryDestination.RegularCatalog,
            EntryDestination.RegularCatalog
        )
        assertEquals(expected, states().map { EnterpriseEntryPolicy.destination(it, true) })
    }

    @Test fun deniedIsDistinctFromUnavailable() {
        assertNotEquals(
            EnterpriseSessionState.NoMembership(identity),
            EnterpriseSessionState.Unavailable(identity)
        )
    }

    @Test fun cleanupRetainsIndependentFailures() {
        val failure = CleanupFailure(CleanupStage.ENTERPRISE, IllegalStateException("offline"))
        val result = CleanupReport(listOf(failure)) + CleanupReport()
        assertFalse(result.isComplete)
        assertEquals(listOf(failure), result.failures)
    }

    private fun states() = listOf(
        EnterpriseSessionState.Restoring,
        EnterpriseSessionState.SignedOut,
        EnterpriseSessionState.Checking(identity),
        EnterpriseSessionState.Allowed(identity, "enterprise", "Company"),
        EnterpriseSessionState.NoMembership(identity),
        EnterpriseSessionState.Unavailable(identity)
    )
}
