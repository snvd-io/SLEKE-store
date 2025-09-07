package com.sleke.presentation

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sleke.library.data.repository.ApkRepository
import com.sleke.library.model.firebase.AppDto
import com.sleke.library.model.firebase.SlekeApkDto
import com.sleke.library.ui.SimpleAppUiState
import com.sleke.library.util.isAppInstalled
import com.sleke.library.worker.ApkDownloadWorker
import com.aurora.store.data.repository.GPlayRepository
import com.sleke.library.data.datastore.SlekePreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gplayRepository: GPlayRepository,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context,
    private val apkRepository: ApkRepository,
    private val slekePreferencesDataStore: SlekePreferencesDataStore,
) : ViewModel() {

    private val _firebaseApps = MutableStateFlow<List<AppDto>>(emptyList())
    val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _searchQuery = MutableStateFlow<String>("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _appStates = MutableStateFlow<Map<String, SimpleAppUiState>>(emptyMap())
    val appStates: StateFlow<Map<String, SimpleAppUiState>> = _appStates

    val isEnterprise: StateFlow<Boolean> = slekePreferencesDataStore.signedAccount
        .map { signedAccount -> signedAccount?.isEnterprise ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val activeDownloads = mutableMapOf<String, Uuid>()

    init {
        refreshInstalledApps()
        viewModelScope.launch {
            val apps = apkRepository.getAllApks()
            _firebaseApps.update { apps }
            _isLoading.value = false
        }
    }

    @OptIn(FlowPreview::class)
    val pagedApps: Flow<PagingData<AppDto>> = combine(
        _searchQuery.debounce(300).distinctUntilChanged(),
        _firebaseApps
    ) { query, _ -> query }
        .flatMapLatest { query ->
            Pager(
                config = PagingConfig(
                    pageSize = 5,
                    enablePlaceholders = false,
                    prefetchDistance = 5
                )
            ) {
                AppDetailsPagingSource(
                    allAppDtos = _firebaseApps.value,
                    gplayRepository = gplayRepository,
                    query = query
                )
            }.flow
        }
        .cachedIn(viewModelScope)

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun downloadApp(app: AppDto) {
        if (app.packageName.isEmpty()) {
            Timber.e("Cannot download app with empty package name")
            updateAppState(app.packageName, SimpleAppUiState.Error("Invalid package name"))
            return
        }

        val slekeApk = SlekeApkDto(
            name = app.name,
            link = app.link,
            packageName = app.packageName
        )

        val workId = ApkDownloadWorker.enqueue(
            workManager,
            slekeApk.link,
            packageName = slekeApk.name
        )

        updateAppState(app.packageName, SimpleAppUiState.Downloading(0))
        activeDownloads[app.packageName] = workId

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId.toJavaUuid())
                .collect { workInfo ->
                    workInfo?.let {
                        when (it.state) {
                            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                                val progress = it.progress.getInt(ApkDownloadWorker.PROGRESS, 0)
                                updateAppState(
                                    app.packageName,
                                    SimpleAppUiState.Downloading(progress)
                                )
                            }

                            WorkInfo.State.SUCCEEDED -> {
                                val uri = it.outputData.getString(ApkDownloadWorker.KEY_APK_URI)!!
                                val pkgName =
                                    it.outputData.getString(ApkDownloadWorker.KEY_PACKAGE)!!
                                if (context.isAppInstalled(pkgName)) {
                                    updateAppState(app.packageName, SimpleAppUiState.Installed)
                                } else {
                                    updateAppState(
                                        app.packageName,
                                        SimpleAppUiState.Downloaded(uri, pkgName)
                                    )
                                }
                                activeDownloads.remove(app.packageName)
                            }

                            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                                updateAppState(
                                    app.packageName,
                                    SimpleAppUiState.Error("Download failed")
                                )
                                activeDownloads.remove(app.packageName)
                            }

                            else -> {}
                        }
                    }
                }
        }
    }

    fun onPackageInstalled(packageName: String) {
        updateAppState(packageName, SimpleAppUiState.Installed)
    }

    fun onPackageUninstalled(packageName: String) {
        updateAppState(packageName, SimpleAppUiState.NotDownloaded)
    }

    fun getAppState(packageName: String): SimpleAppUiState {
        return _appStates.value[packageName] ?: if (context.isAppInstalled(packageName)) {
            SimpleAppUiState.Installed.also {
                updateAppState(packageName, it)
            }
        } else {
            SimpleAppUiState.NotDownloaded.also {
                updateAppState(packageName, it)
            }
        }
    }

    private fun updateAppState(packageName: String, state: SimpleAppUiState) {
        _appStates.value = _appStates.value.toMutableMap().apply {
            put(packageName, state)
        }
    }

    private fun refreshInstalledApps() {
        viewModelScope.launch {
            try {
                val installedApps = context.packageManager
                    .getInstalledApplications(PackageManager.GET_META_DATA)
                    .map { it.packageName }
                    .associateWith { SimpleAppUiState.Installed }

                _appStates.value = installedApps
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh installed apps")
            }
        }
    }
}