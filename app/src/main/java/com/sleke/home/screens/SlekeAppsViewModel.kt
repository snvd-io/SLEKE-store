package com.sleke.home.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sleke.library.data.repository.ApkRepository
import com.sleke.library.model.firebase.SlekeApkDto
import com.sleke.library.ui.SimpleAppUiState
import com.sleke.library.util.isAppInstalled
import com.sleke.library.worker.ApkDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@HiltViewModel
@OptIn(ExperimentalUuidApi::class)
class SlekeAppsViewModel @Inject constructor(
    private val repo: ApkRepository,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SlekeAppsUiState(isLoading = true))
    val uiState: StateFlow<SlekeAppsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val dtos = repo.slekeApks()
            val apps = dtos.map {
                val isAppInstalled = try {
                    context.packageManager.getPackageInfo(it.packageName, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
                SlekeApp(
                    apk = SlekeApkDto(
                        name = it.name,
                        link = it.link,
                        packageName = it.packageName,
                    ),
                    downloadState = if (isAppInstalled) {
                        SimpleAppUiState.Installed
                    } else {
                        SimpleAppUiState.NotDownloaded
                    },
                )
            }
            _uiState.value = SlekeAppsUiState(apps = apps)
        }
    }

    fun startDownload(apk: SlekeApkDto) {
        val id = ApkDownloadWorker.enqueue(workManager, apk.link, packageName = apk.name)
        _uiState.update { ui ->
            ui.copy(apps = ui.apps.map {
                if (it.apk == apk) it.copy(
                    workId = id,
                    downloadState = SimpleAppUiState.Downloading(0)
                ) else it
            })
        }
        observeWork(id)
    }

    private fun observeWork(workId: Uuid) {
        workManager.getWorkInfoByIdFlow(workId.toJavaUuid()).onEach { wi ->
            wi?.let {
                val newState = when (it.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> SimpleAppUiState.Downloading(
                        it.progress.getInt(ApkDownloadWorker.PROGRESS, 0)
                    )

                    WorkInfo.State.SUCCEEDED -> {
                        val pkgName = it.outputData.getString(ApkDownloadWorker.KEY_PACKAGE)!!
                        _uiState.update { ui ->
                            ui.copy(apps = ui.apps.map {
                                if (it.workId == workId) it.copy(
                                    apk = it.apk.copy(packageName = pkgName),
                                ) else it
                            })
                        }
                        val uri = it.outputData.getString(ApkDownloadWorker.KEY_APK_URI)!!
                        val isAppInstalled = context.isAppInstalled(pkgName)
                        if (isAppInstalled) SimpleAppUiState.Installed else SimpleAppUiState.Downloaded(
                            uri,
                            pkgName
                        )
                    }

                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        val error = it.outputData.getString(ApkDownloadWorker.KEY_ERROR)
                            ?: "Download failed"
                        Timber.tag("SlekeAppsScreen").e("Download failed: $error")
                        SimpleAppUiState.Error("Download failed")
                    }

                    else -> SimpleAppUiState.NotDownloaded
                }

                _uiState.update { ui ->
                    ui.copy(apps = ui.apps.map {
                        if (it.workId == workId) it.copy(downloadState = newState) else it
                    })
                }
            }
        }.launchIn(viewModelScope)
    }

    fun onPackageInstalled(packageName: String) {
        _uiState.update { ui ->
            ui.copy(apps = ui.apps.map { item ->
                if (item.apk.packageName == packageName)
                    item.copy(downloadState = SimpleAppUiState.Installed)
                else item
            })
        }
    }

    fun onPackageUninstalled(packageName: String) {
        _uiState.update { ui ->
            ui.copy(apps = ui.apps.map { item ->
                if (item.apk.packageName == packageName)
                    item.copy(downloadState = SimpleAppUiState.NotDownloaded)
                else item
            })
        }
    }
}