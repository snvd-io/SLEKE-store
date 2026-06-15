package com.sleke.presentation.download

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.aurora.store.data.installer.AppInstaller
import com.aurora.store.data.room.download.Download
import com.aurora.store.data.room.suite.ExternalApk
import com.aurora.store.util.PathUtil
import com.sleke.library.ui.SimpleAppUiState
import com.sleke.library.util.installApp
import com.sleke.library.util.isAppInstalled
import com.sleke.library.worker.ApkDownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@Singleton
@OptIn(ExperimentalUuidApi::class)
class AppDownloadManager @Inject constructor(
    private val workManager: WorkManager,
    private val context: Context,
    private val appInstaller: AppInstaller
) {
    fun startDownload(
        downloadUrl: String,
        packageName: String,
        scope: CoroutineScope,
        onStateChange: (Uuid, SimpleAppUiState) -> Unit
    ): Uuid {
        val workId = ApkDownloadWorker.enqueue(workManager, downloadUrl, packageName)
        observeWork(workId, scope, onStateChange)
        return workId
    }

    private fun observeWork(
        workId: Uuid,
        scope: CoroutineScope,
        onStateChange: (Uuid, SimpleAppUiState) -> Unit
    ) {
        workManager.getWorkInfoByIdFlow(workId.toJavaUuid()).onEach { workInfo ->
            workInfo?.let {
                val newState = when (it.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                        SimpleAppUiState.Downloading(
                            it.progress.getInt(ApkDownloadWorker.PROGRESS, 0)
                        )
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        val pkgName = it.outputData.getString(ApkDownloadWorker.KEY_PACKAGE)!!
                        val uri = it.outputData.getString(ApkDownloadWorker.KEY_APK_URI)!!

                        if (context.isAppInstalled(pkgName)) {
                            SimpleAppUiState.Installed
                        } else {
                            val versionCode =
                                it.outputData.getLong(ApkDownloadWorker.KEY_VERSION_CODE, 0L)
                            val targetSdk =
                                it.outputData.getInt(ApkDownloadWorker.KEY_TARGET_SDK, 1)
                            val apkPath = it.outputData.getString(ApkDownloadWorker.KEY_APK_PATH)

                            if (apkPath != null && versionCode > 0L) {
                                install(pkgName, versionCode, targetSdk, apkPath, uri)
                            } else {
                                // Missing metadata, fall back to the system install prompt
                                context.installApp(uri)
                            }
                            SimpleAppUiState.Downloaded(uri, pkgName)
                        }
                    }

                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        val error = it.outputData.getString(ApkDownloadWorker.KEY_ERROR)
                            ?: "Download failed"
                        Timber.tag("AppDownloadManager").e("Download failed: $error")
                        SimpleAppUiState.Error("Download failed")
                    }

                    else -> SimpleAppUiState.NotDownloaded
                }

                onStateChange(workId, newState)
            }
        }.launchIn(scope)
    }

    /**
     * Stages the freshly downloaded APK into the location the installer expects and
     * installs it through the user's preferred installer. This installs silently
     * wherever the configured installer allows (Root/Shizuku/Service/device-owner, or
     * Session for updates), and falls back to the system install prompt otherwise.
     */
    private fun install(
        pkgName: String,
        versionCode: Long,
        targetSdk: Int,
        apkPath: String,
        uri: String
    ) {
        runCatching {
            val destDir = PathUtil.getAppDownloadDir(context, pkgName, versionCode)
                .apply { mkdirs() }
            val destFile = File(destDir, "base.apk")
            File(apkPath).copyTo(destFile, overwrite = true)

            val externalApk = ExternalApk(
                packageName = pkgName,
                versionCode = versionCode,
                versionName = "",
                displayName = pkgName,
                iconURL = "",
                developerName = "",
                fileList = emptyList()
            )
            val download = Download.fromExternalApk(externalApk).copy(targetSdk = targetSdk)
            appInstaller.getPreferredInstaller().install(download)
        }.onFailure {
            Timber.tag("AppDownloadManager")
                .e(it, "Silent install failed for $pkgName, falling back to prompt")
            context.installApp(uri)
        }
    }
} 