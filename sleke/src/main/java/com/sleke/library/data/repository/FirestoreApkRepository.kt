package com.sleke.library.data.repository

import androidx.lifecycle.LifecycleOwner
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import com.firebase.ui.firestore.paging.FirestorePagingOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.sleke.library.data.model.Apk
import com.sleke.library.data.paging.FirestorePagingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreApkRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : ApkRepository {
    
    override fun getAllApks(
        lifecycleOwner: LifecycleOwner,
        pagingConfig: PagingConfig
    ): FirestorePagingOptions<Apk> {
        val query = firestore.collection("apks")
            .orderBy("name")
            .limit(20)
            
        return FirestorePagingOptions.Builder<Apk>()
            .setLifecycleOwner(lifecycleOwner)
            .setQuery(query, pagingConfig, Apk::class.java)
            .build()
    }
    
    override fun searchApksByName(
        searchQuery: String,
        lifecycleOwner: LifecycleOwner,
        pagingConfig: PagingConfig
    ): FirestorePagingOptions<Apk> {
        val trimmedQuery = searchQuery.trim()
        
        val query = firestore.collection("apks")
            .whereGreaterThanOrEqualTo("name", trimmedQuery)
            .whereLessThanOrEqualTo("name", trimmedQuery + "\uf8ff")
            .limit(20)
            
        return FirestorePagingOptions.Builder<Apk>()
            .setLifecycleOwner(lifecycleOwner)
            .setQuery(query, pagingConfig, Apk::class.java)
            .build()
    }
    
    override suspend fun getApkList(limit: Int): List<Apk> = withContext(Dispatchers.IO) {
        try {
            val appsSnapshot = firestore.collection("apks")
                .orderBy("name")
                .limit(limit.toLong())
                .get()
                .await()
                
            return@withContext appsSnapshot.documents.mapNotNull { doc ->
                doc.toObject(Apk::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    override fun getPagedApks(): PagingSource<DocumentSnapshot, Apk> {
        return FirestorePagingSource(firestore)
    }

    override fun searchPagedApks(query: String): PagingSource<DocumentSnapshot, Apk> {
        return FirestorePagingSource(firestore, searchQuery = query)
    }
} 