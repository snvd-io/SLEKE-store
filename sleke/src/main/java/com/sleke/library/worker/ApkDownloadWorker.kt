package com.sleke.library.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Retained for pending WorkManager rows from versions without durable artifact ownership. */
@HiltWorker
class ApkDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = Result.failure(
        workDataOf(
            "KEY_ERROR" to "Request this download again to confirm its ownership"
        )
    )
}
