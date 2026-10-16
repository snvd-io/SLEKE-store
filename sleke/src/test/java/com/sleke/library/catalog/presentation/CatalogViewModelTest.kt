package com.sleke.library.catalog.presentation

import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.domain.CatalogIdentity
import com.sleke.library.catalog.domain.CatalogRepository
import com.sleke.library.catalog.domain.CatalogResult
import com.sleke.library.catalog.domain.CatalogScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
    @Test fun searchDebouncesAndRetryReplacesError() = runTest {
        var calls = 0
        val repository = object : CatalogRepository {
            override suspend fun load(scope: CatalogScope, refresh: Boolean): CatalogResult {
                calls++
                return if (calls ==
                    1
                ) {
                    CatalogResult.Failure(Exception("offline"))
                } else {
                    CatalogResult.Success(
                        listOf(
                            CatalogApp(
                                CatalogIdentity("apks", "1"),
                                "a.b",
                                "url",
                                title = "Example"
                            )
                        )
                    )
                }
            }
        }
        val presenter = CatalogPresenter(CatalogScope.Regular, repository, backgroundScope)
        presenter.search("exa")
        runCurrent()
        advanceTimeBy(299)
        runCurrent()
        assertEquals(0, calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(CatalogStatus.Error, presenter.state.value.status)
        presenter.retry()
        runCurrent()
        assertEquals(CatalogStatus.Populated, presenter.state.value.status)
        assertEquals("a.b", presenter.state.value.apps.single().packageName)
    }

    @Test fun staleQueryCannotOverwriteNewSearch() = runTest {
        val old = CompletableDeferred<CatalogResult>()
        var calls = 0
        val repository = object : CatalogRepository {
            override suspend fun load(scope: CatalogScope, refresh: Boolean): CatalogResult {
                calls++
                return if (calls ==
                    1
                ) {
                    old.await()
                } else {
                    CatalogResult.Success(
                        listOf(
                            CatalogApp(
                                CatalogIdentity("apks", "2"),
                                "new.app",
                                "url",
                                title = "New"
                            )
                        )
                    )
                }
            }
        }
        val presenter = CatalogPresenter(CatalogScope.Regular, repository, backgroundScope)
        presenter.search("old")
        advanceTimeBy(300)
        runCurrent()
        presenter.search("new")
        advanceTimeBy(300)
        runCurrent()
        old.complete(CatalogResult.Success(emptyList()))
        runCurrent()
        assertEquals("new.app", presenter.state.value.apps.single().packageName)
    }
}
