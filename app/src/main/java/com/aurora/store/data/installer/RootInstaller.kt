/*
 * SPDX-FileCopyrightText: 2021 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer

import android.content.Context
import android.os.Process
import android.util.Log
import com.aurora.extensions.TAG
import com.aurora.store.AuroraApp
import com.aurora.store.R
import com.aurora.store.data.event.InstallerEvent
import com.aurora.store.data.installer.base.HostInstallReceipt
import com.aurora.store.data.installer.base.InstallerBase
import com.aurora.store.data.installer.base.OwnedInstallRequest
import com.aurora.store.data.model.Installer
import com.aurora.store.data.model.InstallerInfo
import com.aurora.store.data.room.download.Download
import com.aurora.store.util.PackageUtil.isSharedLibraryInstalled
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) : InstallerBase(context) {

    companion object {
        const val PLAY_PACKAGE_NAME = "com.android.vending"

        val installerInfo: InstallerInfo
            get() = InstallerInfo(
                id = 2,
                installer = Installer.ROOT,
                installerPackageNames = listOf(PLAY_PACKAGE_NAME),
                title = R.string.pref_install_mode_root,
                subtitle = R.string.root_installer_subtitle,
                description = R.string.root_installer_desc
            )
    }

    override fun installInternal(download: Download, owned: OwnedInstallRequest?) {
        if (owned == null && isAlreadyQueued(download.packageName)) {
            Log.i(TAG, "${download.packageName} already queued")
        } else {
            if (Shell.getShell().isRoot) {
                download.sharedLibs.forEach {
                    // Shared library packages cannot be updated
                    if (!isSharedLibraryInstalled(context, it.packageName, it.versionCode)) {
                        xInstall(download.packageName, download.versionCode, it.packageName)
                    }
                }
                xInstall(download.packageName, download.versionCode, owned = owned)
            } else {
                owned?.onFinished?.invoke(false)
                if (owned == null) {
                    postError(
                        download.packageName,
                        context.getString(R.string.installer_status_failure),
                        context.getString(R.string.installer_root_unavailable)
                    )
                }
                Log.e(
                    TAG,
                    " >>>>>>>>>>>>>>>>>>>>>>>>>> NO ROOT ACCESS <<<<<<<<<<<<<<<<<<<<<<<<<<<<<"
                )
            }
        }
    }

    private fun xInstall(
        packageName: String,
        versionCode: Long,
        sharedLibPkgName: String = "",
        owned: OwnedInstallRequest? = null
    ) {
        var totalSize = 0

        for (file in getFiles(packageName, versionCode, sharedLibPkgName, owned)) {
            totalSize += file.length().toInt()
        }

        val userId = Process.myUid() / 100_000
        val result: Shell.Result =
            Shell.cmd("pm install-create -i $PLAY_PACKAGE_NAME --user $userId -r -S $totalSize")
                .exec()

        val response = result.out

        val sessionIdPattern = Pattern.compile("(\\d+)")
        val sessionIdMatcher = sessionIdPattern.matcher(response[0])
        val found = sessionIdMatcher.find()

        if (found) {
            val sessionId = sessionIdMatcher.group(1)?.toInt()
            if (Shell.getShell().isRoot && sessionId != null) {
                for (file in getFiles(packageName, versionCode, sharedLibPkgName, owned)) {
                    Shell.cmd(
                        "cat \"${file.absoluteFile}\" | pm install-write -S ${file.length()} $sessionId \"${file.name}\""
                    )
                        .exec()
                }

                val receipt = HostInstallReceipt("root", sessionId.toString())
                owned?.onPrepared?.invoke(receipt)
                val shellResult = Shell.cmd("pm install-commit $sessionId").exec()

                if (shellResult.isSuccess) {
                    // Installation is not yet finished if this is a shared library
                    owned?.onAccepted?.invoke(receipt)
                    if (owned != null ||
                        packageName == download?.packageName
                    ) {
                        onInstallationSuccess(owned)
                    }
                } else {
                    owned?.onFinished?.invoke(false)
                    if (owned == null) removeFromInstallQueue(packageName)
                    if (owned == null) {
                        AuroraApp.events.send(
                            InstallerEvent.Failed(
                                packageName = packageName,
                                error = parseError(shellResult)
                            )
                        )
                    }
                }
            } else {
                owned?.onFinished?.invoke(false)
                if (owned == null) removeFromInstallQueue(packageName)
                if (owned == null) {
                    postError(
                        packageName,
                        context.getString(R.string.installer_status_failure),
                        context.getString(R.string.installer_root_unavailable)
                    )
                }
            }
        } else {
            owned?.onFinished?.invoke(false)
            if (owned == null) removeFromInstallQueue(packageName)
            if (owned == null) {
                postError(
                    packageName,
                    context.getString(R.string.installer_status_failure),
                    context.getString(R.string.installer_status_failure_session)
                )
            }
        }
    }

    private fun parseError(result: Shell.Result): String = result.err.joinToString(separator = "\n")
}
