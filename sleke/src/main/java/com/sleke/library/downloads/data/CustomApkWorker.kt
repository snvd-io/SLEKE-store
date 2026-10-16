package com.sleke.library.downloads.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.DownloadState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkManagerDownloadScheduler(private val manager: WorkManager) : CustomDownloadScheduler {
    override suspend fun active(packageName: String): DownloadHandle? =
        manager.getWorkInfosForUniqueWork(name(packageName)).get().firstOrNull {
            !it.state.isFinished
        }?.id?.let { DownloadHandle(it.toString()) }

    override suspend fun enqueue(handle: DownloadHandle, packageName: String): DownloadHandle {
        val request = OneTimeWorkRequestBuilder<CustomApkWorker>()
            .setId(UUID.fromString(handle.value))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        manager.enqueueUniqueWork(name(packageName), ExistingWorkPolicy.KEEP, request).result.get()
        val submitted = manager.getWorkInfoById(request.id).get()
        if (submitted != null) return DownloadHandle(submitted.id.toString())
        val retained = manager.getWorkInfosForUniqueWork(name(packageName)).get()
        val actual = retained.firstOrNull { !it.state.isFinished } ?: retained.singleOrNull()
        return DownloadHandle(
            checkNotNull(actual) {
                "Retained APK operation unavailable"
            }.id.toString()
        )
    }
    override suspend fun cancel(handle: DownloadHandle) {
        manager.cancelWorkById(UUID.fromString(handle.value)).result.get()
    }
    private fun name(packageName: String) = "sleke:apk:$packageName"
}

@HiltWorker
class CustomApkWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val processor: DownloadProcessor,
    private val store: FileOperationStore
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val handle = DownloadHandle(id.toString())
        if (store.read(handle) == null) return@withContext Result.failure()
        setForeground(foreground(0))
        coroutineScope {
            val progress = launch {
                store.observe(handle).collect { state ->
                    if (state is DownloadState.Downloading) {
                        setProgress(workDataOf("PROGRESS" to (state.percent ?: 0)))
                        setForeground(foreground(state.percent))
                    }
                }
            }
            try {
                processor.run(handle)
            } finally {
                progress.cancel()
            }
        }
        when (store.read(handle)?.state) {
            DownloadState.AwaitingAccess,
            DownloadState.PendingConfirmation,
            DownloadState.Installing -> Result.retry()
            is DownloadState.Failed, DownloadState.Cancelled -> Result.failure()
            else -> Result.success()
        }
    }

    private fun foreground(percent: Int?): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(
            NotificationManager::class.java
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                "apk_download_channel",
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val notification = NotificationCompat.Builder(applicationContext, "apk_download_channel")
            .setContentTitle(
                applicationContext.getString(com.sleke.library.R.string.sleke_download_notification)
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_delete,
                applicationContext.getString(android.R.string.cancel),
                PendingIntent.getBroadcast(
                    applicationContext,
                    id.hashCode(),
                    Intent(applicationContext, CancelDownloadReceiver::class.java)
                        .setData(android.net.Uri.parse("sleke-download:$id"))
                        .putExtra(CancelDownloadReceiver.EXTRA_HANDLE, id.toString()),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ForegroundInfo(
                id.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }
}
