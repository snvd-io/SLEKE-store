package com.sleke.library.essential

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.util.Log

class DownloadCompletedReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action == "android.intent.action.DOWNLOAD_COMPLETE") {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if(id != -1L) {
                val query = DownloadManager.Query()
                query.setFilterById(id)
                openDownloadedApk(id, context)
                println("Download with ID $id finished!")

                val newIntent = Intent(DOWNLOAD_SUCCESS_ACTION)
                newIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, id)
                LocalBroadcastManager.getInstance(context).sendBroadcast(newIntent)
            }
        }
    }


    private fun openDownloadedApk(downloadId: Long, context: Context) {
        Log.d("DownloadHandler", "openDownloadedApk: Started with downloadId $downloadId")

        // Retrieve the DownloadManager system service
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        Log.d("DownloadHandler", "openDownloadedApk: DownloadManager obtained")

        // Get URI for the downloaded file
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
        if (uri != null) {
            Log.d("DownloadHandler", "openDownloadedApk: URI obtained: $uri")
        } else {
            Log.e("DownloadHandler", "openDownloadedApk: Failed to get URI for downloadId $downloadId")
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("EXTRA_IS_CUSTOM_STORE", true)
            Log.d("DownloadHandler", "openDownloadedApk: Intent extra EXTRA_IS_CUSTOM_STORE set to true")
        }
        Log.d("DownloadHandler", "openDownloadedApk: Intent created with URI $uri and MIME type application/vnd.android.package-archive")

        try {
            context.startActivity(intent)
            Log.d("DownloadHandler", "openDownloadedApk: Successfully started activity to open APK")
        } catch (e: Exception) {
            Log.e("DownloadHandler", "openDownloadedApk: Error starting activity", e)
        }
    }

    companion object {
        const val DOWNLOAD_SUCCESS_ACTION = "download_success_action"
    }
}