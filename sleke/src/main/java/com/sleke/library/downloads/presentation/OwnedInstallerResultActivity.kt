package com.sleke.library.downloads.presentation

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import com.sleke.library.downloads.data.AccessDecision
import com.sleke.library.downloads.data.DownloadAccessSource
import com.sleke.library.downloads.data.FileOperationStore
import com.sleke.library.downloads.data.OwnedInstallReceiptStore
import com.sleke.library.downloads.data.runOwnedExternalLaunch
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.DownloadState
import com.sleke.library.downloads.domain.InstallReceipt
import com.sleke.library.downloads.domain.InstalledPackageSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OwnedInstallerResultEntryPoint {
    fun journal(): FileOperationStore
    fun receipts(): OwnedInstallReceiptStore
    fun access(): DownloadAccessSource
    fun packages(): InstalledPackageSource
    fun uiHost(): InstallerUiHost
}

/** Owns the result of one native/microG confirmation without re-launching it on recreation. */
class OwnedInstallerResultActivity : Activity() {
    companion object {
        private val completionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val REQUEST_CODE = 1
        private const val EXTRA_INSTALL_INTENT = "owned_install_intent"
        private const val EXTRA_KIND = "owned_install_kind"
        private const val EXTRA_PACKAGE = "owned_install_package"
        private const val EXTRA_VERSION = "owned_install_version"
        private const val STATE_LAUNCHED = "owned_install_launched"

        fun launchOrNotify(
            context: Context,
            request: InstallPromptRequest,
            kind: String,
            installIntent: Intent
        ) {
            val receipt = InstallReceipt(kind, request.operationId)
            val wrapper = Intent(context, OwnedInstallerResultActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                data = Uri.parse("sleke-owned-install://${request.operationId}/$kind")
                putExtra(OwnedInstallReceiptStore.EXTRA_OPERATION_ID, request.operationId)
                putExtra(EXTRA_PACKAGE, request.packageName)
                putExtra(EXTRA_VERSION, request.versionCode)
                putExtra(EXTRA_KIND, kind)
                putExtra(EXTRA_INSTALL_INTENT, installIntent)
            }
            val uiHost = EntryPointAccessors.fromApplication(
                context.applicationContext,
                OwnedInstallerResultEntryPoint::class.java
            ).uiHost()
            request.onPrepared(receipt)
            if (uiHost.isForeground) {
                context.startActivity(wrapper)
            } else {
                uiHost.notifyInstallPrompt(request, wrapper)
            }
            request.onAccepted(receipt)
        }
    }

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var externalLaunched = false
    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            OwnedInstallerResultEntryPoint::class.java
        )
    }
    private val operationId get() = intent.getStringExtra(
        OwnedInstallReceiptStore.EXTRA_OPERATION_ID
    )
    private val kind get() = intent.getStringExtra(EXTRA_KIND).orEmpty()
    private val packageNameRequested get() = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
    private val versionRequested get() = intent.getLongExtra(EXTRA_VERSION, -1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalLaunched = savedInstanceState?.getBoolean(STATE_LAUNCHED) == true
        if (externalLaunched) return
        val external = IntentCompat.getParcelableExtra(
            intent,
            EXTRA_INSTALL_INTENT,
            Intent::class.java
        )
        if (external == null) return finish()
        activityScope.launch {
            try {
                val record = withContext(Dispatchers.IO) {
                    entryPoint.receipts().externalLaunchRecord(
                        operationId,
                        kind,
                        packageNameRequested,
                        versionRequested
                    )
                }
                if (record == null) return@launch finish()
                val decision = entryPoint.access().check(record.owner)
                if (record.cancelRequested || decision != AccessDecision.ALLOWED) {
                    withContext(Dispatchers.IO) {
                        entryPoint.receipts().recordExternalNotLaunched(
                            operationId,
                            kind,
                            packageNameRequested,
                            versionRequested,
                            if (record.cancelRequested) AccessDecision.REVOKED else decision
                        )
                    }
                    return@launch finish()
                }
                val permitted = withContext(Dispatchers.IO) {
                    entryPoint.receipts().prepareExternalLaunch(
                        operationId,
                        kind,
                        packageNameRequested,
                        versionRequested,
                        entryPoint.access()
                    )
                }
                if (!permitted) return@launch finish()
                // startActivityForResult cannot return a result from a separately requested task.
                external.flags =
                    external.flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv() and
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK.inv()
                external.putExtra(Intent.EXTRA_RETURN_RESULT, true)
                try {
                    val decisionAtLaunch = runOwnedExternalLaunch(
                        record.owner,
                        entryPoint.access(),
                        { entryPoint.journal().isCancellationRequested(record.handle) }
                    ) {
                        externalLaunched = true
                        startActivityForResult(external, REQUEST_CODE)
                    }
                    if (decisionAtLaunch != AccessDecision.ALLOWED) {
                        withContext(Dispatchers.IO) {
                            entryPoint.receipts().recordExternalNotLaunched(
                                operationId,
                                kind,
                                packageNameRequested,
                                versionRequested,
                                decisionAtLaunch,
                                claimedByThisWrapper = true
                            )
                        }
                        return@launch finish()
                    }
                    withContext(Dispatchers.IO) {
                        operationId?.let {
                            entryPoint.journal().accepted(
                                DownloadHandle(it),
                                InstallReceipt("$kind:launched", it)
                            )
                        }
                    }
                } catch (_: android.content.ActivityNotFoundException) {
                    complete(false)
                } catch (_: SecurityException) {
                    complete(false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    withContext(Dispatchers.IO) {
                        operationId?.let { attempt ->
                            entryPoint.journal().update(DownloadHandle(attempt)) {
                                if (it.state.isTerminal) {
                                    it
                                } else {
                                    it.copy(
                                        state = DownloadState.NeedsReconciliation
                                    )
                                }
                            }
                        }
                    }
                    finish()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A preflight or journal failure must never launch or replay an installer.
                finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_LAUNCHED, externalLaunched)
        super.onSaveInstanceState(outState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) complete(resultCode == RESULT_OK)
    }

    private fun complete(success: Boolean) {
        val attempt = operationId
        val installerKind = kind
        val requestedPackage = packageNameRequested
        val requestedVersion = versionRequested
        completionScope.launch {
            try {
                val installed = if (success) {
                    entryPoint.packages().versionCode(
                        requestedPackage
                    )
                } else {
                    null
                }
                entryPoint.receipts().recordExternalResult(
                    attempt,
                    installerKind,
                    requestedPackage,
                    requestedVersion,
                    success,
                    installed
                )
                if (attempt != null) {
                    getSystemService(NotificationManager::class.java)
                        .cancel("sleke-install:$attempt", attempt.hashCode())
                }
            } finally {
                withContext(Dispatchers.Main.immediate) { finish() }
            }
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}
