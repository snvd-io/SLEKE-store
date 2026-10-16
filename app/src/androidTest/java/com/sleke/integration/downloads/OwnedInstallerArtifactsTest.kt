package com.sleke.integration.downloads

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.store.data.installer.NativeInstaller
import com.aurora.store.data.installer.base.OwnedInstallRequest
import com.aurora.store.data.model.DownloadStatus
import com.aurora.store.data.room.download.Download
import com.aurora.store.util.PathUtil
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnedInstallerArtifactsTest {
    @Test fun foreignSplitsAndTwoSamePackageOperationsRemainIsolated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageName = "com.sleke.test.owned.${UUID.randomUUID().toString().replace("-", "") }"
        val shared = PathUtil.getAppDownloadDir(context, packageName, 11).apply { mkdirs() }
        val split = File(shared, "foreign-split.apk").apply { writeText("foreign") }
        val operationRoot = File(
            context.filesDir,
            "owned-artifact-test-${UUID.randomUUID()}"
        ).apply {
            mkdirs()
        }
        try {
            val first = File(operationRoot, "first.apk").apply { writeText("first") }
            val second = File(operationRoot, "second.apk").apply { writeText("second") }
            val download = Download(
                packageName, 11, 0, false, packageName, "", 0, 0,
                DownloadStatus.COMPLETED, 0, 0, 0, 1, 1, emptyList(), emptyList()
            )
            val installer = NativeInstaller(context)
            val requestA =
                OwnedInstallRequest(download, UUID.randomUUID().toString(), listOf(first), {}, {})
            val requestB =
                OwnedInstallRequest(download, UUID.randomUUID().toString(), listOf(second), {}, {})
            assertEquals(listOf(first), installer.getFiles(packageName, 11, owned = requestA))
            assertEquals(listOf(second), installer.getFiles(packageName, 11, owned = requestB))
            assertEquals("foreign", split.readText())
        } finally {
            operationRoot.deleteRecursively()
            shared.deleteRecursively()
        }
    }
}
