package com.sleke.library.downloads.domain

import com.sleke.library.catalog.domain.CatalogScope
import kotlinx.coroutines.flow.Flow

@JvmInline
value class DownloadHandle(val value: String)

data class CustomDownloadRequest(
    val packageName: String,
    val downloadUrl: String,
    val scope: CatalogScope = CatalogScope.Sleke
)

sealed interface DownloadOwner {
    data object Public : DownloadOwner
    data class Enterprise(val uid: String, val enterpriseId: String) : DownloadOwner
}

interface CustomDownloadCoordinator {
    suspend fun findActive(
        packageName: String,
        scope: CatalogScope = CatalogScope.Sleke
    ): DownloadHandle? = null
    suspend fun start(request: CustomDownloadRequest): DownloadHandle
    fun observe(handle: DownloadHandle): Flow<DownloadState>
    suspend fun retryInstall(handle: DownloadHandle): DownloadHandle
    suspend fun cancel(handle: DownloadHandle)
    suspend fun cancelEnterpriseWork()
}

interface InstalledPackageSource {
    suspend fun versionCode(packageName: String): Long?
}
