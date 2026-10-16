package com.sleke.integration.catalog

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.domain.CatalogIdentity
import com.sleke.library.catalog.presentation.CatalogStatus
import com.sleke.library.catalog.presentation.CatalogUiState
import com.sleke.library.catalog.presentation.SlekeAppsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SlekeCatalogNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadingShowsProgress() {
        compose.setContent { MaterialTheme { SlekeAppsContent(CatalogUiState(), {}, {}, {}, {}) } }
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    @Test fun errorOffersFreshRetry() {
        var retries = 0
        compose.setContent {
            MaterialTheme {
                SlekeAppsContent(CatalogUiState(CatalogStatus.Error), {}, {}, {}, { retries++ })
            }
        }
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test fun emptyCatalogHasNoDownloadAction() {
        compose.setContent {
            MaterialTheme { SlekeAppsContent(CatalogUiState(CatalogStatus.Empty), {}, {}, {}, {}) }
        }
        compose.onNodeWithText("No apps found").assertExists()
        compose.onNodeWithText("Download").assertDoesNotExist()
    }

    @Test fun detailsKeepsOriginalSourcePackage() {
        var opened: String? = null
        val row =
            CatalogApp(
                CatalogIdentity("apks", "source-row"),
                "original.package",
                "https://source",
                title = "Friendly title"
            )
        compose.setContent {
            MaterialTheme {
                SlekeAppsContent(CatalogUiState(CatalogStatus.Populated, listOf(row)), {}, {
                    opened =
                        it
                }, {}, {})
            }
        }
        compose.onNodeWithText("Friendly title").performClick()
        assertEquals("original.package", opened)
    }
}
