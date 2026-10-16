package com.sleke.integration.eligibility

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.gplayapi.helpers.web.WebAppDetailsHelper
import com.aurora.store.data.providers.BlacklistProvider
import com.aurora.store.viewmodel.all.InstalledViewModel
import com.sleke.library.catalog.domain.CatalogRepository
import com.sleke.library.catalog.domain.CatalogResult
import com.sleke.library.catalog.domain.CatalogScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No Play/Firebase requests: a successful empty source never asks for display metadata. */
@RunWith(AndroidJUnit4::class)
class CatalogEligibilityIntegrationTest {
    @Test
    fun installedFailureIsExplicitAndRetryFetchesFreshCatalog() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var calls = 0
        val source = object : CatalogRepository {
            override suspend fun load(scope: CatalogScope, refresh: Boolean): CatalogResult {
                assertEquals(CatalogScope.Regular, scope)
                calls++
                return if (calls == 1) {
                    CatalogResult.Failure(IllegalStateException("offline"))
                } else {
                    CatalogResult.Success(emptyList())
                }
            }
        }
        val store = androidx.lifecycle.ViewModelStore()
        val viewModel = withContext(Dispatchers.Main) {
            InstalledViewModel(
                BlacklistProvider(Json, context),
                context,
                WebAppDetailsHelper(),
                source
            )
                .also { store.put("installed", it) }
        }
        try {
            withTimeout(10_000L) { viewModel.catalogUnavailable.first { it } }
            assertEquals(1, calls)
            withContext(Dispatchers.Main) { viewModel.retryCatalog() }
            withTimeout(10_000L) { viewModel.catalogUnavailable.first { !it } }
            withTimeout(10_000L) { viewModel.loading.first { !it } }
            assertEquals(2, calls)
            assertTrue(viewModel.metadata.value.isEmpty())
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }
}
