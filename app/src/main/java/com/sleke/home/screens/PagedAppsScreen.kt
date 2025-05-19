package com.sleke.home.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.aurora.store.R
import com.sleke.home.HomeViewModel
import com.sleke.library.model.firebase.Apk
import com.sleke.library.ui.SimpleAppUiState
import com.sleke.library.util.installApp
import com.sleke.library.util.openApp
import com.sleke.library.util.uninstallApp
import com.sleke.store.compose.ui.components.SkeletonAppItem
import kotlin.uuid.ExperimentalUuidApi
import com.sleke.store.compose.ui.components.SkeletonAppItem
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun PagedAppsScreen(
    onAppClick: (Apk) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val appStates by viewModel.appStates.collectAsState()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val pagedApps = viewModel.pagedApps.collectAsLazyPagingItems()
    val context = LocalContext.current

    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val pkg = intent.data?.schemeSpecificPart ?: return
                when (intent.action) {
                    Intent.ACTION_PACKAGE_ADDED -> viewModel.onPackageInstalled(pkg)
                    Intent.ACTION_PACKAGE_REMOVED -> viewModel.onPackageUninstalled(pkg)
                }
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(searchQuery) {
        pagedApps.refresh()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search apps...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focusManager.clearFocus()
            })
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading || pagedApps.loadState.refresh is LoadState.Loading -> {
                    LoadingShimmerList()
                }

                pagedApps.loadState.refresh is LoadState.Error -> {
                    val error = (pagedApps.loadState.refresh as LoadState.Error).error
                    SideEffect {
                        Timber.e(error, "Error loading apps: ${error.localizedMessage}")
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_apps),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Error Loading Apps",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error.localizedMessage ?: "Unknown error occurred",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                pagedApps.itemCount == 0 && pagedApps.loadState.refresh is LoadState.NotLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_apps),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "No apps found" else "No results for \"$searchQuery\"",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                else -> {
                    AppsList(
                        pagedApps = pagedApps,
                        appStates = appStates,
                        onAppClick = onAppClick,
                        onDownload = { app -> viewModel.downloadApp(app) },
                        onInstall = { uri, packageName -> context.installApp(uri) },
                        onOpen = { packageName -> context.openApp(packageName) },
                        onUninstall = { packageName -> context.uninstallApp(packageName) }
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingShimmerList() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(10) {
            SkeletonAppItem()
        }
    }
}

@Composable
fun AppsList(
    pagedApps: LazyPagingItems<Apk>,
    appStates: Map<String, SimpleAppUiState>,
    onAppClick: (Apk) -> Unit,
    onDownload: (Apk) -> Unit,
    onInstall: (String, String) -> Unit,
    onOpen: (String) -> Unit,
    onUninstall: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(pagedApps.itemCount) { index ->
            val app = pagedApps[index]

            if (app != null) {
                val appState = appStates[app.packageName] ?: SimpleAppUiState.NotDownloaded

                EnhancedAppItem(
                    app = app,
                    appState = appState,
                    onClick = { onAppClick(app) },
                    onDownload = { onDownload(app) },
                    onInstall = { uri, pkgName -> onInstall(uri, pkgName) },
                    onOpen = { onOpen(app.packageName) },
                    onUninstall = { onUninstall(app.packageName) }
                )
            } else {
                SkeletonAppItem()
            }
        }

        if (pagedApps.loadState.append is LoadState.Loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        if (pagedApps.loadState.append is LoadState.Error) {
            item {
                val error = (pagedApps.loadState.append as LoadState.Error).error
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error loading more items",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = error.localizedMessage
                                ?: "Unknown error occurred",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(
                                alpha = 0.7f
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedAppItem(
    app: Apk,
    appState: SimpleAppUiState,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (String, String) -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onClick = {
            if (appState !is SimpleAppUiState.Downloading) {
                expanded = !expanded
            }
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(app.iconUrl.ifEmpty { "https://play-lh.googleusercontent.com/icon?id=${app.packageName}&s=120" })
                        .crossfade(true)
                        .build(),
                    contentDescription = "App icon",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    loading = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    },
                    error = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_apps),
                            contentDescription = "App icon placeholder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = app.publisher,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (app.rating > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_star),
                                    contentDescription = "Rating",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = String.format("%.1f", app.rating),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    if (app.versionNameDisplay.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = app.versionNameDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                AppStateStatusRow(appState, onDownload, onInstall, onOpen, onUninstall)
            }
        }
    }
}

@Composable
fun AppStateStatusRow(
    appState: SimpleAppUiState,
    onDownload: () -> Unit,
    onInstall: (uri: String, packageName: String) -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit
) {
    when (appState) {
        is SimpleAppUiState.NotDownloaded -> {
            IconButton(onClick = onDownload) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_download),
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        is SimpleAppUiState.Downloading -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 4.dp)
            ) {
                CircularProgressIndicator(
                    progress = { appState.progress / 100f },
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    "${appState.progress}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        is SimpleAppUiState.Downloaded -> {
            IconButton(onClick = { onInstall(appState.apkUri, appState.packageName) }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_install),
                    contentDescription = "Install",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        is SimpleAppUiState.Installed -> {
            Row {
                IconButton(onClick = onUninstall) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = "Uninstall",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onOpen) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_apps),
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        is SimpleAppUiState.Error -> {
            IconButton(onClick = onDownload) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_download),
                    contentDescription = "Retry",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}