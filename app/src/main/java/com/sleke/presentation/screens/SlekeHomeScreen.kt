package com.sleke.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sleke.library.model.firebase.AppDto
import com.sleke.presentation.HomeViewModel

/**
 * Replaces Aurora's default "Apps" tab with SLEKE's own app catalog: an enterprise pane for
 * enterprise-signed-in accounts, or the regular paged catalog otherwise.
 */
@Composable
fun SlekeHomeScreen(
    onAppClick: (AppDto) -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val isEnterprise by homeViewModel.isEnterprise.collectAsStateWithLifecycle()
    if (isEnterprise) {
        EnterpriseAppsPane(upPress = { })
    } else {
        PagedAppsScreen(
            viewModel = homeViewModel,
            onAppClick = onAppClick
        )
    }
}
