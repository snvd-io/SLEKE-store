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
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.sleke.presentation.HomeViewModel
import com.aurora.store.MobileNavigationDirections
import com.aurora.store.R
import com.sleke.presentation.screens.PagedAppsScreen
import com.aurora.store.compose.theme.AuroraTheme
import com.aurora.store.databinding.FragmentAppsGamesBinding
import com.aurora.store.view.ui.commons.BaseFragment
import com.aurora.store.view.ui.commons.CategoryFragment
import com.aurora.store.view.ui.commons.ForYouFragment
import com.aurora.store.view.ui.commons.TopChartContainerFragment
import com.aurora.store.viewmodel.apps.AppsContainerViewModel
import com.sleke.library.model.firebase.AppDto
import com.sleke.presentation.screens.EnterpriseAppsPane
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppsContainerFragment : BaseFragment<FragmentAppsGamesBinding>() {

    private val containerViewModel: AppsContainerViewModel by viewModels()
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

        binding.toolbar.apply {
            title = getString(R.string.title_apps)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_download_manager -> {
                        findNavController().navigate(R.id.downloadFragment)
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
            findNavController().navigate(R.id.searchSuggestionFragment)
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
        val bundle = Bundle().apply {
            putString("packageName", app.packageName)
        }
        findNavController().navigate(R.id.appDetailsFragment, bundle)
    }

    internal class ViewPagerAdapter(
        fragment: FragmentManager,
        lifecycle: Lifecycle,
        private val isGoogleAccount: Boolean,
        private val isForYouEnabled: Boolean
    ) :
        FragmentStateAdapter(fragment, lifecycle) {

        private val tabFragments: MutableList<Fragment> = mutableListOf<Fragment>().apply {
            if (isForYouEnabled) {
                add(ForYouFragment.newInstance(0))
            }
            add(TopChartContainerFragment.newInstance(0))
            add(CategoryFragment.newInstance(0))
        }

        override fun createFragment(position: Int): Fragment {
            return tabFragments[position]
        }

        override fun getItemCount(): Int {
            return tabFragments.size
        }
    }
}
