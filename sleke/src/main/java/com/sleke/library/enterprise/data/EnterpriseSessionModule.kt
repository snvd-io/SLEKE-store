package com.sleke.library.enterprise.data

import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class EnterpriseSessionModule {
    @Binds abstract fun bindSession(
        repository: FirebaseEnterpriseSessionRepository
    ): EnterpriseSessionRepository

    @Binds abstract fun bindSource(source: DefaultFirebaseSessionSource): FirebaseSessionSource

    @Binds abstract fun storage(data: PreferencesEnterpriseSessionStorage): EnterpriseSessionStorage
}
