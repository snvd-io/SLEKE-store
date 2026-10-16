package com.sleke.library.catalog.presentation

import com.sleke.library.catalog.domain.CatalogRepository
import com.sleke.library.catalog.domain.CatalogScope
import com.sleke.library.downloads.domain.CustomDownloadCoordinator
import com.sleke.library.downloads.domain.InstalledPackageSource
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SlekeAppsViewModel @Inject constructor(
    repository: CatalogRepository,
    downloads: CustomDownloadCoordinator,
    packages: InstalledPackageSource,
    sessions: EnterpriseSessionRepository
) : CatalogViewModel(CatalogScope.Sleke, repository, downloads, packages, sessions)
