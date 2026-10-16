package com.sleke.library.downloads.data

import com.sleke.library.downloads.domain.ApkInstallGateway
import com.sleke.library.downloads.domain.DownloadFailure
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.DownloadPolicy
import com.sleke.library.downloads.domain.DownloadState
import com.sleke.library.downloads.domain.InstallCancellation
import com.sleke.library.downloads.domain.InstallReceipt
import com.sleke.library.downloads.domain.InstallReconciliation
import com.sleke.library.downloads.domain.InstallSubmission
import com.sleke.library.downloads.domain.InstalledPackageSource
import com.sleke.library.downloads.domain.ValidatedApk
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PreferredApkInstallGateway(
    private val driver: OwnedInstallerDriver,
    private val store: FileOperationStore,
    private val packages: InstalledPackageSource,
    private val access: DownloadAccessSource,
    private val resolveArtifact: (String) -> File
) : ApkInstallGateway {
    override suspend fun submit(handle: DownloadHandle, apk: ValidatedApk): InstallSubmission =
        withContext(Dispatchers.IO) {
            val record = store.read(handle) ?: return@withContext InstallSubmission.Rejected
            if (record.artifact != apk ||
                record.request.packageName != apk.packageName
            ) {
                return@withContext InstallSubmission.Rejected
            }
            if (record.cancelRequested || record.state.isTerminal) {
                val completed =
                    record.state == DownloadState.Installed ||
                        record.state == DownloadState.AlreadyCurrent
                return@withContext if (completed &&
                    !DownloadPolicy.shouldInstall(
                        packages.versionCode(apk.packageName),
                        apk.versionCode
                    )
                ) {
                    InstallSubmission.Completed
                } else {
                    InstallSubmission.Rejected
                }
            }
            if (!DownloadPolicy.shouldInstall(
                    packages.versionCode(apk.packageName),
                    apk.versionCode
                )
            ) {
                return@withContext InstallSubmission.Completed
            }
            val previousReceipt = record.receipt
            if (previousReceipt != null) {
                return@withContext when (reconcile(handle)) {
                    InstallReconciliation.Installed -> InstallSubmission.Completed
                    InstallReconciliation.Failed -> InstallSubmission.Rejected
                    InstallReconciliation.Pending -> InstallSubmission.Accepted(previousReceipt)
                    else -> InstallSubmission.Unknown
                }
            }
            val file = try {
                resolveArtifact(apk.reference)
            } catch (
                _: Exception
            ) {
                return@withContext InstallSubmission.Rejected
            }
            if (!file.isFile || file.length() == 0L) return@withContext InstallSubmission.Rejected
            val request = OwnedApkInstallRequest(
                apk,
                handle.value,
                listOf(file),
                onPrepared = { receipt ->
                    // Access invalidation can race staging. Reject before the external side effect.
                    requireNotNull(
                        store.update(handle) { current ->
                            check(
                                !current.cancelRequested &&
                                    !current.state.isTerminal &&
                                    current.state != DownloadState.AwaitingAccess &&
                                    access.check(current.owner) == AccessDecision.ALLOWED
                            ) {
                                "Operation cannot hand off without current access"
                            }
                            current.copy(receipt = receipt)
                        }
                    )
                },
                onAccepted = { receipt ->
                    store.update(handle) {
                        if (it.state.isTerminal || it.receipt != receipt) {
                            it
                        } else {
                            it.copy(
                                accepted = true,
                                state = if (it.state ==
                                    DownloadState.NeedsReconciliation
                                ) {
                                    DownloadState.Installing
                                } else {
                                    it.state
                                }
                            )
                        }
                    }
                },
                onFinished = { success ->
                    store.update(handle) {
                        if (it.state.isTerminal) {
                            it
                        } else {
                            it.copy(
                                state = if (success) {
                                    DownloadState.Installed
                                } else {
                                    DownloadState.Failed(DownloadFailure.INSTALLATION)
                                }
                            )
                        }
                    }
                }
            )
            try {
                val preferred = driver.preferred()
                // Even an unexpected driver failure is ambiguous after this boundary; never replay it.
                request.onPrepared(InstallReceipt("attempt", handle.value))
                try {
                    driver.submit(preferred, request)
                } catch (safe: OwnedPreAcceptanceException) {
                    if (store.read(handle)?.accepted == true ||
                        preferred == driver.systemPrompt()
                    ) {
                        InstallSubmission.Unknown
                    } else {
                        // Exactly one declared prompt fallback, only after a proven pre-handoff failure.
                        store.update(handle) {
                            if (it.state.isTerminal) {
                                it
                            } else {
                                it.copy(
                                    receipt = null,
                                    accepted = false
                                )
                            }
                        }
                        request.onPrepared(InstallReceipt("attempt", handle.value))
                        driver.submit(driver.systemPrompt(), request)
                    }
                }
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (
                _: SecurityException
            ) {
                InstallSubmission.Rejected
            } catch (
                _: OwnedPreAcceptanceException
            ) {
                InstallSubmission.Rejected
            } catch (_: Exception) {
                InstallSubmission.Unknown
            }
        }

    override suspend fun reconcile(handle: DownloadHandle): InstallReconciliation =
        withContext(Dispatchers.IO) {
            val record = store.read(handle) ?: return@withContext InstallReconciliation.Unknown
            val apk = record.artifact ?: return@withContext if (record.receipt == null) {
                InstallReconciliation.NotSubmitted
            } else {
                InstallReconciliation.Unknown
            }
            if (!DownloadPolicy.shouldInstall(
                    packages.versionCode(apk.packageName),
                    apk.versionCode
                )
            ) {
                return@withContext InstallReconciliation.Installed
            }
            if (record.state is DownloadState.Failed ||
                record.state == DownloadState.Cancelled
            ) {
                return@withContext InstallReconciliation.Failed
            }
            val receipt = record.receipt ?: return@withContext InstallReconciliation.NotSubmitted
            val hasSession = receipt.kind == "session" &&
                receipt.reference.toIntOrNull()?.let {
                    runCatching { driver.hasOwnedSession(it, apk.packageName) }.getOrDefault(false)
                } == true
            if (hasSession) {
                InstallReconciliation.Pending
            } else {
                InstallReconciliation.Unknown
            }
        }

    override suspend fun cancel(handle: DownloadHandle): InstallCancellation =
        withContext(Dispatchers.IO) {
            val record =
                store.read(handle) ?: return@withContext InstallCancellation.AlreadyFinished
            val apk = record.artifact ?: return@withContext if (record.receipt == null) {
                InstallCancellation.AlreadyFinished
            } else {
                InstallCancellation.Unsupported
            }
            if (!DownloadPolicy.shouldInstall(
                    packages.versionCode(apk.packageName),
                    apk.versionCode
                )
            ) {
                return@withContext InstallCancellation.AlreadyFinished
            }
            val receipt = record.receipt ?: return@withContext InstallCancellation.AlreadyFinished
            val sessionId = if (receipt.kind == "session") receipt.reference.toIntOrNull() else null
            if (sessionId == null) return@withContext InstallCancellation.Unsupported
            try {
                if (!driver.hasOwnedSession(
                        sessionId,
                        apk.packageName
                    )
                ) {
                    return@withContext InstallCancellation.Unsupported
                }
                driver.abandonOwnedSession(sessionId)
                InstallCancellation.Cancelled
            } catch (_: Exception) {
                InstallCancellation.Unsupported
            }
        }
}
