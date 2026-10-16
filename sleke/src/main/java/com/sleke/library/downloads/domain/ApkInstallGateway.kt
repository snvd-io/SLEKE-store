package com.sleke.library.downloads.domain

data class InstallReceipt(val kind: String, val reference: String)

sealed interface InstallSubmission {
    data class Accepted(val receipt: InstallReceipt) : InstallSubmission
    data object Completed : InstallSubmission
    data object Rejected : InstallSubmission
    data object Unknown : InstallSubmission
}

enum class InstallReconciliation { Pending, Installed, Failed, NotSubmitted, Unknown }
enum class InstallCancellation { Cancelled, Unsupported, AlreadyFinished }

interface ApkInstallGateway {
    suspend fun submit(handle: DownloadHandle, apk: ValidatedApk): InstallSubmission
    suspend fun reconcile(handle: DownloadHandle): InstallReconciliation
    suspend fun cancel(handle: DownloadHandle): InstallCancellation
}

interface InstallAttemptRecorder {
    fun prepared(handle: DownloadHandle, receipt: InstallReceipt)
    fun accepted(handle: DownloadHandle, receipt: InstallReceipt)
}
