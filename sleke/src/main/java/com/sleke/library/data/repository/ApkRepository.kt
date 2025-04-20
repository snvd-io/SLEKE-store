package com.sleke.library.data.repository

import androidx.paging.PagingSource
import com.google.firebase.firestore.DocumentSnapshot
import com.sleke.library.data.model.Apk

interface ApkRepository {
    fun apkPagingSource(searchQuery: String? = null): PagingSource<DocumentSnapshot, Apk>
}