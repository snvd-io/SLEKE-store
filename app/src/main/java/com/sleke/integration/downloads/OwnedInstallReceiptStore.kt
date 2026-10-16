package com.sleke.integration.downloads

import com.sleke.library.downloads.data.AccessDecision
import com.sleke.library.downloads.data.DownloadAccessSource
import com.sleke.library.downloads.data.FileOperationStore
import com.sleke.library.downloads.data.OperationRecord
import com.sleke.library.downloads.domain.DownloadFailure
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.DownloadState
import com.sleke.library.downloads.domain.InstallReceipt

/** Shares the durable custom-operation journal; there is no second, competing operation owner. */
class OwnedInstallReceiptStore(private val journal: FileOperationStore) {
    companion object {
        const val EXTRA_OPERATION_ID = "com.sleke.store.OWNED_INSTALL_OPERATION"
    }

    fun retainedSessionIds(): Set<Int> = journal.recordsStrict().mapNotNull { record ->
        record.receipt?.takeIf { it.kind == "session" }?.reference?.toIntOrNull()
    }.toSet()

    /** Called on IO. A legacy package-only broadcast never changes an owned operation. */
    fun recordStatus(
        operationId: String?,
        sessionId: Int,
        packageName: String,
        versionCode: Long,
        status: Int
    ): Boolean {
        val handle =
            operationId?.let {
                runCatching {
                    java.util.UUID.fromString(it)
                    DownloadHandle(it)
                }.getOrNull()
            }
                ?: return false
        var matched = false
        journal.update(handle) { record ->
            val artifact = record.artifact
            if (artifact == null ||
                artifact.packageName != packageName ||
                artifact.versionCode != versionCode ||
                record.receipt != InstallReceipt("session", sessionId.toString())
            ) {
                return@update record
            }
            if (status == -1 &&
                (record.cancelRequested || record.state.isTerminal)
            ) {
                return@update record
            }
            matched = true
            if (record.state.isTerminal) {
                record
            } else {
                record.copy(
                    accepted = true,
                    state = when (status) {
                        0 -> DownloadState.Installed
                        -1 -> DownloadState.PendingConfirmation
                        else -> DownloadState.Failed(DownloadFailure.INSTALLATION)
                    }
                )
            }
        }
        return matched
    }
    fun recordMicroGResult(
        operationId: String?,
        packageName: String,
        versionCode: Long,
        success: Boolean,
        installedVersion: Long?
    ): Boolean = recordExternalResult(
        operationId,
        "microg",
        packageName,
        versionCode,
        success,
        installedVersion
    )

    /** An activity result belongs to one immutable attempt; RESULT_OK also needs package facts. */
    fun recordExternalResult(
        operationId: String?,
        kind: String,
        packageName: String,
        versionCode: Long,
        success: Boolean,
        installedVersion: Long?
    ): Boolean {
        if (kind !in setOf("native", "microg")) return false
        val handle =
            operationId?.let {
                runCatching {
                    java.util.UUID.fromString(it)
                    DownloadHandle(it)
                }.getOrNull()
            }
                ?: return false
        var matched = false
        journal.update(handle) { record ->
            val artifact = record.artifact
            val receipt = record.receipt
            if (artifact == null ||
                artifact.packageName != packageName ||
                artifact.versionCode != versionCode ||
                receipt == null ||
                receipt.reference != handle.value ||
                receipt.kind !in setOf(kind, "$kind:launched")
            ) {
                return@update record
            }
            matched = true
            val installed = success && installedVersion != null && installedVersion >= versionCode
            if (installed) {
                record.copy(state = DownloadState.Installed, accepted = true)
            } else if (record.state.isTerminal) {
                record
            } else {
                record.copy(
                    accepted = true,
                    state = if (success) {
                        DownloadState.NeedsReconciliation
                    } else {
                        DownloadState.Failed(DownloadFailure.INSTALLATION)
                    }
                )
            }
        }
        return matched
    }
    fun externalLaunchRecord(
        operationId: String?,
        kind: String,
        packageName: String,
        versionCode: Long
    ): OperationRecord? {
        val handle =
            operationId?.let {
                runCatching {
                    java.util.UUID.fromString(it)
                    DownloadHandle(it)
                }.getOrNull()
            }
                ?: return null
        return journal.read(handle)?.takeIf {
            matchesUnlaunched(it, kind, packageName, versionCode) &&
                !it.state.isTerminal
        }
    }

    /** Claims this prompt once, durably, before it can launch any external activity. */
    fun prepareExternalLaunch(
        operationId: String?,
        kind: String,
        packageName: String,
        versionCode: Long,
        access: DownloadAccessSource
    ): Boolean {
        val record =
            externalLaunchRecord(operationId, kind, packageName, versionCode) ?: return false
        var prepared = false
        journal.update(record.handle) { current ->
            if (!matchesUnlaunched(current, kind, packageName, versionCode) ||
                current.state.isTerminal
            ) {
                return@update current
            }
            val decision = if (current.cancelRequested) {
                AccessDecision.REVOKED
            } else {
                access.check(
                    current.owner
                )
            }
            if (decision != AccessDecision.ALLOWED) {
                unlaunched(current, decision)
            } else {
                prepared = true
                current.copy(
                    receipt = InstallReceipt("$kind:launched", current.handle.value),
                    accepted = false,
                    state = DownloadState.PendingConfirmation
                )
            }
        }
        return prepared
    }

    fun recordExternalNotLaunched(
        operationId: String?,
        kind: String,
        packageName: String,
        versionCode: Long,
        decision: AccessDecision,
        claimedByThisWrapper: Boolean = false
    ) {
        val handle = operationId?.let {
            runCatching {
                java.util.UUID.fromString(it)
                DownloadHandle(it)
            }.getOrNull()
        } ?: return
        journal.update(handle) { record ->
            val artifact = record.artifact
            val receipt = record.receipt
            val matching = kind in setOf("native", "microg") &&
                artifact != null &&
                artifact.packageName == packageName &&
                artifact.versionCode == versionCode &&
                receipt?.reference == handle.value &&
                (
                    receipt.kind == kind ||
                        (claimedByThisWrapper && receipt.kind == "$kind:launched")
                    )
            if (matching && !record.state.isTerminal) unlaunched(record, decision) else record
        }
    }

    private fun matchesUnlaunched(
        record: OperationRecord,
        kind: String,
        packageName: String,
        versionCode: Long
    ): Boolean {
        val artifact = record.artifact ?: return false
        return kind in setOf("native", "microg") &&
            artifact.packageName == packageName &&
            artifact.versionCode == versionCode &&
            record.receipt == InstallReceipt(kind, record.handle.value)
    }

    private fun unlaunched(record: OperationRecord, decision: AccessDecision): OperationRecord =
        if (decision ==
            AccessDecision.UNAVAILABLE
        ) {
            record.copy(receipt = null, accepted = false, state = DownloadState.AwaitingAccess)
        } else {
            record.copy(
                receipt = null,
                accepted = false,
                cancelRequested = true,
                state = DownloadState.Cancelled
            )
        }
}
