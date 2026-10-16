package com.sleke.integration.auth

import com.sleke.library.enterprise.domain.PlaySessionGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SlekeAuthIntegrationModule {
    @Binds abstract fun bindPlaySession(adapter: PlaySessionAdapter): PlaySessionGateway
}
