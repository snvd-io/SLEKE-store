package com.sleke.integration.downloads

import android.content.Context
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.store.data.installer.base.OwnedInstallRequest
import com.aurora.store.data.installer.base.OwnedInstallSubmission
import com.aurora.store.util.CommonUtil
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
import com.sleke.library.downloads.domain.InstalledPackageSource
import com.sleke.library.downloads.domain.ValidatedApk
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnedInstallRecoveryTest {
    @Test fun startupCleanupRetainsJournalOwnedSessions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val platform = context.packageManager.packageInstaller
        val existingSessions = platform.mySessions.map { it.sessionId }.toSet()
        val owned = platform.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        )
        val abandoned = platform.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        )
        val root = File(context.noBackupFilesDir, "startup-receipt-test-${UUID.randomUUID()}")
        try {
            val handle = DownloadHandle(UUID.randomUUID().toString())
            val apk = ValidatedApk("private", "com.sleke.test.owned", 11, 35)
            val journal = FileOperationStore(root)
            journal.write(
                OperationRecord(
                    handle,
                    CustomDownloadRequest(apk.packageName, "https://example.test/base.apk"),
                    DownloadOwner.Public,
                    DownloadState.Submitting,
                    apk,
                    InstallReceipt("session", owned.toString())
                )
            )
            CommonUtil.cleanupInstallationSessions(
                context,
                existingSessions +
                    OwnedInstallReceiptStore(FileOperationStore(root)).retainedSessionIds(),
                candidateSessionIds = setOf(owned, abandoned)
            )
            assertNotNull(platform.getSessionInfo(owned))
            assertNull(platform.getSessionInfo(abandoned))
        } finally {
            runCatching { platform.abandonSession(owned) }
            runCatching { platform.abandonSession(abandoned) }
            root.deleteRecursively()
        }
    }

    @Test fun reopenedReceiptCancelsOnlyItsPlatformSession() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val platform = context.packageManager.packageInstaller
        val owned = platform.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply { setAppPackageName("com.sleke.test.owned") }
        )
        val foreign = platform.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply { setAppPackageName("com.sleke.test.foreign") }
        )
        val root = File(context.noBackupFilesDir, "receipt-test-${UUID.randomUUID()}")
        try {
            val handle = DownloadHandle(UUID.randomUUID().toString())
            val apk = ValidatedApk("private", "com.sleke.test.owned", 11, 35)
            FileOperationStore(root).write(
                OperationRecord(
                    handle,
                    CustomDownloadRequest(apk.packageName, "https://example.test/base.apk"),
                    DownloadOwner.Public,
                    DownloadState.Submitting,
                    apk,
                    InstallReceipt("session", owned.toString())
                )
            )
            val driver = object : OwnedInstallerDriver {
                override fun preferred() = error("Recovery must not select an installer")
                override fun systemPrompt() = error("Recovery must not launch a prompt")
                override fun submit(
                    installer: String,
                    request: OwnedInstallRequest
                ): OwnedInstallSubmission = error("Recovery must not submit again")
                override fun hasOwnedSession(sessionId: Int, packageName: String) =
                    platform.mySessions.any {
                        it.sessionId ==
                            sessionId
                    }
                override fun abandonOwnedSession(sessionId: Int) =
                    platform.abandonSession(sessionId)
            }
            val packages = object : InstalledPackageSource {
                override suspend fun versionCode(packageName: String): Long? = null
            }
            val access = object : DownloadAccessSource {
                override fun check(owner: DownloadOwner) = AccessDecision.ALLOWED
            }
            val gateway =
                AuroraApkInstallGateway(driver, FileOperationStore(root), packages, access, {
                    error("No artifact replay")
                })
            assertEquals(InstallReconciliation.Pending, gateway.reconcile(handle))
            assertEquals(InstallCancellation.Cancelled, gateway.cancel(handle))
            assertNull(platform.getSessionInfo(owned))
            assertNotNull(platform.getSessionInfo(foreign))
        } finally {
            runCatching { platform.abandonSession(owned) }
            runCatching { platform.abandonSession(foreign) }
            root.deleteRecursively()
        }
    }
}
