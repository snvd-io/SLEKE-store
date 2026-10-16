package com.sleke.library.eligibility.domain

import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.domain.CatalogIdentity
import com.sleke.library.catalog.domain.CatalogResult
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogEligibilityPolicyTest {
    private val catalog = CatalogResult.Success(
        listOf(
            CatalogApp(
                identity = CatalogIdentity("apks", "fixture"),
                packageName = "member",
                downloadUrl = "https://example.com/a.apk"
            )
        )
    )
    private val failure = CatalogResult.Failure(IllegalStateException("unavailable"))
    private val member = PackageFacts("member")
    private val options = UpdateFilterOptions(catalogOnly = false)

    @Test
    fun installedAlwaysIntersectsCatalogAndBlacklist() {
        val result = CatalogEligibilityPolicy.installed(
            listOf(member, PackageFacts("other"), member.copy(blacklisted = true)),
            catalog
        )
        assertEquals(EligibilityResult.Allowed(setOf("member")), result)
        assertEquals(
            EligibilityResult.Unavailable,
            CatalogEligibilityPolicy.installed(listOf(member), failure)
        )
        assertEquals(
            EligibilityResult.Allowed(emptySet()),
            CatalogEligibilityPolicy.installed(listOf(member), CatalogResult.Success(emptyList()))
        )
    }

    @Test
    fun nonmemberUpdatesAreAllowedOnlyWithoutCatalogFilter() {
        val outside = PackageFacts("outside")
        assertEquals(
            EligibilityResult.Allowed(setOf("outside")),
            CatalogEligibilityPolicy.updates(listOf(outside), options, null)
        )
        assertEquals(
            EligibilityResult.Allowed(emptySet()),
            CatalogEligibilityPolicy.updates(
                listOf(outside),
                options.copy(catalogOnly = true),
                catalog
            )
        )
        assertEquals(
            EligibilityResult.Allowed(emptySet()),
            CatalogEligibilityPolicy.installed(listOf(member.copy(blacklisted = true)), catalog)
        )
    }

    @Test
    fun updateTruthTablePreservesBothPreferenceBranches() {
        for (catalogOnly in listOf(false, true)) {
            for (extended in listOf(false, true)) {
                for (enabled in listOf(false, true)) {
                    for (blacklisted in listOf(false, true)) {
                        for (fdroid in listOf(false, true)) {
                            for (installer in listOf(null, "blocked", "other")) {
                                val facts = member.copy(
                                    enabled = enabled,
                                    blacklisted = blacklisted,
                                    fdroid = fdroid,
                                    installerPackage = installer
                                )
                                val expected = !blacklisted &&
                                    (enabled || extended) &&
                                    (catalogOnly || (!fdroid && installer != "blocked"))
                                assertEquals(
                                    EligibilityResult.Allowed(
                                        if (expected) setOf("member") else emptySet()
                                    ),
                                    CatalogEligibilityPolicy.updates(
                                        listOf(facts),
                                        UpdateFilterOptions(
                                            catalogOnly,
                                            extended,
                                            true,
                                            setOf("blocked")
                                        ),
                                        if (catalogOnly) catalog else null
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun failedRequiredCatalogNeverBroadensOrLooksEmpty() {
        assertEquals(
            EligibilityResult.Unavailable,
            CatalogEligibilityPolicy.updates(
                listOf(member),
                options.copy(catalogOnly = true),
                failure
            )
        )
        assertEquals(
            EligibilityResult.Unavailable,
            CatalogEligibilityPolicy.updates(listOf(member), options.copy(catalogOnly = true), null)
        )
        assertEquals(
            EligibilityResult.Allowed(setOf("member")),
            CatalogEligibilityPolicy.updates(listOf(member), options, failure)
        )
        assertEquals(
            EligibilityResult.Allowed(emptySet()),
            CatalogEligibilityPolicy.updates(
                listOf(member),
                options.copy(catalogOnly = true),
                CatalogResult.Success(emptyList())
            )
        )
    }

    @Test
    fun gmsIsExcludedFromEveryUpdateModeButRemainsManuallyDiscoverable() {
        val gms = PackageFacts("com.google.android.gms")
        val gmsCatalog = CatalogResult.Success(
            listOf(
                CatalogApp(
                    identity = CatalogIdentity("apks", "fixture"),
                    packageName = gms.packageName,
                    downloadUrl = "https://example.com/gms.apk"
                )
            )
        )
        for (catalogOnly in listOf(false, true)) {
            assertEquals(
                EligibilityResult.Allowed(emptySet()),
                CatalogEligibilityPolicy.updates(
                    listOf(gms),
                    options.copy(catalogOnly = catalogOnly, extended = true),
                    gmsCatalog
                )
            )
        }
        assertEquals(
            EligibilityResult.Allowed(setOf(gms.packageName)),
            CatalogEligibilityPolicy.installed(listOf(gms), gmsCatalog)
        )
    }
}
