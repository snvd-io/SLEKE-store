package com.sleke.home.components

import com.sleke.library.model.firebase.SlekeApkDto
import com.sleke.library.ui.SimpleAppUiState

data class SimpleApp(
    val name: String,
    val description: String = "",
    val publisher: String = "",
    val packageName: String = "",
    val versionDisplay: String = "",
    val type: String = "",
    val iconUrl: String = "",
    val appState: SimpleAppUiState = SimpleAppUiState.NotDownloaded
)

fun SlekeApkDto.toSimpleApp(appState: SimpleAppUiState = SimpleAppUiState.NotDownloaded): SimpleApp {
    return SimpleApp(
        name = name,
        description = description,
        publisher = publisher,
        packageName = packageName,
        versionDisplay = versionNameDisplay.ifEmpty { versionName },
        type = type,
        iconUrl = "",
        appState = appState
    )
} 