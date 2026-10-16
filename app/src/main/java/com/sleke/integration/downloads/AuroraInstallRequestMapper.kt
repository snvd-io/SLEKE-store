package com.sleke.integration.downloads

import com.aurora.store.data.installer.base.HostInstallReceipt
import com.aurora.store.data.installer.base.OwnedInstallRequest
import com.aurora.store.data.installer.base.OwnedInstallSubmission
import com.aurora.store.data.model.DownloadStatus
import com.aurora.store.data.room.download.Download
import com.sleke.library.downloads.data.OwnedApkInstallRequest
import com.sleke.library.downloads.domain.InstallReceipt
import com.sleke.library.downloads.domain.InstallSubmission

internal fun OwnedApkInstallRequest.toAuroraInstallRequest(): OwnedInstallRequest =
    OwnedInstallRequest(
        download = Download(
            packageName = apk.packageName,
            versionCode = apk.versionCode,
            offerType = 0,
            isInstalled = false,
            displayName = apk.packageName,
            iconURL = "",
            size = files.first().length(),
            id = 0,
            status = DownloadStatus.COMPLETED,
            progress = 100,
            speed = 0,
            timeRemaining = 0,
            totalFiles = 1,
            downloadedFiles = 1,
            fileList = emptyList(),
            sharedLibs = emptyList(),
            targetSdk = apk.targetSdk
        ),
        operationId = operationId,
        files = files,
        onPrepared = { onPrepared(it.domain()) },
        onAccepted = { onAccepted(it.domain()) },
        onFinished = onFinished
    )

internal fun OwnedInstallSubmission.toDomainSubmission(): InstallSubmission = when (this) {
    is OwnedInstallSubmission.Accepted -> InstallSubmission.Accepted(receipt.domain())
    OwnedInstallSubmission.Completed -> InstallSubmission.Completed
    OwnedInstallSubmission.Rejected -> InstallSubmission.Rejected
    OwnedInstallSubmission.Unknown -> InstallSubmission.Unknown
}

private fun HostInstallReceipt.domain() = InstallReceipt(kind, reference)
