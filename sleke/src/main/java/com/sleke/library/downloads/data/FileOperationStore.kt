package com.sleke.library.downloads.data

import com.sleke.library.catalog.domain.CatalogScope
import com.sleke.library.downloads.domain.CustomDownloadRequest
import com.sleke.library.downloads.domain.DownloadFailure
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.DownloadOwner
import com.sleke.library.downloads.domain.DownloadState
import com.sleke.library.downloads.domain.InstallAttemptRecorder
import com.sleke.library.downloads.domain.InstallReceipt
import com.sleke.library.downloads.domain.ValidatedApk
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Custom-operation journal, separate from the application's Room database. */
data class OperationRecord(
    val handle: DownloadHandle,
    val request: CustomDownloadRequest,
    val owner: DownloadOwner,
    val state: DownloadState = DownloadState.Queued,
    val artifact: ValidatedApk? = null,
    val receipt: InstallReceipt? = null,
    val accepted: Boolean = false,
    val cancelRequested: Boolean = false
)

class FileOperationStore(private val directory: File) : InstallAttemptRecorder {
    private val lock = Any()
    private val cancellations = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Populated by journal reads and writes; safe for the final Main-thread handoff guard. */
    fun isCancellationRequested(id: DownloadHandle): Boolean = cancellations.contains(id.value)
    private val streams = mutableMapOf<String, MutableStateFlow<DownloadState>>()

    private fun file(handle: DownloadHandle): File {
        UUID.fromString(handle.value)
        return File(directory, "${handle.value}.properties")
    }

    fun read(handle: DownloadHandle): OperationRecord? = synchronized(lock) {
        val source = file(handle)
        if (!source.exists()) return@synchronized null
        val p = Properties().apply { source.inputStream().use(::load) }
        require(p.getProperty("format") == "1")
        val request =
            CustomDownloadRequest(
                p.required("package"),
                p.required("url"),
                CatalogScope.valueOf(p.required("scope"))
            )
        val owner = if (p.required("owner") == "public") {
            DownloadOwner.Public
        } else {
            DownloadOwner.Enterprise(p.required("uid"), p.required("enterprise"))
        }
        val artifact = p.getProperty("artifact")?.let {
            ValidatedApk(
                it,
                request.packageName,
                p.required("version").toLong(),
                p.required("sdk").toInt()
            )
        }
        val state = when (p.required("phase")) {
            "queued" -> DownloadState.Queued
            "downloading" -> DownloadState.Downloading(p.getProperty("progress")?.toIntOrNull())
            "ready" -> DownloadState.Ready(requireNotNull(artifact))
            "access" -> DownloadState.AwaitingAccess
            "submitting" -> DownloadState.Submitting
            "pending" -> DownloadState.PendingConfirmation
            "installing" -> DownloadState.Installing
            "installed" -> DownloadState.Installed
            "current" -> DownloadState.AlreadyCurrent
            "failed" -> DownloadState.Failed(DownloadFailure.valueOf(p.required("failure")))
            "cancelled" -> DownloadState.Cancelled
            "unknown" -> DownloadState.NeedsReconciliation
            else -> error("Unknown custom operation phase")
        }
        val receipt = p.getProperty("receipt")?.let { InstallReceipt(p.required("installer"), it) }
        OperationRecord(
            handle,
            request,
            owner,
            state,
            artifact,
            receipt,
            p.getProperty("accepted") == "true",
            p.getProperty("cancelRequested") == "true"
        ).also { if (it.cancelRequested) cancellations.add(handle.value) }
    }

    fun write(record: OperationRecord) = synchronized(lock) {
        // Publish cancellation before fallible disk work so deferred UI dispatch fails closed.
        if (record.cancelRequested) cancellations.add(record.handle.value)
        directory.mkdirs()
        val p = Properties()
        fun put(key: String, value: Any) {
            p.setProperty(key, value.toString())
        }
        put("format", 1)
        put("package", record.request.packageName)
        put("url", record.request.downloadUrl)
        put("scope", record.request.scope.name)
        when (val owner = record.owner) {
            DownloadOwner.Public -> put("owner", "public")
            is DownloadOwner.Enterprise -> {
                put("owner", "enterprise")
                put("uid", owner.uid)
                put("enterprise", owner.enterpriseId)
            }
        }
        val phase = when (val state = record.state) {
            DownloadState.Queued -> "queued"
            is DownloadState.Downloading -> {
                state.percent?.let { put("progress", it) }
                "downloading"
            }
            is DownloadState.Ready -> "ready"
            DownloadState.AwaitingAccess -> "access"
            DownloadState.Submitting -> "submitting"
            DownloadState.PendingConfirmation -> "pending"
            DownloadState.Installing -> "installing"
            DownloadState.Installed -> "installed"
            DownloadState.AlreadyCurrent -> "current"
            is DownloadState.Failed -> {
                put("failure", state.reason.name)
                "failed"
            }
            DownloadState.Cancelled -> "cancelled"
            DownloadState.NeedsReconciliation -> "unknown"
        }
        put("phase", phase)
        record.artifact?.let {
            put("artifact", it.reference)
            put("version", it.versionCode)
            put("sdk", it.targetSdk)
        }
        record.receipt?.let {
            put("receipt", it.reference)
            put("installer", it.kind)
        }
        put("accepted", record.accepted)
        put("cancelRequested", record.cancelRequested)
        val target = file(record.handle)
        val pending = File(directory, "${record.handle.value}.pending")
        FileOutputStream(pending).use { output ->
            p.store(output, null)
            output.fd.sync()
        }
        Files.move(
            pending.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
        stream(record.handle, record.state).value = record.state
    }

    fun update(
        handle: DownloadHandle,
        transform: (OperationRecord) -> OperationRecord
    ): OperationRecord? = synchronized(lock) {
        val previous = read(handle) ?: return@synchronized null
        transform(previous).also(::write)
    }

    fun recordsStrict(): List<OperationRecord> = synchronized(lock) {
        directory.listFiles().orEmpty().filter {
            it.extension == "properties"
        }.map { requireNotNull(read(DownloadHandle(it.nameWithoutExtension))) }
    }

    fun records(): List<OperationRecord> = synchronized(lock) {
        directory.listFiles().orEmpty().filter {
            it.extension == "properties"
        }.mapNotNull { source ->
            val handle = DownloadHandle(source.nameWithoutExtension)
            try {
                read(handle)
            } catch (_: Exception) {
                stream(handle, DownloadState.NeedsReconciliation).value =
                    DownloadState.NeedsReconciliation
                null
            }
        }
    }

    fun observe(handle: DownloadHandle): Flow<DownloadState> = synchronized(lock) {
        val initial = try {
            read(handle)?.state ?: DownloadState.Failed(DownloadFailure.RECOVERY)
        } catch (
            _: Exception
        ) {
            DownloadState.NeedsReconciliation
        }
        stream(handle, initial)
    }

    override fun prepared(handle: DownloadHandle, receipt: InstallReceipt) {
        update(handle) {
            if (it.state ==
                DownloadState.Cancelled
            ) {
                throw kotlin.coroutines.cancellation.CancellationException(
                    "Operation cancelled before handoff"
                )
            }
            if (it.state.isTerminal) it else it.copy(receipt = receipt)
        }
    }

    override fun accepted(handle: DownloadHandle, receipt: InstallReceipt) {
        update(handle) {
            if (it.state.isTerminal) it else it.copy(receipt = receipt, accepted = true)
        }
    }

    private fun stream(handle: DownloadHandle, initial: DownloadState) =
        streams.getOrPut(handle.value) { MutableStateFlow(initial) }
    private fun Properties.required(key: String): String =
        requireNotNull(getProperty(key)) { "Incomplete custom operation" }
}
