package com.sleke.library.catalog.domain

import kotlinx.coroutines.flow.StateFlow

data class MetadataContext(val key: String)

data class CatalogMetadata(
    val title: String? = null,
    val description: String? = null,
    val publisher: String? = null,
    val versionLabel: String? = null,
    val iconUrl: String? = null,
    val rating: Float? = null,
    val installs: String? = null
)

interface CatalogMetadataSource {
    val context: StateFlow<MetadataContext>
    suspend fun load(packageName: String, context: MetadataContext): CatalogMetadata?
}
