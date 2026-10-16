package com.sleke.library.downloads.data

import com.sleke.library.downloads.domain.InstallReceipt
import com.sleke.library.downloads.domain.InstallSubmission
import com.sleke.library.downloads.domain.ValidatedApk
import java.io.File

/** Platform seam for installer selection, submission and owned session recovery. */
interface OwnedInstallerDriver {
    fun preferred(): String
    fun systemPrompt(): String
    fun submit(installer: String, request: OwnedApkInstallRequest): InstallSubmission
    fun hasOwnedSession(sessionId: Int, packageName: String): Boolean
    fun abandonOwnedSession(sessionId: Int)
}

/** An operation's validated artifact and complete private file set. */
data class OwnedApkInstallRequest(
    val apk: ValidatedApk,
    val operationId: String,
    val files: List<File>,
    val onPrepared: (InstallReceipt) -> Unit,
    val onAccepted: (InstallReceipt) -> Unit,
    val onFinished: (Boolean) -> Unit = {}
) {
    init {
        require(files.isNotEmpty())
    }
}

/** Only throw when the failing call is known not to have handed off an installation. */
class OwnedPreAcceptanceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** Only surrounds staging/opening calls, never a commit or external launch. */
inline fun <T> withOwnedStaging(owned: Boolean, abandon: () -> Unit, stage: () -> T): T = try {
    stage()
} catch (exception: java.io.IOException) {
    if (!owned) throw exception
    runCatching(abandon)
    throw OwnedPreAcceptanceException("Unable to stage owned APK", exception)
}
