package com.sleke.library.catalog.data

import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.domain.CatalogResult
import com.sleke.library.catalog.domain.CatalogScope
import com.sleke.library.enterprise.domain.CleanupReport
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import com.sleke.library.model.firebase.AppDto
import com.sleke.library.model.firebase.EnterpriseAppDto
import com.sleke.library.model.firebase.SlekeApkDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreCatalogRepositoryTest {
    @Test fun samePackageDifferentCollectionsKeepsIdentityAndSourceFields() {
        val regular = CatalogDtoMapper.map(
            "r",
            AppDto(
                name = "Regular",
                packageName = "a.b",
                link = "regular",
                country = "AU",
                type = "web"
            )
        )
        val sleke = CatalogDtoMapper.map(
            "s",
            SlekeApkDto(name = "Sleke", packageName = "a.b", link = "sleke", type = "private")
        )
        val enterprise = CatalogDtoMapper.map(
            "enterprise/team/apps",
            "e",
            EnterpriseAppDto(packageName = "a.b", link = "private")
        )
        assertNotEquals(regular.identity, sleke.identity)
        assertEquals("AU", regular.country)
        assertEquals("private", enterprise.downloadUrl)
        assertEquals("private", sleke.type)
    }

    @Test fun enterpriseWithoutAllowedMembershipFailsWithoutFetching() = runTest {
        val sessions = object : EnterpriseSessionRepository {
            override val state =
                MutableStateFlow<EnterpriseSessionState>(EnterpriseSessionState.SignedOut)
            override suspend fun refresh() = state.value
            override suspend fun revokeAccess() {
                state.value = EnterpriseSessionState.SignedOut
            }
            override suspend fun signOut(): CleanupReport = error("not used")
        }
        val documents = object : CatalogDocumentsSource {
            override suspend fun load(
                scope: CatalogScope,
                enterpriseId: String?
            ): List<CatalogApp> = error("must not fetch")
        }
        val repository =
            FirestoreCatalogRepository(
                documents,
                sessions,
                CatalogFetchStore(backgroundScope),
                backgroundScope
            )
        assertTrue(repository.load(CatalogScope.Enterprise) is CatalogResult.Failure)
    }
}
