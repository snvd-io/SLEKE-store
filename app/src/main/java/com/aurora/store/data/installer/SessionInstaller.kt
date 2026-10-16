/*
 * SPDX-FileCopyrightText: 2021 Aurora OSS
 * SPDX-FileCopyrightText: 2023 grrfe <grrfe@420blaze.it>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.EXTRA_SESSION_ID
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_STORE
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.PendingIntentCompat
import com.aurora.extensions.TAG
import com.aurora.extensions.isNAndAbove
import com.aurora.extensions.isOAndAbove
import com.aurora.extensions.isSAndAbove
import com.aurora.extensions.isTAndAbove
import com.aurora.extensions.isUAndAbove
import com.aurora.extensions.runOnUiThread
import com.aurora.store.AuroraApp
import com.aurora.store.R
import com.aurora.store.data.event.InstallerEvent
import com.aurora.store.data.installer.AppInstaller.Companion.ACTION_INSTALL_STATUS
import com.aurora.store.data.installer.AppInstaller.Companion.EXTRA_DISPLAY_NAME
import com.aurora.store.data.installer.AppInstaller.Companion.EXTRA_PACKAGE_NAME
import com.aurora.store.data.installer.AppInstaller.Companion.EXTRA_VERSION_CODE
import com.aurora.store.data.installer.base.HostInstallReceipt
import com.aurora.store.data.installer.base.InstallerBase
import com.aurora.store.data.installer.base.OwnedInstallRequest
import com.aurora.store.data.installer.base.OwnedPreAcceptanceException
import com.aurora.store.data.installer.base.withOwnedStaging
import com.aurora.store.data.model.BuildType
import com.aurora.store.data.model.Installer
import com.aurora.store.data.model.InstallerInfo
import com.aurora.store.data.model.SessionInfo
import com.aurora.store.data.receiver.InstallerStatusReceiver
import com.aurora.store.data.room.download.Download
import com.aurora.store.util.PackageUtil.isSharedLibraryInstalled
import com.sleke.library.util.SlekeConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) : InstallerBase(context) {

    val currentSessionId: Int?
        get() = synchronized(this) { enqueuedSessions.firstOrNull()?.lastOrNull()?.sessionId }

    private val packageInstaller = context.packageManager.packageInstaller
    private val enqueuedSessions = mutableListOf<MutableSet<SessionInfo>>()
    private val committedSessions = ConcurrentHashMap.newKeySet<Int>()
    private val ownedSessions = ConcurrentHashMap<Int, OwnedInstallRequest>()

    val callback = object : PackageInstaller.SessionCallback() {
        override fun onCreated(sessionId: Int) {}

        override fun onBadgingChanged(sessionId: Int) {}

        override fun onActiveChanged(sessionId: Int, active: Boolean) {}

        override fun onProgressChanged(sessionId: Int, progress: Float) {
            val packageName = synchronized(this@SessionInstaller) {
                enqueuedSessions.find { set -> set.any { it.sessionId == sessionId } }
                    ?.firstOrNull()?.packageName
            }

            if (packageName != null && progress > 0.0 && !ownedSessions.containsKey(sessionId)) {
                AuroraApp.events.send(
                    InstallerEvent.Installing(
                        packageName = packageName,
                        progress = progress
                    )
                )
            }
        }

        override fun onFinished(sessionId: Int, success: Boolean) {
            synchronized(this@SessionInstaller) {
                committedSessions.remove(sessionId)
                ownedSessions.remove(sessionId)

                val sessionSet =
                    enqueuedSessions.find { it.any { session -> session.sessionId == sessionId } }
                        ?: return

                // Find session safely, if not found return
                val sessionToRemove = sessionSet.firstOrNull { it.sessionId == sessionId } ?: return

                // Remove the session safely
                sessionSet.remove(sessionToRemove)

                if (success && sessionSet.isNotEmpty()) {
                    // Proceed with the next shared library session, if any.
                    commitInstall(sessionSet.first())
                    return
                }

                // Manually remove empty sets using iterator (for API 21 support)
                val iterator = enqueuedSessions.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().isEmpty()) {
                        iterator.remove()
                    }
                }

                commitNextPending()
            }
        }
    }

    companion object {

        val installerInfo: InstallerInfo
            get() = InstallerInfo(
                id = 0,
                installer = Installer.SESSION,
                installerPackageNames = BuildType.PACKAGE_NAMES,
                title = R.string.pref_install_mode_session,
                subtitle = R.string.session_installer_subtitle,
                description = R.string.session_installer_desc
            )
    }

    init {
        runOnUiThread { packageInstaller.registerSessionCallback(callback) }
    }

    @Synchronized
    override fun installInternal(download: Download, owned: OwnedInstallRequest?) {
        super.installInternal(download, owned)

        val sessionSet = if (owned != null) {
            null
        } else {
            enqueuedSessions.find { set ->
                set.none { ownedSessions.containsKey(it.sessionId) } &&
                    set.any { it.packageName == download.packageName }
            }
        }
        if (sessionSet != null) {
            Log.i(TAG, "${download.packageName} already queued")
            commitInstall(sessionSet.first())
        } else {
            Log.i(TAG, "Received session install request for ${download.packageName}")
            val sessionInfoSet = mutableSetOf<SessionInfo>()

            download.sharedLibs.forEach {
                // Shared library packages cannot be updated
                if (!isSharedLibraryInstalled(context, it.packageName, it.versionCode)) {
                    stageInstall(
                        download.packageName,
                        download.versionCode,
                        it.packageName
                    )?.let { sessionID ->
                        sessionInfoSet.add(SessionInfo(sessionID, it.packageName, it.versionCode))
                    }
                }
            }

            stageInstall(
                download.packageName,
                download.versionCode,
                owned = owned
            )?.let { sessionID ->
                if (owned != null) ownedSessions[sessionID] = owned
                sessionInfoSet.add(
                    SessionInfo(
                        sessionID,
                        download.packageName,
                        download.versionCode,
                        download.displayName
                    )
                )
            }

            if (owned != null && sessionInfoSet.isEmpty()) {
                throw OwnedPreAcceptanceException("Unable to stage owned APK")
            }
            // Enqueue and trigger installation
            enqueuedSessions.add(sessionInfoSet)
            commitInstall(sessionInfoSet.first())
        }
    }

    @Synchronized
    override fun cancelInstall(packageName: String) {
        val sessionSet = enqueuedSessions
            .find { set ->
                set.none { ownedSessions.containsKey(it.sessionId) } &&
                    set.any { it.packageName == packageName }
            }
            ?: return

        Log.i(TAG, "Abandoning staged session(s) for $packageName")
        sessionSet.forEach {
            runCatching { packageInstaller.abandonSession(it.sessionId) }
            committedSessions.remove(it.sessionId)
        }
        enqueuedSessions.remove(sessionSet)
        removeFromInstallQueue(packageName)
    }

    /**
     * Commits the head of the first queued set that isn't already in flight.
     *
     * Re-committing a session that is still waiting on the system's install confirmation makes
     * PackageInstallerSession swap in the new status receiver and re-dispatch, which fires a
     * second STATUS_PENDING_USER_ACTION and puts a duplicate confirmation dialog on screen. One
     * of the two installs the app, the other is left resolving a session that no longer exists
     * and lands the user on the system's "Can't install app" screen.
     */
    private fun commitNextPending() {
        enqueuedSessions
            .mapNotNull { it.firstOrNull() }
            .firstOrNull { it.sessionId !in committedSessions }
            ?.let(::commitInstall)
    }

    private fun stageInstall(
        packageName: String,
        versionCode: Long,
        sharedLibPkgName: String = "",
        owned: OwnedInstallRequest? = null
    ): Int? {
        val resolvedPackageName = sharedLibPkgName.ifBlank { packageName }

        // Size hint lets the system reserve space (and evict its cache) for the staged copy.
        val totalSize = runCatching {
            getFiles(packageName, versionCode, sharedLibPkgName, owned).sumOf { it.length() }
        }.getOrDefault(0L)

        val sessionParams = buildSessionParams(resolvedPackageName, totalSize)
        var createdSessionId: Int? = null
        return try {
            withOwnedStaging(owned != null, {
                createdSessionId?.let { packageInstaller.abandonSession(it) }
            }) {
                val sessionId = packageInstaller.createSession(sessionParams)
                createdSessionId = sessionId
                packageInstaller.openSession(sessionId).use { session ->
                    Log.i(TAG, "Writing splits to session for $packageName")
                    getFiles(packageName, versionCode, sharedLibPkgName, owned).forEach { file ->
                        file.inputStream().use { input ->
                            session.openWrite(
                                "${resolvedPackageName}_${file.name}",
                                0,
                                file.length()
                            ).use { output ->
                                input.copyTo(output)
                                session.fsync(output)
                            }
                        }
                    }
                }
                sessionId
            }
        } catch (exception: IOException) {
            // Keep legacy failure publication; owned IO failures were classified by the boundary above.
            createdSessionId?.let { runCatching { packageInstaller.abandonSession(it) } }
            removeFromInstallQueue(packageName)
            postError(packageName, exception.localizedMessage, exception.stackTraceToString())
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun buildSessionParams(packageName: String, totalSize: Long = 0L): SessionParams =
        SessionParams(SessionParams.MODE_FULL_INSTALL).apply {
            setInstallerPackageName(context.packageName)
            setAppPackageName(packageName)
            if (totalSize > 0) setSize(totalSize)
            setInstallLocation(PackageInfo.INSTALL_LOCATION_AUTO)
            if (isNAndAbove) {
                setOriginatingUid(Process.myUid())
            }
            if (isOAndAbove) {
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            if (isSAndAbove) {
                setRequireUserAction(SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (isTAndAbove) {
                setPackageSource(PACKAGE_SOURCE_STORE)
            }
            if (isUAndAbove) {
                setInstallerPackageName(context.packageName)
                setRequestUpdateOwnership(true)
                setApplicationEnabledSettingPersistent()
            }
        }

    private fun commitInstall(sessionInfo: SessionInfo) {
        if (sessionInfo.sessionId in committedSessions) return
        try {
            Log.i(TAG, "Starting install session for ${sessionInfo.packageName}")

            val existingSessionInfo = packageInstaller.getSessionInfo(sessionInfo.sessionId)
            if (existingSessionInfo == null) {
                Log.e(TAG, "Session ${sessionInfo.sessionId} is no longer valid.")
                if (!ownedSessions.containsKey(
                        sessionInfo.sessionId
                    )
                ) {
                    removeFromInstallQueue(sessionInfo.packageName)
                }
                return
            }

            commitSession(sessionInfo)
        } catch (safe: OwnedPreAcceptanceException) {
            throw safe
        } catch (e: Exception) {
            Log.e(TAG, "Error committing session: ${e.message}")
            if (!ownedSessions.containsKey(sessionInfo.sessionId)) {
                removeFromInstallQueue(sessionInfo.packageName)
                postError(sessionInfo.packageName, e.localizedMessage, e.stackTraceToString())
            }
        }
    }

    private fun commitSession(sessionInfo: SessionInfo) {
        try {
            val owned = ownedSessions[sessionInfo.sessionId]
            val receipt = HostInstallReceipt("session", sessionInfo.sessionId.toString())
            // The durable operation receipt must exist before commit can send its first callback.
            committedSessions.add(sessionInfo.sessionId)
            try {
                owned?.onPrepared?.invoke(receipt)
            } catch (exception: Exception) {
                // No commit has begun: a revoked/cancelled operation must leave no staged session.
                packageInstaller.abandonSession(sessionInfo.sessionId)
                throw exception
            }
            withOwnedStaging(owned != null, {
                packageInstaller.abandonSession(sessionInfo.sessionId)
            }) {
                packageInstaller.openSession(sessionInfo.sessionId)
            }.use { session ->
                val receiver = getCallBackIntent(sessionInfo)!!.intentSender
                // Opening the session/PendingIntent can wait on Binder; recheck before commit.
                try {
                    owned?.onPrepared?.invoke(receipt)
                } catch (exception: Exception) {
                    session.abandon()
                    throw exception
                }
                session.commit(receiver)
            }
            owned?.onAccepted?.invoke(receipt)
        } catch (safe: OwnedPreAcceptanceException) {
            ownedSessions.remove(sessionInfo.sessionId)
            committedSessions.remove(sessionInfo.sessionId)
            enqueuedSessions.removeAll { set -> set.any { it.sessionId == sessionInfo.sessionId } }
            throw safe
        } catch (e: Exception) {
            // A failed commit call may already have reached PackageInstaller. Owned requests
            // remain marked attempted so queue advancement cannot commit them a second time.
            if (!ownedSessions.containsKey(
                    sessionInfo.sessionId
                )
            ) {
                committedSessions.remove(sessionInfo.sessionId)
            }
            Log.e(TAG, "Error committing session: ${e.message}")
        } finally {
            if (!ownedSessions.containsKey(
                    sessionInfo.sessionId
                )
            ) {
                removeFromInstallQueue(sessionInfo.packageName)
            }
        }
    }

    private fun getCallBackIntent(sessionInfo: SessionInfo): PendingIntent? {
        val callBackIntent = Intent(context, InstallerStatusReceiver::class.java).apply {
            action = ACTION_INSTALL_STATUS
            setPackage(context.packageName)
            putExtra(EXTRA_SESSION_ID, sessionInfo.sessionId)
            putExtra(EXTRA_PACKAGE_NAME, sessionInfo.packageName)
            putExtra(EXTRA_VERSION_CODE, sessionInfo.versionCode)
            putExtra(EXTRA_DISPLAY_NAME, sessionInfo.displayName)
            ownedSessions[sessionInfo.sessionId]?.let {
                putExtra(
                    com.sleke.integration.downloads.OwnedInstallReceiptStore.EXTRA_OPERATION_ID,
                    it.operationId
                )
            }
            putExtra(SlekeConstants.EXTRA_IS_CUSTOM_STORE, true)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        return PendingIntentCompat.getBroadcast(
            context,
            sessionInfo.sessionId,
            callBackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            true
        )
    }

    enum class ServiceResultCode(val code: Int, val reason: String) {
        SUCCESS(0, "Request successful"),
        SERVICE_VERSION_UPDATE_REQUIRED(2, "Interface depends on a higher version"),
        SERVICE_INVALID(4, "Service is invalid"),
        METHOD_UNSUPPORTED(5, "Interface is not supported"),
        RESOLUTION_REQUIRED(6, "Needs to be resolved by opening PendingIntent"),
        NETWORK_ERROR(7, "Network exception, unable to complete interface request"),
        INTERNAL_ERROR(8, "Internal code error, incorrect parameter transmission in scenario"),
        TIMEOUT(10, "Interface access timeout return"),
        DEAD_CLIENT(12, "Current client is unavailable"),
        RESPONSE_ERROR(13, "Server returns abnormal response"),
        PROTOCOL_ERROR(15, "Not signed Huawei App Market agreement");

        companion object {
            fun fromCode(code: Int): ServiceResultCode? = entries.find { it.code == code }
        }
    }
}
