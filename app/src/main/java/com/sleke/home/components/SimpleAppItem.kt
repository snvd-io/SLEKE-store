package com.sleke.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sleke.library.ui.SimpleAppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleAppItem(
    title: String,
    appState: SimpleAppUiState,
    onDownload: () -> Unit,
    onInstall: (apkUri: String) -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f, false)
                    .padding(end = 8.dp),
                overflow = TextOverflow.Ellipsis
            )
            when (appState) {
                is SimpleAppUiState.NotDownloaded -> {
                    Button(onClick = onDownload) {
                        Text("Download")
                    }
                }

                is SimpleAppUiState.Downloading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { appState.progress / 100f },
                            strokeWidth = 2.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            "${appState.progress}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                is SimpleAppUiState.Downloaded -> {
                    Button(onClick = { onInstall(appState.apkUri) }) {
                        Text("Install")
                    }
                }

                is SimpleAppUiState.Installed -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpen) {
                            Text("Open")
                        }
                        Button(onClick = onUninstall) {
                            Text("Uninstall")
                        }
                    }
                }

                is SimpleAppUiState.Error -> {
                    TextButton(onClick = onDownload) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SimpleAppItemPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SimpleAppItem(
            title = "Example App",
            appState = SimpleAppUiState.NotDownloaded,
            onDownload = {},
            onOpen = {},
            onInstall = {},
            onUninstall = {}
        )
        SimpleAppItem(
            title = "Downloading…",
            appState = SimpleAppUiState.Downloading(progress = 42),
            onDownload = {},
            onOpen = {},
            onInstall = {},
            onUninstall = {}
        )
        SimpleAppItem(
            title = "Downloaded App",
            appState = SimpleAppUiState.Downloaded(
                apkUri = "content://example/app.apk",
                packageName = "com.example.app"
            ),
            onDownload = {},
            onOpen = {},
            onInstall = {},
            onUninstall = {}
        )
        SimpleAppItem(
            title = "Installed App",
            appState = SimpleAppUiState.Installed,
            onDownload = {},
            onOpen = {},
            onInstall = {},
            onUninstall = {}
        )
    }
}