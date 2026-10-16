package com.sleke.library.downloads.data

import com.sleke.library.catalog.domain.CatalogScope
import com.sleke.library.downloads.domain.CustomDownloadCoordinator
import com.sleke.library.downloads.domain.CustomDownloadRequest
import com.sleke.library.downloads.domain.DownloadFailure
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.DownloadOwner
import com.sleke.library.downloads.domain.DownloadState
import com.sleke.library.downloads.domain.InstallReconciliation
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface CustomDownloadScheduler {
    suspend fun active(packageName: String): DownloadHandle?
    suspend fun enqueue(handle: DownloadHandle, packageName: String): DownloadHandle
    suspend fun cancel(handle: DownloadHandle)
}

/** Starts work and observes its journal. Installation is exclusively a processor/worker effect. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomDownloadCoordinatorImpl(
    private val store: FileOperationStore,
    private val scheduler: CustomDownloadScheduler,
    private val sessions: EnterpriseSessionRepository,
    private val files: ApkArtifactFiles,
    private val processor: DownloadProcessor,
    private val recoveryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : CustomDownloadCoordinator {
    private val mutation = Mutex()
    private var initialized = false

    override suspend fun findActive(packageName: String, scope: CatalogScope): DownloadHandle? =
        withContext(Dispatchers.IO) {
            val owner = ownerOrNull(scope) ?: return@withContext null
            store.records().lastOrNull {
                it.request.packageName == packageName &&
                    it.owner == owner &&
                    !it.state.isTerminal
            }?.handle
        }

    override suspend fun start(request: CustomDownloadRequest): DownloadHandle =
        withContext(Dispatchers.IO) {
            require(request.packageName.isNotBlank()) { "Missing package name" }
            require(
                runCatching {
                    URI(request.downloadUrl).scheme?.lowercase() in
                        listOf("http", "https")
                }.getOrDefault(false)
            ) { "Invalid APK URL" }
            mutation.withLock {
                val owner =
                    ownerOrNull(request.scope) ?: error("Enterprise access is not available")
                val active = scheduler.active(request.packageName)
                val prior = store.records().lastOrNull {
                    it.request.packageName == request.packageName &&
                        !it.state.isTerminal
                }
                if (prior != null &&
                    prior.owner == owner &&
                    (
                        prior.handle == active ||
                            prior.state !is DownloadState.Downloading &&
                            prior.state != DownloadState.Queued
                        )
                ) {
                    return@withLock prior.handle
                }
                if (prior != null && prior.owner != owner) {
                    processor.cancel(prior.handle)
                    scheduler.cancel(prior.handle)
                    check(store.read(prior.handle)?.state?.isTerminal == true) {
                        "Previous installer request is still pending"
                    }
                }
                val candidate = DownloadHandle(UUID.randomUUID().toString())
                store.write(OperationRecord(candidate, request, owner))
                try {
                    val actual = scheduler.enqueue(candidate, request.packageName)
                    if (actual != candidate) {
                        store.update(candidate) { it.copy(state = DownloadState.Cancelled) }
                        val retained = store.read(actual)
                        check(
                            retained != null &&
                                retained.owner == owner &&
                                !retained.state.isTerminal
                        ) {
                            "Retained operation ownership is unavailable"
                        }
                    }
                    actual
                } catch (failure: Exception) {
                    store.update(candidate) {
                        it.copy(state = DownloadState.Failed(DownloadFailure.TRANSFER))
                    }
                    throw failure
                }
            }
        }

    override fun observe(handle: DownloadHandle): Flow<DownloadState> =
        combine(store.observe(handle), sessions.state) {
                state,
                session
            ->
            val record = store.read(handle)
            if (record?.owner is DownloadOwner.Enterprise && !belongsTo(record.owner, session)) {
                if (session == EnterpriseSessionState.Restoring ||
                    session is EnterpriseSessionState.Checking ||
                    session is EnterpriseSessionState.Unavailable
                ) {
                    DownloadState.AwaitingAccess
                } else {
                    DownloadState.Cancelled
                }
            } else {
                state
            }
        }.transformWhile {
            emit(it)
            !it.isTerminal
        }.flowOn(Dispatchers.IO)

    override suspend fun retryInstall(handle: DownloadHandle): DownloadHandle =
        withContext(Dispatchers.IO) {
            mutation.withLock {
                val old = requireNotNull(store.read(handle)) { "Operation unavailable" }
                check(ownerOrNull(old.request.scope) == old.owner) {
                    "Operation belongs to another identity"
                }
                val reconciliation = processor.reconcile(handle)
                if (reconciliation == InstallReconciliation.Pending ||
                    reconciliation == InstallReconciliation.Installed ||
                    reconciliation == InstallReconciliation.Unknown
                ) {
                    return@withLock handle
                }
                val artifact =
                    requireNotNull(old.artifact) {
                        "Download the APK again before retrying installation"
                    }
                val candidate = DownloadHandle(UUID.randomUUID().toString())
                val copied = files.copyForRetry(candidate, artifact)
                store.write(
                    OperationRecord(
                        candidate,
                        old.request,
                        old.owner,
                        DownloadState.Ready(copied),
                        copied
                    )
                )
                scheduler.cancel(handle)
                store.update(handle) { it.copy(state = DownloadState.Cancelled) }
                val actual = scheduler.enqueue(candidate, old.request.packageName)
                check(actual == candidate) { "Another operation already owns this package" }
                actual
            }
        }

    override suspend fun cancel(handle: DownloadHandle) = withContext(Dispatchers.IO) {
        val record = store.read(handle) ?: return@withContext
        if (record.owner is DownloadOwner.Enterprise &&
            identityOf(sessions.state.value) != record.owner.uid
        ) {
            return@withContext
        }
        processor.cancel(handle)
        scheduler.cancel(handle)
    }

    override suspend fun cancelEnterpriseWork() = withContext(Dispatchers.IO) {
        store.records().filter {
            it.owner is DownloadOwner.Enterprise &&
                (
                    !it.state.isTerminal ||
                        it.state is DownloadState.Failed ||
                        it.state == DownloadState.Cancelled
                    )
        }.forEach {
            processor.cancel(it.handle)
            scheduler.cancel(it.handle)
        }
    }

    /** One application-owned observer; workers remain the durable execution owner. */
    @Synchronized fun initialize() {
        if (initialized) return
        initialized = true
        recoveryScope.launch {
            sessions.state.collect { session ->
                store.records().filter {
                    !it.state.isTerminal ||
                        it.state is DownloadState.Failed ||
                        it.state == DownloadState.Cancelled
                }.forEach { record ->
                    val owner = record.owner as? DownloadOwner.Enterprise
                    val lostIdentity =
                        owner != null && identityOf(session)?.let { it != owner.uid } == true
                    val lostAccess =
                        owner != null &&
                            (
                                session == EnterpriseSessionState.SignedOut ||
                                    session is EnterpriseSessionState.NoMembership ||
                                    lostIdentity ||
                                    session is EnterpriseSessionState.Allowed &&
                                    !belongsTo(owner, session)
                                )
                    if (lostAccess) {
                        processor.cancel(record.handle)
                        scheduler.cancel(record.handle)
                    } else if (!record.state.isTerminal) {
                        mutation.withLock {
                            val current = store.read(record.handle) ?: return@withLock
                            if (!current.state.isTerminal &&
                                scheduler.active(current.request.packageName) == null
                            ) {
                                // Recover a process stop between writing the journal and scheduling work.
                                scheduler.enqueue(current.handle, current.request.packageName)
                            }
                        }
                        if (record.state !is DownloadState.Downloading &&
                            record.state != DownloadState.Queued
                        ) {
                            launch { processor.run(record.handle) }
                        }
                    }
                }
            }
        }
    }

    private suspend fun ownerOrNull(scope: CatalogScope): DownloadOwner? {
        if (scope != CatalogScope.Enterprise) return DownloadOwner.Public
        val state = sessions.state.first {
            it != EnterpriseSessionState.Restoring &&
                it !is EnterpriseSessionState.Checking
        }
        return (state as? EnterpriseSessionState.Allowed)?.let {
            DownloadOwner.Enterprise(it.identity.uid, it.enterpriseId)
        }
    }

    private fun identityOf(state: EnterpriseSessionState): String? = when (state) {
        is EnterpriseSessionState.Allowed -> state.identity.uid
        is EnterpriseSessionState.Checking -> state.identity.uid
        is EnterpriseSessionState.NoMembership -> state.identity.uid
        is EnterpriseSessionState.Unavailable -> state.identity.uid
        EnterpriseSessionState.Restoring, EnterpriseSessionState.SignedOut -> null
    }

    private fun belongsTo(owner: DownloadOwner.Enterprise, state: EnterpriseSessionState): Boolean =
        when (state) {
            is EnterpriseSessionState.Allowed ->
                owner.uid == state.identity.uid &&
                    owner.enterpriseId == state.enterpriseId
            else -> false
        }
}
