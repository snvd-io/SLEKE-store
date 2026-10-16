package com.sleke.library.downloads.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {
    @Test fun olderInstalledVersionCanUpdate() {
        assertTrue(DownloadPolicy.shouldInstall(10, 11))
        assertTrue(DownloadPolicy.shouldInstall(null, 0))
        assertFalse(DownloadPolicy.shouldInstall(11, 11))
        assertFalse(DownloadPolicy.shouldInstall(12, 11))
    }

    @Test fun retainedWorkNeverCrossesOwner() {
        val alice = DownloadOwner.Enterprise("alice", "one")
        assertTrue(DownloadPolicy.sameOwner(alice, DownloadOwner.Enterprise("alice", "one")))
        assertFalse(DownloadPolicy.sameOwner(alice, DownloadOwner.Enterprise("bob", "one")))
        assertFalse(DownloadPolicy.sameOwner(alice, DownloadOwner.Public))
        assertFalse(DownloadPolicy.sameOwner(alice, DownloadOwner.Enterprise("alice", "two")))
    }

    @Test fun terminalAndPendingStatesRemainDifferent() {
        assertTrue(DownloadState.Cancelled.isTerminal)
        assertTrue(DownloadState.AlreadyCurrent.isTerminal)
        assertFalse(DownloadState.PendingConfirmation.isTerminal)
        assertFalse(DownloadState.AwaitingAccess.isTerminal)
        assertFalse(DownloadState.NeedsReconciliation.isTerminal)
    }
}
