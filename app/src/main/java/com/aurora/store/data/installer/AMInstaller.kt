/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.installer

import android.content.Context
import android.content.Intent
import android.util.Log
import com.aurora.extensions.TAG
import com.aurora.store.R
import com.aurora.store.data.installer.base.HostInstallReceipt
import com.aurora.store.data.installer.base.InstallerBase
import com.aurora.store.data.installer.base.OwnedInstallRequest
import com.aurora.store.data.installer.base.OwnedPreAcceptanceException
import com.aurora.store.data.model.Installer
import com.aurora.store.data.model.InstallerInfo
import com.aurora.store.data.room.download.Download
import com.aurora.store.util.PackageUtil.isSharedLibraryInstalled
import com.aurora.store.util.PathUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AMInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) : InstallerBase(context) {

    companion object {
        const val AM_PACKAGE_NAME = "io.github.muntashirakon.AppManager"
        const val AM_DEBUG_PACKAGE_NAME = "io.github.muntashirakon.AppManager.debug"

        val installerInfo: InstallerInfo
            get() = InstallerInfo(
                id = 4,
                installer = Installer.AM,
                installerPackageNames = listOf(AM_PACKAGE_NAME, AM_DEBUG_PACKAGE_NAME),
                title = R.string.pref_install_mode_am,
                subtitle = R.string.am_installer_subtitle,
                description = R.string.am_installer_desc
            )
    }

    override fun installInternal(download: Download, owned: OwnedInstallRequest?) {
        if (owned == null && isAlreadyQueued(download.packageName)) {
            Log.i(TAG, "${download.packageName} already queued")
        } else {
            Log.i(TAG, "Received AM install request for ${download.packageName}")
            val fileList = mutableListOf<File>()

            download.sharedLibs.forEach {
                // Shared library packages cannot be updated
                if (!isSharedLibraryInstalled(context, it.packageName, it.versionCode)) {
                    fileList.addAll(
                        getFiles(
                            download.packageName,
                            download.versionCode,
                            it.packageName
                        )
                    )
                }
            }

            val zipFile = if (owned == null) {
                PathUtil.getZipFile(context, download.packageName, download.versionCode)
            } else {
                File(owned.files.first().parentFile, "owned-${owned.operationId}.apks")
            }
            try {
                fileList.add(
                    zip(
                        getFiles(download.packageName, download.versionCode, owned = owned),
                        zipFile
                    )
                )
            } catch (exception: java.io.IOException) {
                if (owned !=
                    null
                ) {
                    throw OwnedPreAcceptanceException("Unable to stage owned APK", exception)
                }
                throw exception
            }
            install(fileList, owned)
        }
    }

    private fun install(files: List<File>, owned: OwnedInstallRequest?) {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/x-apks"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { getUri(it) }))
        }
        val receipt = owned?.let { HostInstallReceipt("am", it.operationId) }
        if (owned != null && receipt != null) owned.onPrepared(receipt)
        try {
            context.startActivity(intent)
        } catch (exception: android.content.ActivityNotFoundException) {
            if (owned !=
                null
            ) {
                throw OwnedPreAcceptanceException("App Manager unavailable", exception)
            }
            throw exception
        }
        if (owned != null && receipt != null) owned.onAccepted(receipt)
    }

    fun zip(files: List<File>, zipFile: File): File {
        ZipOutputStream(zipFile.outputStream()).use { zipOutput ->
            files.forEach { file ->
                file.inputStream().use { input ->
                    zipOutput.putNextEntry(ZipEntry(file.name))
                    input.copyTo(zipOutput)
                }
            }
        }
        return zipFile
    }
}
