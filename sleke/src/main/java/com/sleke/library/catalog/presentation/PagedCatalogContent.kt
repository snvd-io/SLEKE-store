package com.sleke.library.catalog.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.sleke.library.R
import com.sleke.library.catalog.domain.CatalogApp

@Composable
fun PagedCatalogContent(
    state: CatalogUiState,
    apps: LazyPagingItems<CatalogApp>,
    onSearch: (String) -> Unit,
    onOpenPackage: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.status != CatalogStatus.Populated) {
        SlekeAppsContent(state, onSearch, onOpenPackage, {}, onRetry, modifier)
        return
    }
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
        when (apps.loadState.refresh) {
            is LoadState.Error -> CatalogMessage(stringResource(R.string.catalog_error)) {
                apps.retry()
            }
            LoadState.Loading -> CircularProgressIndicator(Modifier.padding(24.dp))
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(apps.itemCount, key = { index ->
                    apps.peek(index)?.let { "${it.identity.collection}/${it.identity.documentId}" }
                        ?: "placeholder/$index"
                }) { index ->
                    apps[index]?.let { app ->
                        Card(onClick = { onOpenPackage(app.packageName) }) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row {
                                    com.sleke.library.ui.components.AppLogoIcon(
                                        app.iconUrl,
                                        app.packageName,
                                        app.title,
                                        Modifier.size(56.dp)
                                    )
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text(
                                            app.title.ifBlank {
                                                app.packageName
                                            },
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            app.publisher,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
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
                            }
                        }
                    }
                }
                if (apps.loadState.append == LoadState.Loading) item { CircularProgressIndicator() }
                if (apps.loadState.append is LoadState.Error) {
                    item {
                        TextButton(onClick = {
                            apps.retry()
                        }) { Text(stringResource(R.string.catalog_retry)) }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PagedCatalogLoadingPreview() {
    MaterialTheme { SlekeAppsContent(CatalogUiState(), {}, {}, {}, {}) }
}
