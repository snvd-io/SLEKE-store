/*
 * SPDX-FileCopyrightText: 2021 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer.base

import android.content.Context
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.aurora.extensions.TAG
import com.aurora.store.AuroraApp
import com.aurora.store.R
import com.aurora.store.data.event.InstallerEvent
import com.aurora.store.data.installer.base.IInstaller
import com.aurora.store.data.room.download.Download
import com.aurora.store.util.NotificationUtil
import com.aurora.store.util.PathUtil
import com.aurora.store.util.Preferences
import com.aurora.store.util.Preferences.PREFERENCE_AUTO_DELETE
import com.sleke.library.util.SlekeConstants
import java.io.File
import java.util.concurrent.atomic.AtomicReference

abstract class InstallerBase(private val context: Context) : IInstaller {

    companion object {
        fun notifyInstallation(context: Context, displayName: String, packageName: String) {
            NotificationUtil.notifyInstalled(context, displayName, packageName)
        }

        fun getErrorString(context: Context, status: Int): String = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> context.getString(
                R.string.installer_status_user_action
            )

            PackageInstaller.STATUS_FAILURE_BLOCKED -> context.getString(
                R.string.installer_status_failure_blocked
            )

            PackageInstaller.STATUS_FAILURE_CONFLICT -> context.getString(
                R.string.installer_status_failure_conflict
            )

            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> context.getString(
                R.string.installer_status_failure_incompatible
            )

            PackageInstaller.STATUS_FAILURE_INVALID -> context.getString(
                R.string.installer_status_failure_invalid
            )

            PackageInstaller.STATUS_FAILURE_STORAGE -> context.getString(
                R.string.installer_status_failure_storage
            )

            else -> context.getString(R.string.installer_status_failure)
        }
    }

    var download: Download? = null
        private set

    override fun install(download: Download) = installInternal(download, null)

    protected open fun installInternal(download: Download, owned: OwnedInstallRequest?) {
        if (owned == null) this.download = download
    }

    final override fun installOwned(request: OwnedInstallRequest): OwnedInstallSubmission {
        val result = AtomicReference<OwnedInstallSubmission>(OwnedInstallSubmission.Unknown)
        val owned = request.copy(
            files = request.files.toList(),
            onAccepted = { receipt ->
                request.onAccepted(receipt)
                result.updateAndGet { previous ->
                    if (previous == OwnedInstallSubmission.Completed ||
                        previous == OwnedInstallSubmission.Rejected
                    ) {
                        previous
                    } else {
                        OwnedInstallSubmission.Accepted(receipt)
                    }
                }
            },
            onFinished = { success ->
                request.onFinished(success)
                result.set(
                    if (success) {
                        OwnedInstallSubmission.Completed
                    } else {
                        OwnedInstallSubmission.Rejected
                    }
                )
            }
        )
        installInternal(owned.download, owned)
        return result.get()
    }

    override fun clearQueue() {
        AuroraApp.enqueuedInstalls.clear()
    }

    override fun isAlreadyQueued(packageName: String): Boolean =
        AuroraApp.enqueuedInstalls.contains(packageName)

    override fun removeFromInstallQueue(packageName: String) {
        AuroraApp.enqueuedInstalls.remove(packageName)
    }

    fun onInstallationSuccess(owned: OwnedInstallRequest? = null) {
        owned?.onFinished?.invoke(true)
        (owned?.download ?: download)?.let {
            notifyInstallation(context, it.displayName, it.packageName)
            if (Preferences.getBoolean(context, PREFERENCE_AUTO_DELETE)) {
                if (owned != null) {
                    owned.files.forEach { file -> file.delete() }
                } else {
                    PathUtil.getAppDownloadDir(context, it.packageName, it.versionCode)
                        .deleteRecursively()
                }
            }
        }
    }

    open fun postError(packageName: String, error: String?, extra: String?) {
        Log.e(TAG, "Installer Error :$error")
        AuroraApp.events.send(
            InstallerEvent.Failed(
                packageName = packageName,
                error = error,
                extra = extra
            )
        )
    }

    fun getFiles(
        packageName: String,
        versionCode: Long,
        sharedLibPackageName: String = "",
        owned: OwnedInstallRequest? = null
    ): List<File> {
        if (owned != null) {
            require(
                packageName == owned.download.packageName &&
                    versionCode == owned.download.versionCode
            )
            require(sharedLibPackageName.isEmpty()) {
                "Owned artifacts cannot include shared library directories"
            }
            return owned.files
        }
        val downloadDir = if (sharedLibPackageName.isNotBlank()) {
            PathUtil.getLibDownloadDir(context, packageName, versionCode, sharedLibPackageName)
        } else {
            PathUtil.getAppDownloadDir(context, packageName, versionCode)
        }
        return downloadDir.listFiles()!!.filter { it.path.endsWith(".apk") }
    }

    fun getUri(file: File): Uri = FileProvider.getUriForFile(
        context,
        SlekeConstants.getProviderAuthority(context),
        file
    )
}
