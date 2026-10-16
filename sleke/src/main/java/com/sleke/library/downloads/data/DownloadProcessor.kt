package com.sleke.library.downloads.data

import com.sleke.library.downloads.domain.ApkInstallGateway
import com.sleke.library.downloads.domain.CustomDownloadRequest
import com.sleke.library.downloads.domain.DownloadFailure
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.DownloadOwner
import com.sleke.library.downloads.domain.DownloadPolicy
import com.sleke.library.downloads.domain.DownloadState
import com.sleke.library.downloads.domain.InstallCancellation
import com.sleke.library.downloads.domain.InstallReconciliation
import com.sleke.library.downloads.domain.InstallSubmission
import com.sleke.library.downloads.domain.InstalledPackageSource
import com.sleke.library.downloads.domain.ValidatedApk
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AccessDecision { ALLOWED, UNAVAILABLE, REVOKED }
interface DownloadAccessSource {
    fun check(owner: DownloadOwner): AccessDecision
}
interface ApkTransferSource {
    suspend fun download(
        request: CustomDownloadRequest,
        handle: DownloadHandle,
        progress: suspend (Int?) -> Unit
    ): ValidatedApk
    fun purge(reference: String)
}

/** Executes durable operations; screen collectors never invoke this processor. */
class DownloadProcessor(
    private val store: FileOperationStore,
    private val transfer: ApkTransferSource,
    private val installer: ApkInstallGateway,
    private val packages: InstalledPackageSource,
    private val access: DownloadAccessSource
) {
    private val handoff = Mutex()
    private val executions = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    suspend fun run(handle: DownloadHandle) = executions.computeIfAbsent(handle.value) {
        Mutex()
    }.withLock { process(handle) }

    suspend fun reconcile(handle: DownloadHandle): InstallReconciliation {
        val result = installer.reconcile(handle)
        if (result ==
            InstallReconciliation.Installed
        ) {
            updateActive(handle) { it.copy(state = DownloadState.Installed) }
        }
        return result
    }

    suspend fun cancel(handle: DownloadHandle) {
        val before = store.update(handle) {
            if (it.state == DownloadState.Installed ||
                it.state == DownloadState.AlreadyCurrent
            ) {
                it
            } else {
                it.copy(
                    cancelRequested = true,
                    state = if (!it.accepted &&
                        it.receipt == null
                    ) {
                        DownloadState.Cancelled
                    } else {
                        it.state
                    }
                )
            }
        } ?: return
        if (before.state == DownloadState.Installed ||
            before.state == DownloadState.AlreadyCurrent
        ) {
            return
        }
        if (before.state is DownloadState.Failed || before.state == DownloadState.Cancelled) {
            store.update(handle) {
                it.copy(state = DownloadState.Cancelled, cancelRequested = true)
            }
            transfer.purge(before.artifact?.reference ?: handle.value)
            return
        }
        handoff.withLock {
            val current = store.read(handle) ?: return@withLock
            if (current.receipt != null || current.accepted) {
                val result = installer.cancel(handle)
                store.update(handle) {
                    it.copy(
                        state = when (result) {
                            InstallCancellation.Cancelled -> DownloadState.Cancelled
                            InstallCancellation.AlreadyFinished -> DownloadState.Installed
                            InstallCancellation.Unsupported -> DownloadState.NeedsReconciliation
                        }
                    )
                }
                if (result ==
                    InstallCancellation.Cancelled
                ) {
                    current.artifact?.let { transfer.purge(it.reference) }
                }
            } else {
                transfer.purge(before.artifact?.reference ?: handle.value)
            }
        }
    }

    private suspend fun process(handle: DownloadHandle) {
        var record = store.read(handle) ?: return
        if (record.state.isTerminal) return
        try {
            if (record.state in
                listOf(
                    DownloadState.Submitting,
                    DownloadState.PendingConfirmation,
                    DownloadState.Installing,
                    DownloadState.NeedsReconciliation
                )
            ) {
                val reconciliation = installer.reconcile(handle)
                if (reconciliation == InstallReconciliation.NotSubmitted &&
                    record.receipt == null &&
                    !record.accepted &&
                    !record.cancelRequested
                ) {
                    // A process stopped after the local transition, before any external handoff.
                    // The gateway established that continuing cannot replay an install.
                    record =
                        store.update(handle) {
                            it.copy(
                                state =
                                it.artifact?.let(DownloadState::Ready) ?: DownloadState.Queued
                            )
                        }
                            ?: return
                } else {
                    val state = when (reconciliation) {
                        InstallReconciliation.Pending -> DownloadState.PendingConfirmation
                        InstallReconciliation.Installed -> DownloadState.Installed
                        InstallReconciliation.Failed -> DownloadState.Failed(
                            DownloadFailure.INSTALLATION
                        )
                        InstallReconciliation.NotSubmitted,
                        InstallReconciliation.Unknown -> DownloadState.NeedsReconciliation
                    }
                    updateActive(handle) { it.copy(state = state) }
                    return
                }
            }
            if (!authorize(record)) return
            val apk = record.artifact ?: transfer.download(record.request, handle) { progress ->
                updateActive(handle) { it.copy(state = DownloadState.Downloading(progress)) }
            }
            record = store.read(handle) ?: return
            if (record.state.isTerminal) {
                transfer.purge(apk.reference)
                return
            }
            updateActive(handle) { it.copy(artifact = apk, state = DownloadState.Ready(apk)) }
            record = store.read(handle) ?: return
            if (!authorize(record)) return
            if (!DownloadPolicy.shouldInstall(
                    packages.versionCode(apk.packageName),
                    apk.versionCode
                )
            ) {
                updateActive(handle) { it.copy(state = DownloadState.AlreadyCurrent) }
                transfer.purge(apk.reference)
                return
            }
            handoff.withLock {
                val submitting = store.update(handle) {
                    if (it.state.isTerminal) it else it.copy(state = DownloadState.Submitting)
                } ?: return@withLock
                if (submitting.state != DownloadState.Submitting) return@withLock
                // Access loss can race the local staging transition.
                if (!authorize(submitting)) return@withLock
                val outcome = installer.submit(handle, apk)
                updateActive(handle) {
                    // Activity preflight or an external claim may complete before submit returns.
                    if (it.state == DownloadState.AwaitingAccess &&
                        it.receipt == null &&
                        !it.accepted
                    ) {
                        it
                    } else if (outcome is InstallSubmission.Accepted &&
                        it.receipt != null &&
                        it.receipt != outcome.receipt
                    ) {
                        it
                    } else {
                        when (outcome) {
                            is InstallSubmission.Accepted -> it.copy(
                                state = DownloadState.PendingConfirmation,
                                receipt = outcome.receipt,
                                accepted = true
                            )
                            InstallSubmission.Completed -> it.copy(
                                state = DownloadState.Installed,
                                accepted = true
                            )
                            InstallSubmission.Rejected -> it.copy(
                                state = DownloadState.Failed(DownloadFailure.INSTALLATION)
                            )
                            InstallSubmission.Unknown -> it.copy(
                                state = DownloadState.NeedsReconciliation
                            )
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            try {
                val current = store.read(handle)
                if (current?.accepted != true && current?.receipt == null) {
                    val explicitCancel =
                        current?.cancelRequested == true ||
                            current?.state == DownloadState.Cancelled
                    store.update(handle) {
                        if (it.state.isTerminal) {
                            it
                        } else {
                            it.copy(
                                state = if (explicitCancel) {
                                    DownloadState.Cancelled
                                } else {
                                    it.artifact?.let(DownloadState::Ready) ?: DownloadState.Queued
                                }
                            )
                        }
                    }
                    // Scheduler interruption retains the request; only explicit cancellation revokes it.
                    if (explicitCancel || current?.artifact == null) {
                        transfer.purge(current?.artifact?.reference ?: handle.value)
                    }
                }
            } catch (failure: Exception) {
                cancelled.addSuppressed(failure)
            }
            throw cancelled
        } catch (_: Exception) {
            val current = store.read(handle)
            val state = if (current?.receipt != null ||
                current?.state == DownloadState.Submitting
            ) {
                DownloadState.NeedsReconciliation
            } else {
                DownloadState.Failed(DownloadFailure.TRANSFER)
            }
            updateActive(handle) { it.copy(state = state) }
        }
    }

    private suspend fun authorize(record: OperationRecord): Boolean =
        when (access.check(record.owner)) {
            AccessDecision.ALLOWED -> true
            AccessDecision.UNAVAILABLE -> {
                updateActive(record.handle) { it.copy(state = DownloadState.AwaitingAccess) }
                false
            }
            AccessDecision.REVOKED -> {
                updateActive(record.handle) { it.copy(state = DownloadState.Cancelled) }
                record.artifact?.let { transfer.purge(it.reference) }
                false
            }
        }

    private fun updateActive(
        handle: DownloadHandle,
        transform: (OperationRecord) -> OperationRecord
    ) = store.update(handle) { if (it.state.isTerminal) it else transform(it) }
}
