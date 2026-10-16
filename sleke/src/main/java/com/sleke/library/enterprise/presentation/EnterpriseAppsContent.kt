package com.sleke.library.enterprise.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sleke.library.R
import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.presentation.CatalogMessage
import com.sleke.library.catalog.presentation.CatalogPackageObserver
import com.sleke.library.catalog.presentation.CatalogStatus
import com.sleke.library.catalog.presentation.CatalogUiState
import com.sleke.library.catalog.presentation.SlekeAppsContent
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import com.sleke.library.util.openApp
import com.sleke.library.util.uninstallApp

@Composable
fun EnterpriseAppsRoute(
    onOpenPackage: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onEnterpriseSignOut: (() -> Unit)? = null,
    viewModel: EnterpriseAppsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val allowed = session as? EnterpriseSessionState.Allowed
    val context = LocalContext.current
    CatalogPackageObserver(viewModel::refreshInstalled)
    if (allowed != null) {
        EnterpriseAppsContent(
            state, allowed.enterpriseName, viewModel::search, onOpenPackage,
            viewModel::startDownload, viewModel::retryInstall, viewModel::retry,
            context::openApp, context::uninstallApp, onBack, onEnterpriseSignOut
        )
    } else {
        CatalogMessage(
            stringResource(R.string.catalog_awaiting_access),
            viewModel::refreshMembership
        )
    }
}

@Composable
fun EnterpriseAppsContent(
    state: CatalogUiState,
    enterpriseName: String,
    onSearch: (String) -> Unit,
    onOpenPackage: (String) -> Unit,
    onDownload: (CatalogApp) -> Unit,
    onRetryInstall: (CatalogApp) -> Unit,
    onRetry: () -> Unit,
    onLaunchPackage: (String) -> Unit,
    onUninstallPackage: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onEnterpriseSignOut: (() -> Unit)? = null
) {
    Column {
        if (onBack !=
            null
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.catalog_back)) }
        }
        Text(
            enterpriseName.ifBlank {
                stringResource(R.string.catalog_enterprise)
            },
            style = MaterialTheme.typography.headlineMedium
        )
        if (onEnterpriseSignOut !=
            null
        ) {
            TextButton(onClick = onEnterpriseSignOut) {
                Text(stringResource(R.string.catalog_sign_out))
            }
        }
        SlekeAppsContent(
            state,
            onSearch,
            onOpenPackage,
            onDownload,
            onRetry,
            onRetryInstall = onRetryInstall,
            onLaunchPackage = onLaunchPackage,
            onUninstallPackage = onUninstallPackage
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EnterpriseAppsPreview() {
    MaterialTheme {
        EnterpriseAppsContent(CatalogUiState(CatalogStatus.Empty), "Example workplace", {
        }, {}, {}, {}, {}, {}, {})
    }
}
