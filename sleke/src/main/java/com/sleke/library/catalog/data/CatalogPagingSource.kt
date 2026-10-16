package com.sleke.library.catalog.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.domain.MetadataContext
import kotlinx.coroutines.CancellationException

class CatalogPagingSource(
    apps: List<CatalogApp>,
    query: String,
    private val enricher: CatalogEnricher,
    private val context: MetadataContext
) : PagingSource<Int, CatalogApp>() {
    private val filtered = apps.filter {
        query.isBlank() ||
            it.title.contains(query.trim(), true) ||
            it.packageName.contains(query.trim(), true)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CatalogApp> = try {
        val offset = (params.key ?: 0).coerceIn(0, filtered.size)
        val end = (offset.toLong() + params.loadSize).coerceAtMost(filtered.size.toLong()).toInt()
        LoadResult.Page(
            data = enricher.enrich(filtered.subList(offset, end), context),
            prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
            nextKey = end.takeIf { it < filtered.size }
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        LoadResult.Error(failure)
    }

    override fun getRefreshKey(state: PagingState<Int, CatalogApp>): Int? =
        state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor) ?: return@let null
            val first = page.data.firstOrNull() ?: return@let null
            filtered.indexOfFirst { it.identity == first.identity }.takeIf { it >= 0 }
        }
}
