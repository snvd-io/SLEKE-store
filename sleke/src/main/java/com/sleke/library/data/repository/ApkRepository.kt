package com.sleke.library.data.repository

import androidx.lifecycle.LifecycleOwner
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import com.firebase.ui.firestore.paging.FirestorePagingOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.sleke.library.data.model.Apk

interface ApkRepository {
    fun getAllApks(
        lifecycleOwner: LifecycleOwner,
        pagingConfig: PagingConfig = PagingConfig(
            pageSize = 10,
            prefetchDistance = 2,
            enablePlaceholders = false
        )
    ): FirestorePagingOptions<Apk>
    
    fun searchApksByName(
        searchQuery: String,
        lifecycleOwner: LifecycleOwner,
        pagingConfig: PagingConfig = PagingConfig(
            pageSize = 10,
            prefetchDistance = 2,
            enablePlaceholders = false
        )
    ): FirestorePagingOptions<Apk>
    
    suspend fun getApkList(limit: Int = 20): List<Apk>
    
    fun getPagedApks(): PagingSource<DocumentSnapshot, Apk>
    
    fun searchPagedApks(query: String): PagingSource<DocumentSnapshot, Apk>
}