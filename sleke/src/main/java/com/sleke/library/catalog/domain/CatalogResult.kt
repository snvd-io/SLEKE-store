package com.sleke.library.catalog.domain

sealed interface CatalogResult {
    data class Success(val apps: List<CatalogApp>) : CatalogResult
    data class Failure(val cause: Throwable) : CatalogResult
}
