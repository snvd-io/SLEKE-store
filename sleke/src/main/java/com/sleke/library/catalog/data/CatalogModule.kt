package com.sleke.library.catalog.data

import com.sleke.library.catalog.domain.CatalogRepository
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides @Singleton
    fun repository(
        documents: FirebaseCatalogDocumentsSource,
        sessions: EnterpriseSessionRepository
    ): CatalogRepository {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return FirestoreCatalogRepository(documents, sessions, CatalogFetchStore(scope), scope)
    }
}
