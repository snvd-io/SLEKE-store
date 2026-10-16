package com.sleke.library.downloads.data

import com.sleke.library.downloads.domain.CustomDownloadRequest
import com.sleke.library.downloads.domain.DownloadFailure
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.DownloadOwner
import com.sleke.library.downloads.domain.DownloadState
import com.sleke.library.downloads.domain.InstallReceipt
import com.sleke.library.downloads.domain.ValidatedApk
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OwnedInstallReceiptStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun callbackMustMatchOperationSessionPackageAndVersion() {
        val journal = FileOperationStore(temporary.newFolder())
        val handle = DownloadHandle(UUID.randomUUID().toString())
        val apk = ValidatedApk("private", "test.package", 11, 35)
        journal.write(
            OperationRecord(
                handle,
                CustomDownloadRequest(apk.packageName, "https://test/base.apk"),
                DownloadOwner.Public,
                DownloadState.Submitting,
                apk,
                InstallReceipt("session", "42")
            )
        )
        val store = OwnedInstallReceiptStore(journal)
        assertFalse(store.recordStatus(null, 42, apk.packageName, 11, 0))
        assertFalse(store.recordStatus(handle.value, 41, apk.packageName, 11, 0))
        assertFalse(store.recordStatus(handle.value, 42, "other.package", 11, 0))
        assertFalse(store.recordStatus(handle.value, 42, apk.packageName, 10, 0))
        assertEquals(DownloadState.Submitting, journal.read(handle)?.state)
        assertTrue(store.recordStatus(handle.value, 42, apk.packageName, 11, -1))
        assertEquals(DownloadState.PendingConfirmation, journal.read(handle)?.state)
        // Reopen the durable store rather than relying on an in-memory session map.
        val reopened = OwnedInstallReceiptStore(journal)
        assertTrue(reopened.recordStatus(handle.value, 42, apk.packageName, 11, 0))
        assertEquals(DownloadState.Installed, journal.read(handle)?.state)
    }

    @Test fun cancelledOrTerminalPendingCallbackCannotPrompt() {
        val journal = FileOperationStore(temporary.newFolder())
        val handle = DownloadHandle(UUID.randomUUID().toString())
        val apk = ValidatedApk("private", "test.package", 11, 35)
        val record =
            OperationRecord(
                handle,
                CustomDownloadRequest(apk.packageName, "https://test/base.apk"),
                DownloadOwner.Public,
                DownloadState.NeedsReconciliation,
                apk,
                InstallReceipt("session", "42"),
                cancelRequested = true
            )
        journal.write(record)
        val store = OwnedInstallReceiptStore(journal)
        assertFalse(store.recordStatus(handle.value, 42, apk.packageName, 11, -1))
        assertEquals(DownloadState.NeedsReconciliation, journal.read(handle)?.state)
        journal.write(record.copy(state = DownloadState.Cancelled, cancelRequested = false))
        assertFalse(store.recordStatus(handle.value, 42, apk.packageName, 11, -1))
        assertEquals(DownloadState.Cancelled, journal.read(handle)?.state)
    }

    @Test fun microGResultMatchesExactAttemptAndConfirmsInstalledVersion() {
        val journal = FileOperationStore(temporary.newFolder())
        val handle = DownloadHandle(UUID.randomUUID().toString())
        val apk = ValidatedApk("private", "test.package", 11, 35)
        val record =
            OperationRecord(
                handle,
                CustomDownloadRequest(apk.packageName, "https://test/base.apk"),
                DownloadOwner.Public,
                DownloadState.PendingConfirmation,
                apk,
                InstallReceipt("microg", handle.value)
            )
        journal.write(record)
        val store = OwnedInstallReceiptStore(journal)
        assertFalse(store.recordMicroGResult(handle.value, "other.package", 11, false, null))
        assertFalse(store.recordMicroGResult(handle.value, apk.packageName, 10, false, null))
        assertFalse(
            store.recordMicroGResult(UUID.randomUUID().toString(), apk.packageName, 11, false, null)
        )
        assertTrue(store.recordMicroGResult(handle.value, apk.packageName, 11, true, 10))
        assertEquals(DownloadState.NeedsReconciliation, journal.read(handle)?.state)
        assertTrue(store.recordMicroGResult(handle.value, apk.packageName, 11, false, null))
        assertEquals(
            DownloadState.Failed(DownloadFailure.INSTALLATION),
            journal.read(handle)?.state
        )
        journal.write(record)
        assertTrue(store.recordMicroGResult(handle.value, apk.packageName, 11, true, 11))
        assertEquals(DownloadState.Installed, journal.read(handle)?.state)
    }

    @Test fun nativeResultAndPromptClaimBelongToOneExactAttempt() {
        val journal = FileOperationStore(temporary.newFolder())
        val handle = DownloadHandle(UUID.randomUUID().toString())
        val apk = ValidatedApk("private", "test.package", 11, 35)
        val record =
            OperationRecord(
                handle,
                CustomDownloadRequest(apk.packageName, "https://test/base.apk"),
                DownloadOwner.Public,
                DownloadState.PendingConfirmation,
                apk,
                InstallReceipt("native", handle.value)
            )
        val store = OwnedInstallReceiptStore(journal)
        journal.write(record)
        val access = object : DownloadAccessSource {
            override fun check(owner: DownloadOwner) = AccessDecision.ALLOWED
        }
        assertTrue(store.prepareExternalLaunch(handle.value, "native", apk.packageName, 11, access))
        assertFalse(
            store.prepareExternalLaunch(handle.value, "native", apk.packageName, 11, access)
        )
        assertEquals(InstallReceipt("native:launched", handle.value), journal.read(handle)?.receipt)
        assertFalse(
            store.recordExternalResult(handle.value, "microg", apk.packageName, 11, false, null)
        )
        assertTrue(
            store.recordExternalResult(handle.value, "native", apk.packageName, 11, false, null)
        )
        assertEquals(
            DownloadState.Failed(DownloadFailure.INSTALLATION),
            journal.read(handle)?.state
        )
        journal.write(record)
        assertTrue(
            store.recordExternalResult(handle.value, "native", apk.packageName, 11, true, 12)
        )
        assertEquals(DownloadState.Installed, journal.read(handle)?.state)
    }

    @Test fun pendingPromptAuthorityLossIsKnownNotLaunched() {
        val journal = FileOperationStore(temporary.newFolder())
        val handle = DownloadHandle(UUID.randomUUID().toString())
        val apk = ValidatedApk("private", "test.package", 11, 35)
        val record =
            OperationRecord(
                handle,
                CustomDownloadRequest(apk.packageName, "https://test/base.apk"),
                DownloadOwner.Public,
                DownloadState.PendingConfirmation,
                apk,
                InstallReceipt("native", handle.value),
                accepted = true
            )
        journal.write(record)
        val store = OwnedInstallReceiptStore(journal)
        val unavailable = object : DownloadAccessSource {
            override fun check(owner: DownloadOwner) = AccessDecision.UNAVAILABLE
        }
        assertFalse(
            store.prepareExternalLaunch(handle.value, "native", apk.packageName, 11, unavailable)
        )
        assertEquals(DownloadState.AwaitingAccess, journal.read(handle)?.state)
        assertNull(journal.read(handle)?.receipt)
        assertFalse(journal.read(handle)!!.accepted)
    }

    @Test fun claimThenRevocationBeforeMainDispatchCannotLaunch() {
        val journal = FileOperationStore(temporary.newFolder())
        val handle = DownloadHandle(UUID.randomUUID().toString())
        val apk = ValidatedApk("private", "test.package", 11, 35)
        journal.write(
            OperationRecord(
                handle,
                CustomDownloadRequest(apk.packageName, "https://test/base.apk"),
                DownloadOwner.Public,
                DownloadState.PendingConfirmation,
                apk,
                InstallReceipt("native", handle.value)
            )
        )
        val store = OwnedInstallReceiptStore(journal)
        var decision = AccessDecision.ALLOWED
        val access = object : DownloadAccessSource {
            override fun check(owner: DownloadOwner) = decision
        }
        assertTrue(store.prepareExternalLaunch(handle.value, "native", apk.packageName, 11, access))
        decision = AccessDecision.REVOKED
        var launches = 0
        assertEquals(
            AccessDecision.REVOKED,
            runOwnedExternalLaunch(DownloadOwner.Public, access, {
                false
            }) { launches++ }
        )
        assertEquals(0, launches)
        store.recordExternalNotLaunched(
            handle.value,
            "native",
            apk.packageName,
            11,
            AccessDecision.REVOKED,
            claimedByThisWrapper = true
        )
        assertEquals(DownloadState.Cancelled, journal.read(handle)?.state)
        assertNull(journal.read(handle)?.receipt)
    }

    @Test fun cancellationBeforeMainDispatchCannotLaunch() {
        val access = object : DownloadAccessSource {
            override fun check(owner: DownloadOwner) = AccessDecision.ALLOWED
        }
        var launches = 0
        assertEquals(
            AccessDecision.REVOKED,
            runOwnedExternalLaunch(DownloadOwner.Public, access, {
                true
            }) { launches++ }
        )
        assertEquals(0, launches)
    }
}
