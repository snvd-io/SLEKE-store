package com.sleke.presentation.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aurora.store.compose.theme.AuroraTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlin.apply


@AndroidEntryPoint
class SlekeAppsFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            AuroraTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = SlekeNavigation.SlekeApps
                ) {
                    composable<SlekeNavigation.SlekeApps> {
                        SlekeAppsScreen(
                            onNavigateToEnterprise = {
                                navController.navigate(SlekeNavigation.SlekeEnterprise)
                            }
                        )
                    }

                    composable<SlekeNavigation.SlekeEnterprise> {
                        EnterpriseAppsPane(
                            upPress = {
                            navController.popBackStack<SlekeNavigation.SlekeApps>(
                                inclusive = false
                            )
                        })
                    }
                }

            }
        }
    }
}