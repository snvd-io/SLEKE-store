package com.sleke.library.di

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.sleke.library.data.datastore.SlekePreferencesDataStore
import com.sleke.library.data.datastore.SlekePreferencesDataStoreImpl
import com.sleke.library.data.repository.ApkRepository
import com.sleke.library.data.repository.FirestoreApkRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideApkRepository(repository: FirestoreApkRepository): ApkRepository {
        return repository
    }

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): SlekePreferencesDataStore =
        SlekePreferencesDataStoreImpl(context)
} 