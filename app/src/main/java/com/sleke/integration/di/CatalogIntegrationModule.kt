package com.sleke.integration.di

import com.sleke.integration.catalog.PlayCatalogMetadataSource
import com.sleke.library.catalog.domain.CatalogMetadataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogIntegrationModule {
    @Binds @Singleton
    abstract fun metadata(source: PlayCatalogMetadataSource): CatalogMetadataSource
}
