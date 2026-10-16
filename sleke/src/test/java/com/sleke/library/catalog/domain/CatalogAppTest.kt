package com.sleke.library.catalog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogAppTest {
    @Test fun sourceFieldsSurviveMetadata() {
        val source =
            CatalogApp(
                CatalogIdentity("apks", "doc-1"),
                "a.b",
                "https://example.test/a.apk",
                "enterprise",
                "AU",
                title = "Source"
            )
        val enriched = source.withMetadata(CatalogMetadata(title = "Play", iconUrl = "icon"))
        assertEquals(source.identity, enriched.identity)
        assertEquals(source.downloadUrl, enriched.downloadUrl)
        assertEquals(source.packageName, enriched.packageName)
        assertEquals("enterprise", enriched.type)
        assertEquals("AU", enriched.country)
        assertEquals("Play", enriched.title)
        assertEquals(source, source.withMetadata(null))
    }
}
