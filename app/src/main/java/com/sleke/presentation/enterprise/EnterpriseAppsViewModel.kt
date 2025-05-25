package com.sleke.presentation.enterprise

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.sleke.presentation.download.AppDownloadManager
import com.sleke.presentation.download.DownloadableApp
import com.sleke.library.data.repository.ApkRepository
import com.sleke.library.domain.AppDomain
import com.sleke.library.ui.SimpleAppUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class EnterpriseAppsUiState(
    val isLoading: Boolean = true,
    val apps: List<DownloadableApp<AppDomain>> = emptyList(),
    val error: String? = null,
    val hasAccess: Boolean = false,
    val enterpriseName: String = ""
)

@HiltViewModel
@OptIn(ExperimentalUuidApi::class)
class EnterpriseAppsViewModel @Inject constructor(
    private val apkRepository: ApkRepository,
    private val firebaseAuth: FirebaseAuth,
    private val downloadManager: AppDownloadManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnterpriseAppsUiState())
    val uiState: StateFlow<EnterpriseAppsUiState> = _uiState.asStateFlow()

    init {
        checkAccessAndLoadApps()
    }

    private fun checkAccessAndLoadApps() {
        viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser

                if (currentUser != null) {
                    _uiState.value = _uiState.value.copy(hasAccess = true)
                    loadEnterpriseData(currentUser.uid)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasAccess = false,
                        error = "No enterprise access"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    private suspend fun loadEnterpriseData(userId: String) {
        try {
            val enterpriseDetails = apkRepository.getEnterpriseDetails(userId)
            val apps = apkRepository.getEnterpriseApps(userId)
            val downloadableApps = apps.map { app ->
                val isAppInstalled = try {
                    context.packageManager.getPackageInfo(app.packageName, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
                DownloadableApp(
                    app = app,
                    downloadState = if (isAppInstalled) {
                        SimpleAppUiState.Installed
                    } else {
                        SimpleAppUiState.NotDownloaded
                    }
                )
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                apps = downloadableApps,
                enterpriseName = enterpriseDetails?.title ?: "Enterprise"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = e.message ?: "Failed to load enterprise data"
            )
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        checkAccessAndLoadApps()
    }

    fun startDownload(app: AppDomain) {
        val workId = downloadManager.startDownload(
            downloadUrl = app.downloadUrl,
            packageName = app.packageName,
            scope = viewModelScope
        ) { workId, newState ->
            updateAppDownloadState(workId, newState)
        }

        _uiState.update { ui ->
            ui.copy(apps = ui.apps.map { downloadableApp ->
                if (downloadableApp.app.packageName == app.packageName) {
                    downloadableApp.copy(
                        workId = workId,
                        downloadState = SimpleAppUiState.Downloading(0)
                    )
                } else {
                    downloadableApp
                }
            })
        }
    }

    private fun updateAppDownloadState(workId: Uuid, newState: SimpleAppUiState) {
        _uiState.update { ui ->
            ui.copy(apps = ui.apps.map { downloadableApp ->
                if (downloadableApp.workId == workId) {
                    downloadableApp.copy(downloadState = newState)
                } else {
                    downloadableApp
                }
            })
        }
    }

    fun onPackageInstalled(packageName: String) {
        _uiState.update { ui ->
            ui.copy(apps = ui.apps.map { downloadableApp ->
                if (downloadableApp.app.packageName == packageName) {
                    downloadableApp.copy(downloadState = SimpleAppUiState.Installed)
                } else {
                    downloadableApp
                }
            })
        }
    }

    fun onPackageUninstalled(packageName: String) {
        _uiState.update { ui ->
            ui.copy(apps = ui.apps.map { downloadableApp ->
                if (downloadableApp.app.packageName == packageName) {
                    downloadableApp.copy(downloadState = SimpleAppUiState.NotDownloaded)
                } else {
                    downloadableApp
                }
            })
        }
    }
} 