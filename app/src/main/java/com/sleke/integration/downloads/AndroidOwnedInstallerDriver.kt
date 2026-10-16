package com.sleke.integration.downloads

import android.content.Context
import com.aurora.store.data.installer.AMInstaller
import com.aurora.store.data.installer.AppInstaller
import com.aurora.store.data.installer.MicroGInstaller
import com.aurora.store.data.installer.NativeInstaller
import com.aurora.store.data.installer.RootInstaller
import com.aurora.store.data.installer.ServiceInstaller
import com.aurora.store.data.installer.SessionInstaller
import com.aurora.store.data.installer.ShizukuInstaller
import com.aurora.store.data.installer.base.IInstaller
import com.aurora.store.data.installer.base.OwnedInstallRequest
import com.aurora.store.data.installer.base.OwnedInstallSubmission
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidOwnedInstallerDriver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appInstaller: AppInstaller,
    session: SessionInstaller,
    native: NativeInstaller,
    root: RootInstaller,
    service: ServiceInstaller,
    appManager: AMInstaller,
    shizuku: ShizukuInstaller,
    microG: MicroGInstaller
) : OwnedInstallerDriver {
    private val installers: Map<String, IInstaller> = linkedMapOf(
        "session" to session,
        "native" to native,
        "root" to root,
        "service" to service,
        "am" to appManager,
        "shizuku" to shizuku,
        "microg" to microG
    )

    override fun preferred(): String {
        val installer = appInstaller.getPreferredInstaller()
        return installers.entries.firstOrNull { it.value === installer }?.key ?: "session"
    }

    override fun systemPrompt() = "native"
    override fun submit(installer: String, request: OwnedInstallRequest): OwnedInstallSubmission =
        requireNotNull(installers[installer]).installOwned(request)

    override fun hasOwnedSession(sessionId: Int, packageName: String): Boolean =
        context.packageManager.packageInstaller.mySessions.any {
            it.sessionId == sessionId && it.appPackageName == packageName
        }

    override fun abandonOwnedSession(sessionId: Int) {
        // Gateway verifies the exact session receipt and mySessions before calling this.
        context.packageManager.packageInstaller.abandonSession(sessionId)
    }
}
