package com.sleke.library.ui.components

import com.sleke.library.domain.AppDomain
import com.sleke.library.model.firebase.AppDto
import com.sleke.library.model.firebase.SlekeApkDto
import com.sleke.library.ui.SimpleAppUiState

data class SimpleApp(
    val name: String,
    val description: String,
    val publisher: String,
    val packageName: String,
    val versionDisplay: String,
    val type: String? = null,
    val iconUrl: String? = null,
    val appState: SimpleAppUiState = SimpleAppUiState.NotDownloaded
)

fun SlekeApkDto.toSimpleApp(appState: SimpleAppUiState = SimpleAppUiState.NotDownloaded): SimpleApp {
    return SimpleApp(
        name = name,
        description = description,
        publisher = publisher,
        packageName = packageName,
        versionDisplay = versionNameDisplay.ifEmpty { versionName },
        type = type.orEmpty(),
        iconUrl = "",
        appState = appState
    )
}

fun AppDto.toSimpleApp(appState: SimpleAppUiState = SimpleAppUiState.NotDownloaded): SimpleApp {
    return SimpleApp(
        name = name,
        description = description,
        publisher = publisher,
        packageName = packageName,
        versionDisplay = versionNameDisplay,
        type = type,
        iconUrl = iconUrl,
        appState = appState
    )
}

fun AppDomain.toSimpleApp(appState: SimpleAppUiState = SimpleAppUiState.NotDownloaded): SimpleApp {
    return SimpleApp(
        name = name,
        description = description,
        publisher = publisher,
        packageName = packageName,
        versionDisplay = version.orEmpty(),
        type = type.orEmpty(),
        iconUrl = "",
        appState = appState
    )
}