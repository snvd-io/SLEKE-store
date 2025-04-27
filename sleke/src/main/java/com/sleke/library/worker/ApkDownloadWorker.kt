package com.sleke.library.worker

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sleke.library.util.SlekeConstants
import com.sleke.library.util.extractPackageName
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid


@HiltWorker
class ApkDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val httpClient: HttpClient,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_PACKAGE = "KEY_PACKAGE"
        private const val KEY_URL = "KEY_URL"
        const val KEY_APK_URI = "APK_URI"
        const val KEY_ERROR = "KEY_ERROR"

        const val PROGRESS = "PROGRESS"
        private const val CHANNEL_ID = "apk_download_channel"
        private const val NOTIF_ID = 42

        @OptIn(ExperimentalUuidApi::class)
        fun enqueue(
            manager: WorkManager,
            url: String,
            packageName: String,
        ): Uuid {
            val data = workDataOf(
                KEY_PACKAGE to packageName,
                KEY_URL to url
            )
            val req = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
                .setInputData(data)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            manager.enqueueUniqueWork(packageName, ExistingWorkPolicy.KEEP, req)
            return req.id.toKotlinUuid()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val packageName =
                inputData.getString(KEY_PACKAGE) ?: return@withContext Result.failure()
            val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
            val downloadsDir =
                applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: return@withContext Result.failure()
            val outFile = File(downloadsDir, "$packageName.apk")
            ensureChannel()
            setForeground(createForegroundInfo(packageName, 0))
            val channel: ByteReadChannel = httpClient.get(url).bodyAsChannel()
            val totalBytes = channel.availableForRead.toLong().takeIf { it > 0 } ?: -1L
            channel.copyToFile(outFile, totalBytes) { pct ->
                setProgress(workDataOf(PROGRESS to pct))
                setForeground(createForegroundInfo(packageName, pct))
            }
            val extractedPkg = applicationContext.extractPackageName(outFile.absolutePath) ?: Result.failure()

            val uri = FileProvider.getUriForFile(
                applicationContext,
                SlekeConstants.ProviderAuthority,
                outFile
            )


            extractedPkg to uri
        }.fold(
            onFailure = {
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to (it.message ?: "Unknown error"))
                )
            },
            onSuccess = { (extractedPkg, uri) ->
                return@withContext Result.success(
                    workDataOf(
                        KEY_PACKAGE to extractedPkg,
                        KEY_APK_URI to uri.toString()
                    )
                )
            }
        )
    }

    private fun ensureChannel() {
        val chan = NotificationChannel(
            CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW
        )
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(chan)
    }

    private fun createForegroundInfo(name: String, progress: Int): ForegroundInfo {
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(name)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ForegroundInfo(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIF_ID, notif)
        }
    }

    private suspend fun ByteReadChannel.copyToFile(
        file: File,
        totalBytes: Long,
        onProgress: suspend (Int) -> Unit
    ) {
        file.parentFile?.mkdirs()
        file.outputStream().buffered().use { out ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            var copied = 0L
            while (!isClosedForRead) {
                val read = readAvailable(buf)
                if (read <= 0) break
                out.write(buf, 0, read)
                copied += read
                if (isStopped) throw CancellationException()
                if (totalBytes > 0) {
                    val pct = (copied * 100 / totalBytes).toInt().coerceIn(0, 100)
                    onProgress(pct)
                }
            }
        }
    }
}