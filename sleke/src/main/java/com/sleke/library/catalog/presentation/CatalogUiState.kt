package com.sleke.library.catalog.presentation

import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.downloads.domain.DownloadHandle
import com.sleke.library.downloads.domain.DownloadState

enum class CatalogStatus { Loading, Populated, Empty, Error }

data class CatalogUiState(
    val status: CatalogStatus = CatalogStatus.Loading,
    val apps: List<CatalogApp> = emptyList(),
    val query: String = "",
    val error: String? = null,
    val downloads: Map<String, DownloadState> = emptyMap(),
    val handles: Map<String, DownloadHandle> = emptyMap(),
    val installedPackages: Set<String> = emptySet()
)

data class SlekeNavigationState(val enterpriseVisible: Boolean = false) {
    fun interceptsBack(isActive: Boolean) = enterpriseVisible && isActive
    fun enterEnterprise(allowed: Boolean) = copy(enterpriseVisible = allowed)
    fun back() = copy(enterpriseVisible = false)
    fun reconcileAccess(allowed: Boolean) = if (allowed) this else back()
}
