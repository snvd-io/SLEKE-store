package com.sleke.library.catalog.data

import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.domain.CatalogIdentity
import com.sleke.library.catalog.domain.CatalogMetadata
import com.sleke.library.catalog.domain.CatalogMetadataSource
import com.sleke.library.catalog.domain.MetadataContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogEnricherTest {
    private val context = MetadataContext("a")
    private fun row(id: Int = 0) =
        CatalogApp(CatalogIdentity("apks", "$id"), "a.$id", "https://source/$id")
    private class Source(
        val action: suspend (String, MetadataContext) -> CatalogMetadata?
    ) : CatalogMetadataSource {
        override val context = MutableStateFlow(MetadataContext("a"))
        override suspend fun load(packageName: String, context: MetadataContext) =
            action(packageName, context)
    }

    @Test fun missingMetadataAndOrdinaryFailureKeepRows() = runTest {
        val source = Source { _, _ -> error("offline") }
        val rows = listOf(row())
        assertEquals(rows, CatalogEnricher(source, backgroundScope).enrich(rows, context))
        assertEquals(
            rows,
            CatalogEnricher(
                Source { _, _ ->
                    null
                },
                backgroundScope
            ).enrich(rows, context)
        )
    }

    @Test fun cancellationPropagates() = runTest {
        val enricher =
            CatalogEnricher(Source { _, _ -> throw CancellationException() }, backgroundScope)
        val result = async { runCatching { enricher.enrich(listOf(row()), context) } }
        runCurrent()
        assertTrue(result.await().exceptionOrNull() is CancellationException)
    }

    @Test fun fourCallsMaximumAndDuplicatesShareWork() = runTest {
        var active = 0
        var peak = 0
        var calls = 0
        val release = CompletableDeferred<Unit>()
        val source =
            Source { _, _ ->
                calls++
                active++
                peak = maxOf(peak, active)
                release.await()
                active--
                CatalogMetadata(title = "Play")
            }
        val enricher = CatalogEnricher(source, backgroundScope)
        val one = async { enricher.enrich((0..7).map(::row), context) }
        val duplicate = async { enricher.enrich(listOf(row()), context) }
        runCurrent()
        assertEquals(4, peak)
        release.complete(Unit)
        assertEquals(8, one.await().size)
        assertEquals("Play", duplicate.await().single().title)
        assertEquals(8, calls)
    }

    @Test fun cancellingCallerDoesNotCancelSharedWork() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val enricher =
            CatalogEnricher(
                Source { _, _ ->
                    calls++
                    release.await()
                    CatalogMetadata(title = "shared")
                },
                backgroundScope
            )
        val first = async { enricher.enrich(listOf(row()), context) }
        val second = async { enricher.enrich(listOf(row()), context) }
        runCurrent()
        first.cancel()
        release.complete(Unit)
        assertEquals("shared", second.await().single().title)
        assertEquals(1, calls)
    }

    @Test fun contextChangeDiscardsSharedCompletion() = runTest {
        val release = CompletableDeferred<Unit>()
        val source = Source { _, c ->
            if (c.key ==
                "a"
            ) {
                release.await()
            }
            CatalogMetadata(title = c.key)
        }
        val enricher = CatalogEnricher(source, backgroundScope)
        val old = async { runCatching { enricher.enrich(listOf(row()), context) } }
        runCurrent()
        source.context.value = MetadataContext("b")
        runCurrent()
        assertEquals("b", enricher.enrich(listOf(row()), MetadataContext("b")).single().title)
        release.complete(Unit)
        assertTrue(old.await().exceptionOrNull() is CancellationException)
    }
}
