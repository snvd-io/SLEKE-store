package com.sleke.library.catalog.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.sleke.library.R
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import com.sleke.library.enterprise.presentation.EnterpriseAppsRoute

@Composable
fun HomeRoute(onOpenPackage: (String) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    if (session is EnterpriseSessionState.Allowed) {
        EnterpriseAppsRoute(onOpenPackage = onOpenPackage)
    } else {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val apps = viewModel.pagedApps.collectAsLazyPagingItems()
        Column {
            if (session is EnterpriseSessionState.Unavailable) {
                TextButton(onClick = viewModel::refreshMembership) {
                    Text(stringResource(R.string.catalog_membership_retry))
                }
            }
            PagedCatalogContent(state, apps, viewModel::search, onOpenPackage, viewModel::retry)
        }
    }
}
