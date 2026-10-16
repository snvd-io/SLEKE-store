package com.sleke.library.downloads.presentation

import android.content.Intent
import com.sleke.library.downloads.domain.InstallReceipt

/** Host notification and foreground state, without depending on a particular application. */
interface InstallerUiHost {
    val isForeground: Boolean
    fun notifyInstallPrompt(request: InstallPromptRequest, intent: Intent)
}

data class InstallPromptRequest(
    val operationId: String,
    val packageName: String,
    val versionCode: Long,
    val displayName: String,
    val onPrepared: (InstallReceipt) -> Unit,
    val onAccepted: (InstallReceipt) -> Unit
)
