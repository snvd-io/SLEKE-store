package com.sleke.library.eligibility.domain

import com.sleke.library.catalog.domain.CatalogResult

data class PackageFacts(
    val packageName: String,
    val blacklisted: Boolean = false,
    val enabled: Boolean = true,
    val fdroid: Boolean = false,
    val installerPackage: String? = null
)

data class UpdateFilterOptions(
    val catalogOnly: Boolean = false,
    val extended: Boolean = false,
    val excludeFdroid: Boolean = false,
    val excludedInstallers: Set<String> = emptySet()
)

sealed interface EligibilityResult {
    data class Allowed(val packages: Set<String>) : EligibilityResult
    data object Unavailable : EligibilityResult
}

object CatalogEligibilityPolicy {
    private const val GMS_PACKAGE = "com.google.android.gms"

    fun isAutomatedUpdateAllowed(packageName: String): Boolean = packageName != GMS_PACKAGE

    fun installed(facts: List<PackageFacts>, catalog: CatalogResult): EligibilityResult {
        if (catalog !is CatalogResult.Success) return EligibilityResult.Unavailable
        val members = catalog.apps.map { it.packageName }.toSet()
        return EligibilityResult.Allowed(
            facts.filter {
                !it.blacklisted && it.packageName in members
            }
                .map { it.packageName }.toSet()
        )
    }

    fun updates(
        facts: List<PackageFacts>,
        options: UpdateFilterOptions,
        catalog: CatalogResult? = null
    ): EligibilityResult {
        val members = if (options.catalogOnly) {
            if (catalog !is CatalogResult.Success) return EligibilityResult.Unavailable
            catalog.apps.map { it.packageName }.toSet()
        } else {
            null
        }
        return EligibilityResult.Allowed(
            facts.filter {
                isAutomatedUpdateAllowed(it.packageName) &&
                    !it.blacklisted &&
                    (options.extended || it.enabled) &&
                    if (members != null) {
                        it.packageName in members
                    } else {
                        !(options.excludeFdroid && it.fdroid) &&
                            (
                                it.installerPackage == null ||
                                    it.installerPackage !in options.excludedInstallers
                                )
                    }
            }.map { it.packageName }.toSet()
        )
    }
}
