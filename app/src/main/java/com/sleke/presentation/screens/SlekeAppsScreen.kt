package com.sleke.presentation.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sleke.library.ui.components.CenteredMessageView
import com.sleke.library.util.installApp
import com.sleke.library.util.openApp
import com.sleke.library.util.uninstallApp
import com.sleke.library.ui.components.SimpleAppItem
import com.sleke.library.ui.components.UiStateContainer
import com.sleke.library.ui.components.toSimpleApp
import kotlin.uuid.ExperimentalUuidApi

private const val PROGRESS_KEY = "PROGRESS"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun SlekeAppsScreen(
    modifier: Modifier = Modifier,
    onNavigateToEnterprise: () -> Unit
) {
    val viewModel: SlekeAppsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Sleke Apps") },
                windowInsets = WindowInsets(0)
            )
        },
    ) { padding ->
        UiStateContainer(
            modifier = Modifier.padding(padding),
            isLoading = uiState.isLoading,
            error = uiState.error,
            data = uiState.apps,
            isEmpty = uiState.apps.isEmpty(),
            emptyContent = {
                CenteredMessageView(
                    message = "No apps available",
                    modifier = modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        ) { apps ->
            LazyColumn(
                modifier = modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(apps) { app ->
                    SimpleAppItem(
                        modifier = Modifier.zIndex(3f),
                        app = app.app.toSimpleApp(app.downloadState),
                        onDownload = { viewModel.startDownload(app.app) },
                        onOpen = {
                            context.openApp(app.app.packageName)
                        },
                        onInstall = { uri ->
                            context.installApp(uri)
                        },
                        onUninstall = {
                            context.uninstallApp(app.app.packageName)
                        }
                    )
                }
            }
        }
    }
}