package com.sleke.library.downloads.data

import com.sleke.library.catalog.domain.CatalogScope
import com.sleke.library.downloads.domain.ApkInstallGateway
import com.sleke.library.downloads.domain.CustomDownloadRequest
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.DownloadOwner
import com.sleke.library.downloads.domain.DownloadState
import com.sleke.library.downloads.domain.InstallCancellation
import com.sleke.library.downloads.domain.InstallReceipt
import com.sleke.library.downloads.domain.InstallReconciliation
import com.sleke.library.downloads.domain.InstallSubmission
import com.sleke.library.downloads.domain.InstalledPackageSource
import com.sleke.library.downloads.domain.ValidatedApk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadProcessorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val handle = DownloadHandle("00000000-0000-0000-0000-000000000001")
    private val apk = ValidatedApk(handle.value, "a.b", 11, 26)
    private val request =
        CustomDownloadRequest("a.b", "https://example.test/a.apk", CatalogScope.Sleke)
    private val owner = DownloadOwner.Enterprise("alice", "enterprise")

    @Test fun acceptedHandoffIsNotRepeatedOnRecovery() = runTest {
        val store = FileOperationStore(temporary.newFolder())
        store.write(OperationRecord(handle, request, DownloadOwner.Public))
        val gateway = FakeGateway()
        val processor =
            DownloadProcessor(
                store,
                transfer(),
                gateway,
                installed(null),
                access(AccessDecision.ALLOWED)
            )
        processor.run(handle)
        assertEquals(DownloadState.PendingConfirmation, store.read(handle)!!.state)
        processor.run(handle)
        assertEquals(1, gateway.submissions)
    }

    @Test fun unknownHandoffDoesNotReplay() = runTest {
        val store = FileOperationStore(temporary.newFolder())
        store.write(
            OperationRecord(handle, request, DownloadOwner.Public, DownloadState.Submitting, apk)
        )
        val gateway = FakeGateway().apply { reconciliation = InstallReconciliation.Unknown }
        DownloadProcessor(
            store,
            transfer(),
            gateway,
            installed(null),
            access(AccessDecision.ALLOWED)
        ).run(handle)
        assertEquals(DownloadState.NeedsReconciliation, store.read(handle)!!.state)
        assertEquals(0, gateway.submissions)
    }

    @Test fun cancelDuringTransferCannotSubmit() = runTest {
        val store = FileOperationStore(temporary.newFolder())
        store.write(OperationRecord(handle, request, DownloadOwner.Public))
        val gateway = FakeGateway()
        val transfer = object : ApkTransferSource {
            override suspend fun download(
                request: CustomDownloadRequest,
                handle: DownloadHandle,
                progress: suspend (Int?) -> Unit
            ): ValidatedApk {
                store.update(handle) { it.copy(state = DownloadState.Cancelled) }
                return apk
            }
            override fun purge(reference: String) = Unit
        }
        DownloadProcessor(
            store,
            transfer,
            gateway,
            installed(null),
            access(AccessDecision.ALLOWED)
        ).run(handle)
        assertEquals(DownloadState.Cancelled, store.read(handle)!!.state)
        assertEquals(0, gateway.submissions)
    }

    @Test fun outageParksPrivateArtifactUntilRefresh() = runTest {
        val store = FileOperationStore(temporary.newFolder())
        store.write(
            OperationRecord(
                handle,
                request.copy(scope = CatalogScope.Enterprise),
                owner,
                DownloadState.Ready(apk),
                apk
            )
        )
        val gateway = FakeGateway()
        DownloadProcessor(
            store,
            transfer(),
            gateway,
            installed(null),
            access(AccessDecision.UNAVAILABLE)
        ).run(handle)
        assertEquals(DownloadState.AwaitingAccess, store.read(handle)!!.state)
        assertEquals(0, gateway.submissions)
        DownloadProcessor(
            store,
            transfer(),
            gateway,
            installed(null),
            access(AccessDecision.ALLOWED)
        ).run(handle)
        assertEquals(1, gateway.submissions)
    }

    @Test fun confirmedAccessLossCancelsUnsubmittedArtifact() = runTest {
        val store = FileOperationStore(temporary.newFolder())
        store.write(OperationRecord(handle, request, owner, DownloadState.Ready(apk), apk))
        val gateway = FakeGateway()
        DownloadProcessor(
            store,
            transfer(),
            gateway,
            installed(null),
            access(AccessDecision.REVOKED)
        ).run(handle)
        assertEquals(DownloadState.Cancelled, store.read(handle)!!.state)
        assertEquals(0, gateway.submissions)
    }

    @Test fun equalInstalledVersionIsNotReinstalled() = runTest {
        val store = FileOperationStore(temporary.newFolder())
        store.write(
            OperationRecord(handle, request, DownloadOwner.Public, DownloadState.Ready(apk), apk)
        )
        val gateway = FakeGateway()
        DownloadProcessor(
            store,
            transfer(),
            gateway,
            installed(11),
            access(AccessDecision.ALLOWED)
        ).run(handle)
        assertEquals(DownloadState.AlreadyCurrent, store.read(handle)!!.state)
        assertEquals(0, gateway.submissions)
    }

    @Test fun reopenedJournalPreservesReceiptAndOwner() = runTest {
        val dir = temporary.newFolder()
        val store = FileOperationStore(dir)
        store.write(OperationRecord(handle, request, owner, DownloadState.Submitting, apk))
        store.prepared(handle, InstallReceipt("session", "123"))
        val reopened = FileOperationStore(dir).read(handle)!!
        assertEquals(owner, reopened.owner)
        assertEquals(InstallReceipt("session", "123"), reopened.receipt)
        assertEquals(DownloadState.Submitting, reopened.state)
    }

    @Test fun definitelyUnsubmittedHandoffResumesAfterRestart() = runTest {
        val store = FileOperationStore(temporary.newFolder())
        store.write(
            OperationRecord(handle, request, DownloadOwner.Public, DownloadState.Submitting, apk)
        )
        val gateway = FakeGateway().apply { reconciliation = InstallReconciliation.NotSubmitted }
        DownloadProcessor(
            store,
            transfer(),
            gateway,
            installed(null),
            access(AccessDecision.ALLOWED)
        ).run(handle)
        assertEquals(1, gateway.submissions)
        assertEquals(DownloadState.PendingConfirmation, store.read(handle)!!.state)
    }

    @Test fun cancellingAnAcceptedRequestPreservesAmbiguousReceipt() = runTest {
        val store = FileOperationStore(temporary.newFolder())
        val receipt = InstallReceipt("native", "request")
        store.write(
            OperationRecord(
                handle,
                request,
                DownloadOwner.Public,
                DownloadState.PendingConfirmation,
                apk,
                receipt,
                true
            )
        )
        val gateway = FakeGateway().apply { cancellation = InstallCancellation.Unsupported }
        DownloadProcessor(
            store,
            transfer(),
            gateway,
            installed(null),
            access(AccessDecision.ALLOWED)
        ).cancel(handle)
        assertEquals(receipt, store.read(handle)!!.receipt)
        assertTrue(store.read(handle)!!.cancelRequested)
        assertEquals(DownloadState.NeedsReconciliation, store.read(handle)!!.state)
        assertEquals(0, gateway.submissions)
    }

    @Test fun fastActivityPreflightCannotBeOverwrittenBySubmissionReturn() = runTest {
        val store = FileOperationStore(temporary.newFolder())
        store.write(
            OperationRecord(handle, request, DownloadOwner.Public, DownloadState.Ready(apk), apk)
        )
        val gateway = FakeGateway().apply {
            beforeSubmitReturn =
                {
                    store.update(handle) {
                        it.copy(
                            state = DownloadState.AwaitingAccess,
                            receipt = null,
                            accepted = false
                        )
                    }
                }
        }
        DownloadProcessor(
            store,
            transfer(),
            gateway,
            installed(null),
            access(AccessDecision.ALLOWED)
        ).run(handle)
        assertEquals(DownloadState.AwaitingAccess, store.read(handle)!!.state)
        assertEquals(null, store.read(handle)!!.receipt)
        assertFalse(store.read(handle)!!.accepted)
    }

    @Test fun fastExternalClaimCannotRegressToAnUnlaunchedReceipt() = runTest {
        val store = FileOperationStore(temporary.newFolder())
        store.write(
            OperationRecord(handle, request, DownloadOwner.Public, DownloadState.Ready(apk), apk)
        )
        val launched = InstallReceipt("native:launched", handle.value)
        val gateway = FakeGateway().apply {
            beforeSubmitReturn =
                {
                    store.update(handle) {
                        it.copy(
                            state = DownloadState.Installing,
                            receipt = launched,
                            accepted = true
                        )
                    }
                }
        }
        DownloadProcessor(
            store,
            transfer(),
            gateway,
            installed(null),
            access(AccessDecision.ALLOWED)
        ).run(handle)
        assertEquals(launched, store.read(handle)!!.receipt)
        assertEquals(DownloadState.Installing, store.read(handle)!!.state)
    }

    @Test fun workerInterruptionPreservesTheRequestedDownload() = runTest {
        val store = FileOperationStore(temporary.newFolder())
        store.write(OperationRecord(handle, request, DownloadOwner.Public))
        val interruption = CancellationException("Worker stopped")
        var transfers = 0
        val transfer = object : ApkTransferSource {
            override suspend fun download(
                request: CustomDownloadRequest,
                handle: DownloadHandle,
                progress: suspend (Int?) -> Unit
            ): ValidatedApk {
                if (transfers++ == 0) throw interruption
                return apk
            }
            override fun purge(reference: String) = Unit
        }
        val gateway = FakeGateway()
        val processor =
            DownloadProcessor(
                store,
                transfer,
                gateway,
                installed(null),
                access(AccessDecision.ALLOWED)
            )
        try {
            processor.run(handle)
            fail("Cancellation must propagate")
        } catch (
            cancelled: CancellationException
        ) {
            assertSame(interruption, cancelled)
        }
        assertEquals(DownloadState.Queued, store.read(handle)!!.state)
        processor.run(handle)
        assertEquals(1, gateway.submissions)
    }
    private fun transfer() = object : ApkTransferSource {
        override suspend fun download(
            request: CustomDownloadRequest,
            handle: DownloadHandle,
            progress: suspend (Int?) -> Unit
        ) = apk
        override fun purge(reference: String) = Unit
    }
    private fun installed(version: Long?) = object : InstalledPackageSource {
        override suspend fun versionCode(packageName: String) = version
    }
    private fun access(decision: AccessDecision) = object : DownloadAccessSource {
        override fun check(owner: DownloadOwner) = decision
    }
    private class FakeGateway : ApkInstallGateway {
        var beforeSubmitReturn: () -> Unit = {}
        var submissions = 0
        var cancellation = InstallCancellation.Cancelled
        var reconciliation = InstallReconciliation.Pending
        override suspend fun submit(handle: DownloadHandle, apk: ValidatedApk): InstallSubmission {
            submissions++
            beforeSubmitReturn()
            return InstallSubmission.Accepted(InstallReceipt("session", "1"))
        }
        override suspend fun reconcile(handle: DownloadHandle) = reconciliation
        override suspend fun cancel(handle: DownloadHandle) = cancellation
    }
}
