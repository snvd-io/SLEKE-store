package com.aurora.store.data.installer.base

import com.aurora.store.data.room.download.Download
import java.io.File

/** An operation's complete artifact set. Never resolve its package in the Play download tree. */
data class OwnedInstallRequest(
    val download: Download,
    val operationId: String,
    val files: List<File>,
    val onPrepared: (HostInstallReceipt) -> Unit,
    val onAccepted: (HostInstallReceipt) -> Unit,
    val onFinished: (Boolean) -> Unit = {}
) {
    init {
        require(files.isNotEmpty())
        require(download.sharedLibs.isEmpty()) {
            "Owned APK requests cannot resolve shared Play libraries"
        }
    }
}

data class HostInstallReceipt(val kind: String, val reference: String)

sealed interface OwnedInstallSubmission {
    data class Accepted(val receipt: HostInstallReceipt) : OwnedInstallSubmission
    data object Completed : OwnedInstallSubmission
    data object Rejected : OwnedInstallSubmission
    data object Unknown : OwnedInstallSubmission
}

typealias OwnedPreAcceptanceException =
    com.sleke.library.downloads.data.OwnedPreAcceptanceException

/** Only surrounds staging/opening calls, never a commit or external launch. */
internal inline fun <T> withOwnedStaging(owned: Boolean, abandon: () -> Unit, stage: () -> T): T =
    com.sleke.library.downloads.data.withOwnedStaging(owned, abandon, stage)
