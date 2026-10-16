package com.sleke.library.catalog.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sleke.library.R
import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.domain.CatalogIdentity
import com.sleke.library.downloads.domain.DownloadState
import com.sleke.library.ui.components.AppLogoIcon

@Composable
fun SlekeAppsContent(
    state: CatalogUiState,
    onSearch: (String) -> Unit,
    onOpenPackage: (String) -> Unit,
    onDownload: (CatalogApp) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryInstall: (CatalogApp) -> Unit = onDownload,
    onLaunchPackage: (String) -> Unit = onOpenPackage,
    onUninstallPackage: (String) -> Unit = {}
) {
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onSearch,
            singleLine = true,
            label = {
                Text(stringResource(R.string.catalog_search))
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        when (state.status) {
            CatalogStatus.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            CatalogStatus.Error -> CatalogMessage(stringResource(R.string.catalog_error), onRetry)
            CatalogStatus.Empty -> CatalogMessage(stringResource(R.string.catalog_empty))
            CatalogStatus.Populated -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.apps, key = {
                    "${it.identity.collection}/${it.identity.documentId}"
                }) { app ->
                    CatalogAppRow(
                        app,
                        state.downloads[app.packageName],
                        app.packageName in state.installedPackages,
                        {
                            onOpenPackage(app.packageName)
                        },
                        { onDownload(app) },
                        { onRetryInstall(app) },
                        {
                            onLaunchPackage(app.packageName)
                        },
                        { onUninstallPackage(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun CatalogMessage(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (onRetry !=
            null
        ) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.catalog_retry)) }
        }
    }
}

@Composable
internal fun CatalogAppRow(
    app: CatalogApp,
    download: DownloadState?,
    installed: Boolean,
    onDetails: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit
) {
    Card(onClick = onDetails, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogoIcon(app.iconUrl, app.packageName, app.title, Modifier.size(56.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        app.title.ifBlank {
                            app.packageName
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (app.publisher.isNotBlank()) {
                        Text(
                            app.publisher,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (app.versionLabel.isNotBlank()) {
                        Text(
                            app.versionLabel,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (app.rating >
                        0
                    ) {
                        Text(
                            "★ ${app.rating}  ${app.installs}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (app.description.isNotBlank()) {
                Text(
                    app.description,
                    maxLines = 3,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (!app.type.isNullOrBlank()) {
                Text(
                    app.type,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (download) {
                    is DownloadState.Downloading -> Text("${download.percent ?: 0}%")
                    DownloadState.Queued -> Text(stringResource(R.string.catalog_queued))
                    is DownloadState.Ready -> TextButton(onClick = onInstall) {
                        Text(stringResource(R.string.catalog_install))
                    }
                    DownloadState.AwaitingAccess -> Text(
                        stringResource(R.string.catalog_awaiting_access)
                    )
                    DownloadState.Submitting, DownloadState.Installing -> Text(
                        stringResource(R.string.catalog_installing)
                    )
                    DownloadState.PendingConfirmation -> Text(
                        stringResource(R.string.catalog_confirmation)
                    )
                    DownloadState.NeedsReconciliation -> Text(
                        stringResource(R.string.catalog_reconcile)
                    )
                    is DownloadState.Failed, DownloadState.Cancelled -> TextButton(
                        onClick = onInstall
                    ) {
                        Text(stringResource(R.string.catalog_retry))
                    }
                    else -> if (!installed) {
                        TextButton(onClick = onDownload) {
                            Text(stringResource(R.string.catalog_download))
                        }
                    }
                }
                if (installed ||
                    download == DownloadState.Installed ||
                    download == DownloadState.AlreadyCurrent
                ) {
                    TextButton(onClick = onOpen) { Text(stringResource(R.string.catalog_open)) }
                    TextButton(onClick = onUninstall) {
                        Text(stringResource(R.string.catalog_uninstall))
                    }
                    if (download ==
                        null
                    ) {
                        TextButton(onClick = onDownload) {
                            Text(stringResource(R.string.catalog_download))
                        }
                    }
                }
            }
        }
    }
}

internal val previewCatalogApp =
    CatalogApp(
        CatalogIdentity("sleke_apps", "sample"),
        "com.example.app",
        "https://example.test/app.apk",
        title = "Sleke Reader",
        description = "Your reading, in one place.",
        publisher = "Sleke",
        versionLabel = "2.0"
    )

@Preview(showBackground = true)
@Composable
private fun CatalogPopulatedPreview() {
    MaterialTheme {
        SlekeAppsContent(CatalogUiState(CatalogStatus.Populated, listOf(previewCatalogApp)), {
        }, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun CatalogLoadingPreview() {
    MaterialTheme { SlekeAppsContent(CatalogUiState(), {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun CatalogEmptyPreview() {
    MaterialTheme { SlekeAppsContent(CatalogUiState(CatalogStatus.Empty), {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun CatalogErrorPreview() {
    MaterialTheme { SlekeAppsContent(CatalogUiState(CatalogStatus.Error), {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun CatalogMessagePreview() {
    MaterialTheme { CatalogMessage("No apps found") }
}

@Preview(showBackground = true)
@Composable
private fun CatalogAppRowPreview() {
    MaterialTheme { CatalogAppRow(previewCatalogApp, null, false, {}, {}, {}, {}, {}) }
}
