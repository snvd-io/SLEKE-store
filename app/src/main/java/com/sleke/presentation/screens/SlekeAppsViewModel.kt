package com.sleke.presentation.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.sleke.presentation.download.AppDownloadManager
import com.sleke.library.data.repository.ApkRepository
import com.sleke.library.model.firebase.SlekeApkDto
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


@HiltViewModel
@OptIn(ExperimentalUuidApi::class)
class SlekeAppsViewModel @Inject constructor(
    private val repo: ApkRepository,
    private val appDownloadManager: AppDownloadManager,
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SlekeAppsUiState(isLoading = true))
    val uiState: StateFlow<SlekeAppsUiState> = _uiState.asStateFlow()

    init {
        loadApps()
        checkEnterpriseAccess()
    }

    private fun loadApps() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                val slekeApps = repo.slekeApks()
                val apps = slekeApps.map { apk ->
                    val isAppInstalled = try {
                        context.packageManager.getPackageInfo(apk.packageName, 0)
                        true
                    } catch (e: PackageManager.NameNotFoundException) {
                        false
                    }
                    SlekeApp(
                        app = apk,
                        downloadState = if (isAppInstalled) {
                            SimpleAppUiState.Installed
                        } else {
                            SimpleAppUiState.NotDownloaded
                        },
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    apps = apps
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    private fun checkEnterpriseAccess() {
        viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null) {
                    val hasAccess = repo.hasEnterpriseAccess(currentUser.uid)
                    _uiState.value =
                        _uiState.value.copy(hasEnterpriseAccess = hasAccess)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(hasEnterpriseAccess = false)
            }
        }
    }

    fun startDownload(apk: SlekeApkDto) {
        val workId = appDownloadManager.startDownload(
            downloadUrl = apk.link,
            packageName = apk.packageName,
            scope = viewModelScope
        ) { workId, newState ->
            updateAppDownloadState(workId, newState)
        }

        _uiState.update { ui ->
            ui.copy(apps = ui.apps.map { slekeApp ->
                if (slekeApp.app == apk) {
                    slekeApp.copy(
                        workId = workId,
                        downloadState = SimpleAppUiState.Downloading(0)
                    )
                } else {
                    slekeApp
                }
            })
        }
    }

    private fun updateAppDownloadState(workId: Uuid, newState: SimpleAppUiState) {
        _uiState.update { ui ->
            ui.copy(apps = ui.apps.map { slekeApp ->
                if (slekeApp.workId == workId) {
                    slekeApp.copy(downloadState = newState)
                } else {
                    slekeApp
                }
            })
        }
    }

    fun onPackageInstalled(packageName: String) {
        _uiState.update { ui ->
            ui.copy(apps = ui.apps.map { slekeApp ->
                if (slekeApp.app.packageName == packageName) {
                    slekeApp.copy(downloadState = SimpleAppUiState.Installed)
                } else {
                    slekeApp
                }
            })
        }
    }

    fun onPackageUninstalled(packageName: String) {
        _uiState.update { ui ->
            ui.copy(apps = ui.apps.map { slekeApp ->
                if (slekeApp.app.packageName == packageName) {
                    slekeApp.copy(downloadState = SimpleAppUiState.NotDownloaded)
                } else {
                    slekeApp
                }
            })
        }
    }
}