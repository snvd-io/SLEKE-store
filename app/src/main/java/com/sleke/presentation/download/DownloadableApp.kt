package com.sleke.presentation.download

import com.sleke.library.ui.SimpleAppUiState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class DownloadableApp<T>(
    val app: T,
    val downloadState: SimpleAppUiState = SimpleAppUiState.NotDownloaded,
    val workId: Uuid? = null
) 