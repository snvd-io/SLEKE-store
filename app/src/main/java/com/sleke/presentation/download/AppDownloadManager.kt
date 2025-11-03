package com.sleke.presentation.download

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sleke.library.ui.SimpleAppUiState
import com.sleke.library.util.installApp
import com.sleke.library.util.isAppInstalled
import com.sleke.library.worker.ApkDownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@Singleton
@OptIn(ExperimentalUuidApi::class)
class AppDownloadManager @Inject constructor(
    private val workManager: WorkManager,
    private val context: Context
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
                        val isAppInstalled = context.isAppInstalled(pkgName)
                        
                        if (isAppInstalled) {
                            SimpleAppUiState.Installed
                        } else {
                            context.installApp(uri)
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
} 