package com.sleke.library.enterprise.presentation

import com.sleke.library.catalog.domain.CatalogRepository
import com.sleke.library.catalog.domain.CatalogScope
import com.sleke.library.catalog.presentation.CatalogViewModel
import com.sleke.library.downloads.domain.CustomDownloadCoordinator
import com.sleke.library.downloads.domain.InstalledPackageSource
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EnterpriseAppsViewModel @Inject constructor(
    repository: CatalogRepository,
    downloads: CustomDownloadCoordinator,
    packages: InstalledPackageSource,
    sessions: EnterpriseSessionRepository
) : CatalogViewModel(CatalogScope.Enterprise, repository, downloads, packages, sessions)
