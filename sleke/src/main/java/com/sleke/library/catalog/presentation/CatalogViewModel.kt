package com.sleke.library.catalog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.domain.CatalogRepository
import com.sleke.library.catalog.domain.CatalogScope
import com.sleke.library.downloads.domain.CustomDownloadCoordinator
import com.sleke.library.downloads.domain.CustomDownloadRequest
import com.sleke.library.downloads.domain.DownloadFailure
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.DownloadState
import com.sleke.library.downloads.domain.InstalledPackageSource
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
abstract class CatalogViewModel(
    private val catalogScope: CatalogScope,
    repository: CatalogRepository,
    private val downloads: CustomDownloadCoordinator,
    private val packages: InstalledPackageSource,
    protected val sessions: EnterpriseSessionRepository
) : ViewModel() {
    private val presenter = CatalogPresenter(catalogScope, repository, viewModelScope)
    private val operations = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    private val handles = MutableStateFlow<Map<String, DownloadHandle>>(emptyMap())
    private val installed = MutableStateFlow<Set<String>>(emptySet())
    private val collectors = mutableMapOf<String, Job>()
    val session = sessions.state
    val state: StateFlow<CatalogUiState> = combine(
        presenter.state,
        operations,
        handles,
        installed
    ) {
            content,
            work,
            ids,
            installedPackages
        ->
        content.copy(downloads = work, handles = ids, installedPackages = installedPackages)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CatalogUiState())

    init {
        presenter.load()
        viewModelScope.launch {
            combine(presenter.state, session) { content, currentSession ->
                Triple(content.status, content.apps.map(CatalogApp::packageName), currentSession)
            }.distinctUntilChanged().collectLatest { (status, names, _) ->
                if (status != CatalogStatus.Populated) return@collectLatest
                installed.value = names.filter { packages.versionCode(it) != null }.toSet()
                for (packageName in names) {
                    if (packageName in handles.value) continue
                    try {
                        downloads.findActive(packageName, catalogScope)?.let {
                            observe(packageName, it)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        operations.update {
                            it +
                                (packageName to DownloadState.Failed(DownloadFailure.RECOVERY))
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            var first = true
            var ownerIdentity = session.value.identityKey()
            var enterpriseId = (session.value as? EnterpriseSessionState.Allowed)?.enterpriseId
            session.collectLatest { current ->
                if (!first || catalogScope == CatalogScope.Enterprise) presenter.load()
                first = false
                val identityChanged = current.identityKey() != ownerIdentity
                val enterpriseChanged =
                    current is EnterpriseSessionState.Allowed &&
                        current.enterpriseId != enterpriseId
                if (catalogScope == CatalogScope.Enterprise &&
                    (
                        identityChanged ||
                            enterpriseChanged ||
                            current is EnterpriseSessionState.SignedOut ||
                            current is EnterpriseSessionState.NoMembership
                        )
                ) {
                    collectors.values.forEach { it.cancel() }
                    collectors.clear()
                    operations.value = emptyMap()
                    handles.value = emptyMap()
                }
                ownerIdentity = current.identityKey()
                if (current is EnterpriseSessionState.Allowed) enterpriseId = current.enterpriseId
            }
        }
    }

    fun search(query: String) = presenter.search(query)
    fun retry() = presenter.retry()
    fun refreshMembership() {
        viewModelScope.launch { sessions.refresh() }
    }

    fun enterpriseSignOut() {
        viewModelScope.launch {
            try {
                sessions.revokeAccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Continue every cleanup stage even if one reports a failure.
            }
            try {
                downloads.cancelEnterpriseWork()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Session cleanup must still run if durable download cleanup reports a failure.
            }
            try {
                sessions.signOut()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operations.update { current ->
                    current.mapValues { DownloadState.Failed(DownloadFailure.ACCESS) }
                }
            }
        }
    }

    fun refreshInstalled() {
        viewModelScope.launch {
            installed.value =
                state.value.apps.map(CatalogApp::packageName).filter {
                    packages.versionCode(it) != null
                }.toSet()
        }
    }

    fun startDownload(app: CatalogApp) {
        viewModelScope.launch {
            try {
                val owner = session.value
                val handle = downloads.start(
                    CustomDownloadRequest(app.packageName, app.downloadUrl, catalogScope)
                )
                if (catalogScope != CatalogScope.Enterprise ||
                    sameEnterpriseOwner(owner, session.value)
                ) {
                    observe(app.packageName, handle)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operations.update {
                    it +
                        (app.packageName to DownloadState.Failed(DownloadFailure.TRANSFER))
                }
            }
        }
    }

    fun retryInstall(app: CatalogApp) {
        val failed = operations.value[app.packageName] as? DownloadState.Failed
        if (failed?.reason == DownloadFailure.TRANSFER ||
            failed?.reason == DownloadFailure.VALIDATION
        ) {
            startDownload(app)
            return
        }
        val handle = handles.value[app.packageName] ?: return
        viewModelScope.launch {
            try {
                val owner = session.value
                val retained = downloads.retryInstall(handle)
                if (catalogScope != CatalogScope.Enterprise ||
                    sameEnterpriseOwner(owner, session.value)
                ) {
                    observe(app.packageName, retained)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operations.update {
                    it +
                        (app.packageName to DownloadState.Failed(DownloadFailure.STAGING))
                }
            }
        }
    }

    private fun observe(packageName: String, handle: DownloadHandle) {
        handles.update { it + (packageName to handle) }
        collectors.remove(packageName)?.cancel()
        collectors[packageName] = viewModelScope.launch {
            try {
                downloads.observe(handle).transformWhile { update ->
                    if (handles.value[packageName] ==
                        handle
                    ) {
                        operations.update { it + (packageName to update) }
                    }
                    emit(update)
                    !update.isTerminal
                }.collect()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (handles.value[packageName] ==
                    handle
                ) {
                    operations.update {
                        it +
                            (packageName to DownloadState.Failed(DownloadFailure.RECOVERY))
                    }
                }
            } finally {
                if (collectors[packageName] == coroutineContext[Job]) collectors.remove(packageName)
            }
        }
    }
}

private fun sameEnterpriseOwner(
    first: EnterpriseSessionState,
    second: EnterpriseSessionState
): Boolean = first is EnterpriseSessionState.Allowed &&
    second is EnterpriseSessionState.Allowed &&
    first.identity == second.identity &&
    first.enterpriseId == second.enterpriseId

private fun EnterpriseSessionState.identityKey(): String? = when (this) {
    is EnterpriseSessionState.Allowed -> identity.uid
    is EnterpriseSessionState.Checking -> identity.uid
    is EnterpriseSessionState.NoMembership -> identity.uid
    is EnterpriseSessionState.Unavailable -> identity.uid
    EnterpriseSessionState.Restoring, EnterpriseSessionState.SignedOut -> null
}
