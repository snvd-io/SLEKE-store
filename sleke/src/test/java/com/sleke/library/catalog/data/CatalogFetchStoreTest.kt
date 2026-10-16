package com.sleke.library.catalog.data

import com.sleke.library.catalog.domain.CatalogResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogFetchStoreTest {
    @Test fun failedFetchCanRetryAndSuccessfulEmptyIsCached() = runTest {
        val store = CatalogFetchStore(backgroundScope)
        var calls = 0
        suspend fun fetch(): CatalogResult {
            calls++
            return if (calls ==
                1
            ) {
                CatalogResult.Failure(Exception())
            } else {
                CatalogResult.Success(emptyList())
            }
        }
        assertTrue(store.load("a", false, ::fetch) is CatalogResult.Failure)
        assertEquals(CatalogResult.Success(emptyList()), store.load("a", false, ::fetch))
        store.load("a", false, ::fetch)
        assertEquals(2, calls)
    }

    @Test fun concurrentConsumersShareFetchAndInvalidationRejectsOldResult() = runTest {
        val store = CatalogFetchStore(backgroundScope)
        var calls = 0
        val release = CompletableDeferred<Unit>()
        val first =
            async {
                runCatching {
                    store.load("a", false) {
                        calls++
                        release.await()
                        CatalogResult.Success(emptyList())
                    }
                }
            }
        val next = async { runCatching { store.load("a", false) { error("duplicate") } } }
        runCurrent()
        assertEquals(1, calls)
        store.invalidate()
        release.complete(Unit)
        assertTrue(first.await().exceptionOrNull() is CancellationException)
        assertTrue(next.await().exceptionOrNull() is CancellationException)
    }
}
