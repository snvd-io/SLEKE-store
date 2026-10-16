package com.sleke.library.catalog.presentation

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.sleke.library.catalog.data.CatalogEnricher
import com.sleke.library.catalog.data.CatalogPagingSource
import com.sleke.library.catalog.domain.CatalogMetadataSource
import com.sleke.library.catalog.domain.CatalogRepository
import com.sleke.library.catalog.domain.CatalogScope
import com.sleke.library.downloads.domain.CustomDownloadCoordinator
import com.sleke.library.downloads.domain.InstalledPackageSource
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: CatalogRepository,
    metadata: CatalogMetadataSource,
    downloads: CustomDownloadCoordinator,
    packages: InstalledPackageSource,
    sessions: EnterpriseSessionRepository
) : CatalogViewModel(CatalogScope.Regular, repository, downloads, packages, sessions) {
    private val enricher = CatalogEnricher(metadata, viewModelScope)
    val pagedApps = combine(state, metadata.context) { state, context ->
        Triple(state.apps, state.query, context)
    }
        .distinctUntilChanged()
        .flatMapLatest { (apps, query, context) ->
            Pager(PagingConfig(pageSize = 5, enablePlaceholders = false, prefetchDistance = 5)) {
                CatalogPagingSource(apps, query, enricher, context)
            }.flow
        }.cachedIn(viewModelScope)
}
