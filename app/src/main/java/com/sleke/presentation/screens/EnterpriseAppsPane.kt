package com.sleke.presentation.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.store.R
import com.sleke.library.domain.AppDomain
import com.sleke.library.ui.components.EnterpriseUiStateContainer
import com.sleke.library.util.installApp
import com.sleke.library.util.openApp
import com.sleke.library.util.uninstallApp
import com.sleke.library.ui.components.SimpleAppItem
import com.sleke.library.ui.components.toSimpleApp
import com.sleke.presentation.enterprise.EnterpriseAppsUiState
import com.sleke.presentation.enterprise.EnterpriseAppsViewModel
import com.topjohnwu.superuser.internal.Utils.context
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun EnterpriseAppsPane(
    modifier: Modifier = Modifier,
    viewModel: EnterpriseAppsViewModel = hiltViewModel(),
    upPress: () -> Unit
) {
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

    EnterpriseAppsContent(
        uiState = uiState,
        modifier = modifier,
        onDownload = {
            viewModel.startDownload(it)
        }
    )
}

@Composable
fun EnterpriseAppsContent(
    uiState: EnterpriseAppsUiState, modifier: Modifier,
    onDownload: (app: AppDomain) -> Unit
) {
    val context = LocalContext.current
    Scaffold { padding ->
        Column(modifier = Modifier.Companion.padding(padding)) {
            Text(
                modifier = Modifier.Companion.padding(horizontal = 16.dp),
                text = uiState.enterpriseName.ifBlank { stringResource(R.string.enterprise_apps) },
                style = MaterialTheme.typography.headlineMedium
            )
            EnterpriseUiStateContainer(
                hasAccess = uiState.hasAccess,
                isLoading = uiState.isLoading,
                error = uiState.error,
                data = uiState.apps,
                isEmpty = uiState.apps.isEmpty(),
                modifier = modifier.fillMaxSize()
            ) { apps ->
                LazyColumn(
                    modifier = Modifier.Companion.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(apps) { downloadableApp ->
                        SimpleAppItem(
                            modifier = Modifier.zIndex(3f),
                            app = downloadableApp.app.toSimpleApp(downloadableApp.downloadState),
                            onDownload = {
                                onDownload(downloadableApp.app)
                            },
                            onOpen = {
                                context.openApp(downloadableApp.app.packageName)
                            },
                            onInstall = { uri ->
                                context.installApp(uri)
                            },
                            onUninstall = {
                                context.uninstallApp(downloadableApp.app.packageName)
                            }
                        )
                    }
                }
            }
        }
    }
}