package com.sleke.integration.downloads

import android.content.Context
import android.content.Intent
import com.aurora.store.AuroraApp
import com.aurora.store.util.NotificationUtil
import com.sleke.library.downloads.presentation.InstallPromptRequest
import com.sleke.library.downloads.presentation.InstallerUiHost
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AuroraInstallerUiHost @Inject constructor(
    @ApplicationContext private val context: Context
) : InstallerUiHost {
    override val isForeground get() = AuroraApp.isForeground

    override fun notifyInstallPrompt(request: InstallPromptRequest, intent: Intent) {
        NotificationUtil.notifyInstallPrompt(
            context,
            request.packageName,
            request.displayName,
            intent,
            operationId = request.operationId
        )
    }
}
