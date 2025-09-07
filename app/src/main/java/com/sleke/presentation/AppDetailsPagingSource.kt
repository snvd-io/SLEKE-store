package com.sleke.presentation

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.sleke.library.model.firebase.AppDto
import com.aurora.store.data.repository.GPlayRepository
import timber.log.Timber

class AppDetailsPagingSource(
    private val allAppDtos: List<AppDto>,
    private val gplayRepository: GPlayRepository,
    private val query: String
) : PagingSource<Int, AppDto>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AppDto> {
        return try {

            val filteredApks = if (query.isBlank()) {
                allAppDtos
            } else {
                allAppDtos.filter { apk ->
                    apk.name.contains(query, ignoreCase = true) ||
                            apk.packageName.contains(query, ignoreCase = true)
                }
            }

            val page = params.key ?: 0
            val pageSize = params.loadSize
            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, filteredApks.size)

            if (startIndex >= filteredApks.size) {
                return LoadResult.Page(
                    data = emptyList(),
                    prevKey = if (page > 0) page - 1 else null,
                    nextKey = null
                )
            }

            val pageItems = filteredApks.subList(startIndex, endIndex)
            val enrichedItems = pageItems.mapNotNull { apk ->
                try {
                    gplayRepository.getAppDetails(apk.packageName)
                } catch (e: Exception) {
                    Timber.tag("AppPaging").w(e, "Error fetching details for ${apk.packageName}")
                    apk
                }
            }

            LoadResult.Page(
                data = enrichedItems,
                prevKey = if (page > 0) page - 1 else null,
                nextKey = if (endIndex < filteredApks.size) page + 1 else null
            )
        } catch (e: Exception) {
            Timber.tag("PagingSource").e(e, "Error loading apk page: ${e.message}")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, AppDto>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}