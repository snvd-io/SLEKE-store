/*
 * SPDX-FileCopyrightText: 2021 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.viewmodel.all

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.aurora.extensions.isValidApp
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.helpers.web.WebAppDetailsHelper
import com.aurora.store.compose.ui.commons.InstalledAppMeta
import com.aurora.store.compose.ui.commons.SortFilterPrefKeys
import com.aurora.store.compose.ui.commons.SortFilterState
import com.aurora.store.compose.ui.commons.applyFilter
import com.aurora.store.compose.ui.commons.applySort
import com.aurora.store.compose.ui.commons.loadSortFilterState
import com.aurora.store.compose.ui.commons.save
import com.aurora.store.data.PageResult
import com.aurora.store.data.paging.GenericPagingSource
import com.aurora.store.data.paging.GenericPagingSource.Companion.manualPager
import com.aurora.store.data.providers.BlacklistProvider
import com.aurora.store.util.PackageUtil
import com.aurora.store.util.Preferences
import com.sleke.library.catalog.domain.CatalogRepository
import com.sleke.library.catalog.domain.CatalogScope
import com.sleke.library.eligibility.domain.CatalogEligibilityPolicy
import com.sleke.library.eligibility.domain.EligibilityResult
import com.sleke.library.eligibility.domain.PackageFacts
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class InstalledViewModel @Inject constructor(
    blacklistProvider: BlacklistProvider,
    @ApplicationContext private val context: Context,
    private val webAppDetailsHelper: WebAppDetailsHelper,
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    private val blacklist = blacklistProvider.blacklist

    private val _apps = MutableStateFlow<PagingData<App>>(PagingData.empty())
    val apps = _apps.asStateFlow()

    private val _state = MutableStateFlow(loadSortFilterState(context, PREF_KEYS))
    val state = _state.asStateFlow()

    private val _installers = MutableStateFlow<Map<String, String>>(emptyMap())
    val installers = _installers.asStateFlow()

    private val _metadata = MutableStateFlow<Map<String, InstalledAppMeta>>(emptyMap())
    val metadata = _metadata.asStateFlow()

    private val refreshVersion = MutableStateFlow(0)
    private val _catalogUnavailable = MutableStateFlow(false)
    val catalogUnavailable = _catalogUnavailable.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private suspend fun loadPackages(): List<EnrichedPackage>? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installed = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            .filter { it.isValidApp(pm) }
        val eligibility = CatalogEligibilityPolicy.installed(
            installed.map {
                PackageFacts(it.packageName, blacklisted = it.packageName in blacklist)
            },
            catalogRepository.load(CatalogScope.Regular)
        )
        if (eligibility !is EligibilityResult.Allowed) return@withContext null
        val packages = installed.filter { it.packageName in eligibility.packages }.map { info ->
            val appInfo = info.applicationInfo!!
            EnrichedPackage(
                info = info,
                label = appInfo.loadLabel(pm).toString(),
                installer = PackageUtil.getInstallerPackageName(context, info.packageName),
                sizeBytes = runCatching { File(appInfo.sourceDir).length() }.getOrDefault(0L),
                isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            )
        }
        _installers.value = packages.mapNotNull { it.installer }.toSet()
            .associateWith { resolveInstallerLabel(it) }
        _metadata.value = packages.associateBy { it.packageName }
        packages
    }

    init {
        viewModelScope.launch {
            combine(_state, refreshVersion) { state, _ -> state }.collectLatest { state ->
                _loading.value = true
                _catalogUnavailable.value = false
                val all = try {
                    loadPackages()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    null
                }
                _loading.value = false
                if (all == null) {
                    _catalogUnavailable.value = true
                    _apps.value = PagingData.empty()
                    return@collectLatest
                }
                val filteredSorted = all.applyFilter(state).applySort(state)
                val chunks = filteredSorted.chunked(GenericPagingSource.DEFAULT_PAGE_SIZE)
                manualPager(pageSize = GenericPagingSource.DEFAULT_PAGE_SIZE) { page ->
                    val chunk = chunks.getOrNull(page - 1)
                        ?: return@manualPager PageResult(emptyList<App>(), hasMore = false)
                    val items = webAppDetailsHelper.getAppDetails(chunk.map { it.packageName })
                    PageResult(items, hasMore = page < chunks.size)
                }.flow
                    .distinctUntilChanged()
                    .cachedIn(viewModelScope)
                    .collect { _apps.value = it }
            }
        }
    }

    fun retryCatalog() {
        refreshVersion.value += 1
    }

    fun updateState(newState: SortFilterState) {
        if (newState == _state.value) return
        _state.value = newState
        newState.save(context, PREF_KEYS)
    }

    private fun resolveInstallerLabel(packageName: String): String = try {
        val info = PackageUtil.getPackageInfo(context, packageName)
        info.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: packageName
    } catch (_: Exception) {
        packageName
    }

    private data class EnrichedPackage(
        val info: PackageInfo,
        override val label: String,
        override val installer: String?,
        override val sizeBytes: Long,
        override val isSystem: Boolean
    ) : InstalledAppMeta {
        override val packageName: String get() = info.packageName
        override val firstInstallTime: Long get() = info.firstInstallTime
        override val lastUpdateTime: Long get() = info.lastUpdateTime
    }

    companion object {
        private val PREF_KEYS = SortFilterPrefKeys(
            sortBy = Preferences.PREFERENCE_INSTALLED_SORT_BY,
            sortOrder = Preferences.PREFERENCE_INSTALLED_SORT_ORDER,
            appTypes = Preferences.PREFERENCE_INSTALLED_APP_TYPES,
            installer = Preferences.PREFERENCE_INSTALLED_INSTALLER
        )
    }
}
