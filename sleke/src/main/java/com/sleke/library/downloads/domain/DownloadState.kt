package com.sleke.library.downloads.domain

sealed interface DownloadState {
    data object Queued : DownloadState
    data class Downloading(val percent: Int?) : DownloadState
    data class Ready(val apk: ValidatedApk) : DownloadState
    data object AwaitingAccess : DownloadState
    data object Submitting : DownloadState
    data object PendingConfirmation : DownloadState
    data object Installing : DownloadState
    data object Installed : DownloadState
    data object AlreadyCurrent : DownloadState
    data class Failed(val reason: DownloadFailure) : DownloadState
    data object Cancelled : DownloadState
    data object NeedsReconciliation : DownloadState

    val isTerminal: Boolean
        get() = this is Installed || this is AlreadyCurrent || this is Failed || this is Cancelled
}

enum class DownloadFailure { TRANSFER, VALIDATION, STAGING, INSTALLATION, ACCESS, RECOVERY }

data class ValidatedApk(
    val reference: String,
    val packageName: String,
    val versionCode: Long,
    val targetSdk: Int
)

object DownloadPolicy {
    fun shouldInstall(installedVersion: Long?, requestedVersion: Long): Boolean =
        installedVersion == null || installedVersion < requestedVersion

    fun sameOwner(first: DownloadOwner, second: DownloadOwner): Boolean = first == second
}
