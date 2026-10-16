package com.sleke.library.catalog.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sleke.library.R
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import com.sleke.library.enterprise.presentation.EnterpriseAppsRoute
import com.sleke.library.util.openApp
import com.sleke.library.util.uninstallApp

@Composable
fun SlekeAppsRoute(
    onOpenPackage: (String) -> Unit,
    onEnterpriseSignOut: () -> Unit = {},
    viewModel: SlekeAppsViewModel = hiltViewModel(),
    isActive: Boolean = true
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val allowed = session is EnterpriseSessionState.Allowed
    var enterpriseVisible by rememberSaveable { mutableStateOf(false) }
    val navigation = SlekeNavigationState(enterpriseVisible)
    LaunchedEffect(allowed) {
        enterpriseVisible =
            navigation.reconcileAccess(allowed).enterpriseVisible
    }
    BackHandler(navigation.interceptsBack(isActive)) {
        enterpriseVisible =
            navigation.back().enterpriseVisible
    }
    if (enterpriseVisible && allowed) {
        EnterpriseAppsRoute(onOpenPackage, { enterpriseVisible = false }, {
            viewModel.enterpriseSignOut()
            onEnterpriseSignOut()
        })
    } else {
        val context = LocalContext.current
        CatalogPackageObserver(viewModel::refreshInstalled)
        Column {
            if (allowed) {
                TextButton(onClick = {
                    enterpriseVisible =
                        navigation.enterEnterprise(allowed).enterpriseVisible
                }) { Text(stringResource(R.string.catalog_enterprise)) }
            }
            if (session is EnterpriseSessionState.Unavailable) {
                TextButton(onClick = viewModel::refreshMembership) {
                    Text(stringResource(R.string.catalog_membership_retry))
                }
            }
            SlekeAppsContent(
                state,
                viewModel::search,
                onOpenPackage,
                viewModel::startDownload,
                viewModel::retry,
                onRetryInstall = viewModel::retryInstall,
                onLaunchPackage = context::openApp,
                onUninstallPackage = context::uninstallApp
            )
        }
    }
}
