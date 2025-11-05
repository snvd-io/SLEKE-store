/*
 * Aurora Store
 *  Copyright (C) 2021, Rahul Kumar Patel <whyorean@gmail.com>
 *
 *  Aurora Store is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  Aurora Store is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.aurora.store.view.ui.apps

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.aurora.extensions.navigate
import com.aurora.store.MobileNavigationDirections
import com.aurora.store.R
import com.aurora.store.compose.navigation.Screen
import com.aurora.store.compose.theme.AuroraTheme
import com.aurora.store.databinding.FragmentAppsGamesBinding
import com.aurora.store.util.Preferences
import com.aurora.store.view.ui.commons.BaseFragment
import com.aurora.store.view.ui.commons.CategoryFragment
import com.aurora.store.view.ui.commons.ForYouFragment
import com.aurora.store.view.ui.commons.TopChartContainerFragment
import com.aurora.store.viewmodel.apps.AppsContainerViewModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.sleke.library.model.firebase.AppDto
import com.sleke.presentation.HomeViewModel
import com.sleke.presentation.screens.EnterpriseAppsPane
import com.sleke.presentation.screens.PagedAppsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppsContainerFragment : BaseFragment<FragmentAppsGamesBinding>() {

    private val viewModel: AppsContainerViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Adjust FAB margins for edgeToEdge display
        binding.searchFab.visibility = View.GONE
        ViewCompat.setOnApplyWindowInsetsListener(binding.searchFab) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.searchFab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom + resources.getDimensionPixelSize(R.dimen.margin_large)
            }
            WindowInsetsCompat.CONSUMED
        }

        // Toolbar
        binding.toolbar.apply {
            title = getString(R.string.title_apps)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_download_manager -> {
                        requireContext().navigate(Screen.Downloads)
                    }

                    R.id.menu_more -> {
                        findNavController().navigate(
                            MobileNavigationDirections.actionGlobalMoreDialogFragment()
                        )
                    }
                }
                true
            }
        }

        setupComposeUI()

        binding.searchFab.setOnClickListener {
            requireContext().navigate(Screen.Search)
        }
    }

    private fun setupComposeUI() {
        binding.pager.visibility = View.GONE
        binding.tabLayout.visibility = View.GONE

        binding.appsListCompose.apply {
            visibility = View.VISIBLE

            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                AuroraTheme {
                    val isEnterprise by homeViewModel.isEnterprise.collectAsStateWithLifecycle()
                    if (isEnterprise) {
                        EnterpriseAppsPane(
                            upPress = { }
                        )
                    } else {
                        PagedAppsScreen(
                            viewModel = homeViewModel,
                            onAppClick = { app -> navigateToAppDetails(app) }
                        )
                    }
                }
            }
        }
    }

    private fun navigateToAppDetails(app: AppDto) {
        openDetailsFragment(app.packageName)
    }

    override fun onDestroyView() {
        binding.pager.adapter = null
        super.onDestroyView()
    }
}
