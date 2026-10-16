package com.sleke.library.catalog.data

import com.google.firebase.firestore.FirebaseFirestore
import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.domain.CatalogRepository
import com.sleke.library.catalog.domain.CatalogResult
import com.sleke.library.catalog.domain.CatalogScope
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import com.sleke.library.model.firebase.AppDto
import com.sleke.library.model.firebase.EnterpriseAppDto
import com.sleke.library.model.firebase.SlekeApkDto
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

interface CatalogDocumentsSource {
    suspend fun load(scope: CatalogScope, enterpriseId: String?): List<CatalogApp>
}

class FirebaseCatalogDocumentsSource @Inject constructor(
    private val firestore: FirebaseFirestore
) : CatalogDocumentsSource {
    override suspend fun load(scope: CatalogScope, enterpriseId: String?): List<CatalogApp> {
        val path = when (scope) {
            CatalogScope.Regular -> "apks"
            CatalogScope.Sleke -> "sleke_apps"
            CatalogScope.Enterprise -> "enterprise/${requireNotNull(enterpriseId)}/apps"
        }
        val collection = firestore.collection(path)
        val snapshot = (
            if (scope ==
                CatalogScope.Regular
            ) {
                collection.orderBy("name")
            } else {
                collection
            }
            ).get().await()
        return snapshot.documents.mapNotNull { document ->
            when (scope) {
                CatalogScope.Regular -> document.toObject(AppDto::class.java)?.let {
                    CatalogDtoMapper.map(document.id, it)
                }
                CatalogScope.Sleke -> document.toObject(SlekeApkDto::class.java)?.let {
                    CatalogDtoMapper.map(document.id, it)
                }
                CatalogScope.Enterprise -> document.toObject(EnterpriseAppDto::class.java)?.let {
                    CatalogDtoMapper.map(path, document.id, it)
                }
            }
        }
    }
}

class FirestoreCatalogRepository(
    private val documents: CatalogDocumentsSource,
    private val sessions: EnterpriseSessionRepository,
    private val store: CatalogFetchStore,
    scope: CoroutineScope
) : CatalogRepository {
    private val lock = Any()
    private var identity = sessions.state.value.catalogIdentity()
    init {
        scope.launch { sessions.state.collect { state -> synchronized(lock) { reconcile(state) } } }
    }
    private fun reconcile(state: EnterpriseSessionState) {
        val next = state.catalogIdentity()
        if (next != identity) {
            identity = next
            store.invalidate()
        }
    }
    override suspend fun load(scope: CatalogScope, refresh: Boolean): CatalogResult {
        val state = sessions.state.value
        synchronized(lock) { reconcile(state) }
        val allowed = state as? EnterpriseSessionState.Allowed
        if (scope == CatalogScope.Enterprise && allowed == null) {
            return CatalogResult.Failure(
                IllegalStateException("Enterprise membership must be verified")
            )
        }
        val captured = state.catalogIdentity()
        val result = store.load("$captured/$scope", refresh) {
            documents.load(scope, allowed?.enterpriseId).let(CatalogResult::Success)
        }
        if (sessions.state.value.catalogIdentity() !=
            captured
        ) {
            throw CancellationException("Catalog identity changed")
        }
        return result
    }
}

private fun EnterpriseSessionState.catalogIdentity(): String = when (this) {
    is EnterpriseSessionState.Allowed -> "${identity.uid}/$enterpriseId/allowed"
    is EnterpriseSessionState.Checking -> "${identity.uid}/checking"
    is EnterpriseSessionState.NoMembership -> "${identity.uid}/none"
    is EnterpriseSessionState.Unavailable -> "${identity.uid}/unavailable"
    EnterpriseSessionState.Restoring -> "restoring"
    EnterpriseSessionState.SignedOut -> "signed-out"
}
