package com.sleke.library.catalog.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.domain.CatalogIdentity
import com.sleke.library.catalog.domain.CatalogMetadata
import com.sleke.library.catalog.domain.CatalogMetadataSource
import com.sleke.library.catalog.domain.MetadataContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPagingSourceTest {
    @Test fun differentLoadSizesKeepSourceOrder() = runTest {
        val metadata = object : CatalogMetadataSource {
            override val context = MutableStateFlow(MetadataContext("a"))
            override suspend fun load(
                packageName: String,
                context: MetadataContext
            ): CatalogMetadata? = null
        }
        val source =
            CatalogPagingSource(
                (0..19).map {
                    CatalogApp(CatalogIdentity("apks", "$it"), "a.$it", "url", title = "Name $it")
                },
                "",
                CatalogEnricher(metadata, backgroundScope),
                metadata.context.value
            )
        val first = source.load(
            PagingSource.LoadParams.Refresh(null, 15, false)
        ) as PagingSource.LoadResult.Page
        val next = source.load(
            PagingSource.LoadParams.Append(first.nextKey!!, 5, false)
        ) as PagingSource.LoadResult.Page
        assertEquals(
            (0..19).map(Int::toString),
            (first.data + next.data).map {
                it.identity.documentId
            }
        )
        assertNull(next.nextKey)
        val beyond = source.load(
            PagingSource.LoadParams.Append(100, 5, false)
        ) as PagingSource.LoadResult.Page
        assertTrue(beyond.data.isEmpty())
    }

    @Test fun refreshAnchorUsesActualOffsetWithVariablePageSizes() = runTest {
        val metadata = object : CatalogMetadataSource {
            override val context = MutableStateFlow(MetadataContext("a"))
            override suspend fun load(
                packageName: String,
                context: MetadataContext
            ): CatalogMetadata? = null
        }
        val source =
            CatalogPagingSource(
                (0..16).map {
                    CatalogApp(CatalogIdentity("apks", "$it"), "a.$it", "url")
                },
                "",
                CatalogEnricher(metadata, backgroundScope),
                metadata.context.value
            )
        val first = source.load(
            PagingSource.LoadParams.Refresh(null, 15, false)
        ) as PagingSource.LoadResult.Page
        val last = source.load(
            PagingSource.LoadParams.Append(first.nextKey!!, 2, false)
        ) as PagingSource.LoadResult.Page
        val state =
            PagingState(listOf(first, last), 16, androidx.paging.PagingConfig(pageSize = 5), 0)
        assertEquals(15, source.getRefreshKey(state))
    }
}
