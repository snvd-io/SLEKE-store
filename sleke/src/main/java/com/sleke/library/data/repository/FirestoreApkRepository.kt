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
    private val firestore: FirebaseFirestore,
) : ApkRepository {
    override fun apkPagingSource(searchQuery: String?): PagingSource<DocumentSnapshot, Apk> {
        return FirestorePagingSource(firestore, searchQuery)
    }
} 