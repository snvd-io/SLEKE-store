package com.sleke.integration.downloads

import com.aurora.store.data.installer.base.HostInstallReceipt
import com.aurora.store.data.installer.base.OwnedInstallRequest
import com.aurora.store.data.installer.base.OwnedInstallSubmission
import com.aurora.store.data.installer.base.OwnedPreAcceptanceException
import com.sleke.library.downloads.data.AccessDecision
import com.sleke.library.downloads.data.DownloadAccessSource
import com.sleke.library.downloads.data.FileOperationStore
import com.sleke.library.downloads.data.OperationRecord
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
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AuroraApkInstallGatewayTest {
    @get:Rule val temporary = TemporaryFolder()
    private val handle = DownloadHandle(UUID.randomUUID().toString())
    private val apk = ValidatedApk("private-reference", "test.package", 11, 35)
    private var installedVersion: Long? = 10
    private val installed = object : InstalledPackageSource {
        override suspend fun versionCode(packageName: String) = installedVersion
    }
    private var accessDecision = AccessDecision.ALLOWED
    private val access = object : DownloadAccessSource {
        override fun check(owner: DownloadOwner) = accessDecision
    }
    private val driver = FakeDriver()
    private val journal by lazy { FileOperationStore(temporary.newFolder("journal")) }
    private val artifact by lazy { temporary.newFile("validated.apk").apply { writeText("owned") } }

    private fun gateway(): AuroraApkInstallGateway {
        if (journal.read(handle) == null) {
            journal.write(
                OperationRecord(
                    handle,
                    CustomDownloadRequest(apk.packageName, "https://example.test/base.apk"),
                    DownloadOwner.Public,
                    DownloadState.Ready(apk),
                    apk
                )
            )
        }
        return AuroraApkInstallGateway(driver, journal, installed, access, { artifact })
    }

    @Test fun preferredInstallerIsUsed() = runBlocking {
        gateway().submit(handle, apk)
        assertEquals(listOf("preferred"), driver.calls)
    }

    @Test fun olderInstalledVersionCanUpdate() = runBlocking {
        assertTrue(gateway().submit(handle, apk) is InstallSubmission.Accepted)
        assertEquals(1, driver.calls.size)
    }

    @Test fun equalOrNewerVersionIsNotSubmitted() = runBlocking {
        installedVersion = 11
        assertEquals(InstallSubmission.Completed, gateway().submit(handle, apk))
        installedVersion = 12
        assertEquals(InstallSubmission.Completed, gateway().submit(handle, apk))
        assertTrue(driver.calls.isEmpty())
    }

    @Test fun synchronousPreAcceptanceFailureCanPromptOnce() = runBlocking {
        driver.install = { kind, request ->
            if (kind == "preferred") throw OwnedPreAcceptanceException("not launched")
            request.onPrepared(HostInstallReceipt("native", request.operationId))
            request.onAccepted(HostInstallReceipt("native", request.operationId))
            OwnedInstallSubmission.Accepted(HostInstallReceipt("native", request.operationId))
        }
        assertTrue(gateway().submit(handle, apk) is InstallSubmission.Accepted)
        assertEquals(listOf("preferred", "native"), driver.calls)
    }

    @Test fun asynchronousFailureNeverFallsBack() = runBlocking {
        driver.install = { _, _ -> OwnedInstallSubmission.Unknown }
        assertEquals(InstallSubmission.Unknown, gateway().submit(handle, apk))
        assertEquals(listOf("preferred"), driver.calls)
    }

    @Test fun userRejectionNeverFallsBack() = runBlocking {
        driver.install = { _, _ -> OwnedInstallSubmission.Rejected }
        assertEquals(InstallSubmission.Rejected, gateway().submit(handle, apk))
        assertEquals(listOf("preferred"), driver.calls)
    }

    @Test fun ambiguousExceptionNeverFallsBack() = runBlocking {
        driver.install = { _, request ->
            request.onPrepared(HostInstallReceipt("native", request.operationId))
            throw IllegalStateException("launch result lost")
        }
        assertEquals(InstallSubmission.Unknown, gateway().submit(handle, apk))
        assertEquals(listOf("preferred"), driver.calls)
    }

    @Test fun foreignSplitSetIsNeitherInstalledNorDeleted() = runBlocking {
        val shared = temporary.newFolder("play-shared")
        File(shared, "base.apk").writeText("foreign-base")
        File(shared, "split.apk").writeText("foreign-split")
        gateway().submit(handle, apk)
        assertEquals(listOf(artifact), driver.requests.single().files)
        assertEquals("foreign-base", File(shared, "base.apk").readText())
        assertEquals("foreign-split", File(shared, "split.apk").readText())
    }

    @Test fun preparedReceiptPrecedesCommitAndAcceptanceIsDurable() = runBlocking {
        val gateway = gateway()
        driver.install = { _, request ->
            val receipt = HostInstallReceipt("session", "42")
            request.onPrepared(receipt)
            assertEquals(InstallReceipt("session", "42"), journal.read(handle)?.receipt)
            assertFalse(journal.read(handle)!!.accepted)
            request.onAccepted(receipt)
            OwnedInstallSubmission.Accepted(receipt)
        }
        gateway.submit(handle, apk)
        assertTrue(journal.read(handle)!!.accepted)
    }

    @Test fun unknownHandoffDoesNotReplay() = runBlocking {
        val gateway = gateway()
        journal.prepared(handle, InstallReceipt("native", handle.value))
        assertEquals(InstallReconciliation.Unknown, gateway.reconcile(handle))
        assertEquals(InstallSubmission.Unknown, gateway().submit(handle, apk))
        assertTrue(driver.calls.isEmpty())
    }

    @Test fun ownedSessionReconcilesAndCancelsWithoutPackageFallback() = runBlocking {
        val gateway = gateway()
        journal.prepared(handle, InstallReceipt("session", "42"))
        driver.sessions.add(42)
        assertEquals(InstallReconciliation.Pending, gateway.reconcile(handle))
        assertEquals(InstallCancellation.Cancelled, gateway.cancel(handle))
        assertEquals(listOf(42), driver.abandoned)
    }

    @Test fun lostSessionRemainsUnknownAndInstalledVersionReconciles() = runBlocking {
        val gateway = gateway()
        journal.prepared(handle, InstallReceipt("session", "42"))
        assertEquals(InstallReconciliation.Unknown, gateway.reconcile(handle))
        installedVersion = 11
        assertEquals(InstallReconciliation.Installed, gateway.reconcile(handle))
        assertEquals(InstallCancellation.AlreadyFinished, gateway.cancel(handle))
        assertTrue(driver.abandoned.isEmpty())
    }

    @Test fun twoSamePackageOperationsUseTheirOwnValidatedFile() = runBlocking {
        val gateway = gateway()
        gateway.submit(handle, apk)
        val next = DownloadHandle(UUID.randomUUID().toString())
        val nextFile = temporary.newFile("second.apk").apply { writeText("second") }
        val nextApk = apk.copy(reference = "second-reference")
        journal.write(
            OperationRecord(
                next,
                CustomDownloadRequest(apk.packageName, "https://example.test/next.apk"),
                DownloadOwner.Public,
                DownloadState.Ready(nextApk),
                nextApk
            )
        )
        AuroraApkInstallGateway(driver, journal, installed, access, { reference ->
            if (reference == nextApk.reference) nextFile else artifact
        }).submit(next, nextApk)
        assertEquals(listOf(listOf(artifact), listOf(nextFile)), driver.requests.map { it.files })
        assertNotEquals(driver.requests[0].operationId, driver.requests[1].operationId)
    }

    @Test fun deferredHandoffRechecksAuthorityEvenBeforeJournalCatchesUp() = runBlocking {
        driver.install = { _, _ -> OwnedInstallSubmission.Unknown }
        gateway().submit(handle, apk)
        accessDecision = AccessDecision.REVOKED
        val prepared =
            runCatching {
                driver.requests.single().onPrepared(HostInstallReceipt("service", handle.value))
            }
        assertTrue(prepared.isFailure)
        assertEquals(InstallReceipt("attempt", handle.value), journal.read(handle)?.receipt)
    }

    @Test fun deferredHandoffHonoursCancellationTombstone() = runBlocking {
        driver.install = { _, _ -> OwnedInstallSubmission.Unknown }
        gateway().submit(handle, apk)
        journal.update(handle) { it.copy(cancelRequested = true) }
        assertTrue(
            runCatching {
                driver.requests.single().onPrepared(HostInstallReceipt("service", handle.value))
            }.isFailure
        )
    }

    @Test fun terminalOperationCannotBeReinstalledAfterUserUninstallsPackage() = runBlocking {
        val gateway = gateway()
        journal.update(handle) { it.copy(state = DownloadState.Installed) }
        installedVersion = null
        assertEquals(InstallSubmission.Rejected, gateway.submit(handle, apk))
        assertTrue(driver.calls.isEmpty())
    }

    @Test fun receiptWithoutArtifactMetadataIsUnknown() = runBlocking {
        val gateway = gateway()
        journal.update(handle) {
            it.copy(
                artifact = null,
                state = DownloadState.NeedsReconciliation,
                receipt = InstallReceipt("native", handle.value)
            )
        }
        assertEquals(InstallReconciliation.Unknown, gateway.reconcile(handle))
        assertEquals(InstallCancellation.Unsupported, gateway.cancel(handle))
    }

    private class FakeDriver : OwnedInstallerDriver {
        val calls = mutableListOf<String>()
        val requests = mutableListOf<OwnedInstallRequest>()
        val sessions = mutableSetOf<Int>()
        val abandoned = mutableListOf<Int>()
        var install: (String, OwnedInstallRequest) -> OwnedInstallSubmission = { _, request ->
            val receipt = HostInstallReceipt("native", request.operationId)
            request.onPrepared(receipt)
            request.onAccepted(receipt)
            OwnedInstallSubmission.Accepted(receipt)
        }
        override fun preferred() = "preferred"
        override fun systemPrompt() = "native"
        override fun submit(
            installer: String,
            request: OwnedInstallRequest
        ): OwnedInstallSubmission {
            calls.add(installer)
            requests.add(request)
            return install(installer, request)
        }
        override fun hasOwnedSession(sessionId: Int, packageName: String) = sessionId in sessions
        override fun abandonOwnedSession(sessionId: Int) {
            abandoned.add(sessionId)
        }
    }
}
