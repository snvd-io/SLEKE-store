package com.sleke.integration.downloads

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sleke.library.downloads.data.CustomApkWorker
import com.sleke.library.downloads.data.WorkManagerDownloadScheduler
import com.sleke.library.downloads.domain.DownloadHandle
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkManagerDownloadSchedulerTest {
    @Test fun keepReturnsTheRetainedWorkManagerId() = runBlocking(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WorkManager.getInstance(context)
        val packageName = "test.sleke.${UUID.randomUUID().toString().replace("-", "")}"
        val uniqueName = "sleke:apk:$packageName"
        val retained = OneTimeWorkRequestBuilder<CustomApkWorker>()
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        manager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, retained).result.get()
        try {
            val scheduler = WorkManagerDownloadScheduler(manager)
            val candidate = DownloadHandle(UUID.randomUUID().toString())
            assertEquals(
                DownloadHandle(retained.id.toString()),
                scheduler.enqueue(candidate, packageName)
            )
            assertEquals(DownloadHandle(retained.id.toString()), scheduler.active(packageName))
            assertNull(manager.getWorkInfoById(UUID.fromString(candidate.value)).get())
        } finally {
            // Only this test's unique operation is cancelled; unrelated device work is preserved.
            manager.cancelUniqueWork(uniqueName).result.get()
        }
    }
}
