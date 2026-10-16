package com.sleke.library.catalog.domain

interface CatalogRepository {
    suspend fun load(scope: CatalogScope, refresh: Boolean = false): CatalogResult
}
