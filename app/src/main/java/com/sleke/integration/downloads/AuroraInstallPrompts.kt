package com.sleke.integration.downloads

import android.content.Context
import android.content.Intent
import com.aurora.store.data.installer.base.HostInstallReceipt
import com.aurora.store.data.installer.base.OwnedInstallRequest
import com.sleke.library.downloads.presentation.InstallPromptRequest
import com.sleke.library.downloads.presentation.OwnedInstallerResultActivity

/** Adapts Aurora's installer DTO at the host boundary. */
object AuroraInstallPrompts {
    fun launchOrNotify(
        context: Context,
        request: OwnedInstallRequest,
        kind: String,
        installIntent: Intent
    ) = OwnedInstallerResultActivity.launchOrNotify(
        context,
        InstallPromptRequest(
            request.operationId,
            request.download.packageName,
            request.download.versionCode,
            request.download.displayName,
            { request.onPrepared(HostInstallReceipt(it.kind, it.reference)) },
            { request.onAccepted(HostInstallReceipt(it.kind, it.reference)) }
        ),
        kind,
        installIntent
    )
}
