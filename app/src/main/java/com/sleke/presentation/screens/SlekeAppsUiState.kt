package com.sleke.presentation.screens

import com.sleke.library.model.firebase.SlekeApkDto
import com.sleke.library.ui.SimpleAppUiState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class SlekeAppsUiState(
    val isLoading: Boolean = false,
    val apps: List<SlekeApp> = emptyList(),
    val error: String? = null,
    val hasEnterpriseAccess: Boolean = false
)

@OptIn(ExperimentalUuidApi::class)
data class SlekeApp(
    val app: SlekeApkDto,
    val workId: Uuid? = null,
    val downloadState: SimpleAppUiState = SimpleAppUiState.NotDownloaded,
    val extractedPackageName: String? = null,
    val apkUri: String? = null
)