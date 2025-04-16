package com.sleke.library.di

import com.google.firebase.firestore.FirebaseFirestore
import com.sleke.library.data.repository.ApkRepository
import com.sleke.library.data.repository.FirestoreApkRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
} 