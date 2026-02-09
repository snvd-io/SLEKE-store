package com.sleke.presentation.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.aurora.store.compose.theme.AuroraTheme
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class SlekeAppsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            AuroraTheme {
                val backStack = rememberNavBackStack(SlekeNavigation.SlekeApps)

                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator()
                    ),
                    entryProvider = entryProvider {
                        entry<SlekeNavigation.SlekeApps> {
                            SlekeAppsScreen(
                                onNavigateToEnterprise = {
                                    backStack.add(SlekeNavigation.SlekeEnterprise)
                                }
                            )
                        }

                        entry<SlekeNavigation.SlekeEnterprise> {
                            EnterpriseAppsPane(
                                upPress = {
                                    backStack.removeLastOrNull()
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}
