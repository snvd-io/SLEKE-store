package com.sleke.integration.downloads

import com.aurora.store.data.installer.base.HostInstallReceipt
import com.aurora.store.data.installer.base.OwnedInstallSubmission
import com.aurora.store.data.model.DownloadStatus
import com.sleke.library.downloads.data.OwnedApkInstallRequest
import com.sleke.library.downloads.domain.InstallReceipt
import com.sleke.library.downloads.domain.InstallSubmission
import com.sleke.library.downloads.domain.ValidatedApk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AuroraInstallRequestMapperTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun validatedIdentityAndExactOwnedFilesBecomeAuroraRequest() {
        val file = temporary.newFile("owned.apk").apply { writeText("validated artifact") }
        val apk = ValidatedApk("private-reference", "test.private.package", 42L, 35)
        val files = listOf(file)
        val source = OwnedApkInstallRequest(apk, "operation-id", files, {}, {})

        val mapped = source.toAuroraInstallRequest()

        assertEquals(source.operationId, mapped.operationId)
        assertSame(files, mapped.files)
        assertSame(file, mapped.files.single())
        with(mapped.download) {
            assertEquals(apk.packageName, packageName)
            assertEquals(apk.versionCode, versionCode)
            assertEquals(apk.targetSdk, targetSdk)
            assertEquals(apk.packageName, displayName)
            assertEquals(file.length(), size)
            assertEquals(0, offerType)
            assertFalse(isInstalled)
            assertEquals("", iconURL)
            assertEquals(0, id)
            assertEquals(DownloadStatus.COMPLETED, status)
            assertEquals(100, progress)
            assertEquals(0L, speed)
            assertEquals(0L, timeRemaining)
            assertEquals(1, totalFiles)
            assertEquals(1, downloadedFiles)
            assertTrue(fileList.isEmpty())
            assertTrue(sharedLibs.isEmpty())
        }
    }

    @Test fun hostReceiptsAndCompletionReachOriginalCallbacks() {
        val file = temporary.newFile("owned.apk")
        val prepared = mutableListOf<InstallReceipt>()
        val accepted = mutableListOf<InstallReceipt>()
        val finished = mutableListOf<Boolean>()
        val source = OwnedApkInstallRequest(
            ValidatedApk("private-reference", "test.package", 42L, 35),
            "operation-id",
            listOf(file),
            onPrepared = { prepared.add(it) },
            onAccepted = { accepted.add(it) },
            onFinished = { finished.add(it) }
        )
        val mapped = source.toAuroraInstallRequest()

        mapped.onPrepared(HostInstallReceipt("session", "17"))
        mapped.onAccepted(HostInstallReceipt("native", "operation-id"))
        mapped.onFinished(true)
        mapped.onFinished(false)

        assertEquals(listOf(InstallReceipt("session", "17")), prepared)
        assertEquals(listOf(InstallReceipt("native", "operation-id")), accepted)
        assertEquals(listOf(true, false), finished)
        assertSame(source.onFinished, mapped.onFinished)
    }

    @Test fun everyHostSubmissionRetainsItsMeaning() {
        assertEquals(
            InstallSubmission.Accepted(InstallReceipt("session", "17")),
            OwnedInstallSubmission.Accepted(HostInstallReceipt("session", "17"))
                .toDomainSubmission()
        )
        assertEquals(
            InstallSubmission.Completed,
            OwnedInstallSubmission.Completed.toDomainSubmission()
        )
        assertEquals(
            InstallSubmission.Rejected,
            OwnedInstallSubmission.Rejected.toDomainSubmission()
        )
        assertEquals(
            InstallSubmission.Unknown,
            OwnedInstallSubmission.Unknown.toDomainSubmission()
        )
    }
}
