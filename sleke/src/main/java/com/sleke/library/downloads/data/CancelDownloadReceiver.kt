package com.sleke.library.downloads.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sleke.library.downloads.domain.CustomDownloadCoordinator
import com.sleke.library.downloads.domain.DownloadHandle
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** App-private notification action that also persists the cancellation tombstone. */
@AndroidEntryPoint
class CancelDownloadReceiver : BroadcastReceiver() {
    @Inject lateinit var downloads: CustomDownloadCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val handle = intent.getStringExtra(EXTRA_HANDLE)?.let {
            runCatching { DownloadHandle(UUID.fromString(it).toString()) }.getOrNull()
        } ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { downloads.cancel(handle) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_HANDLE = "operation"
    }
}
