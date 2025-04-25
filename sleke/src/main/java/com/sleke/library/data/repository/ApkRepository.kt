package com.sleke.library.data.repository

import androidx.paging.PagingSource
import com.google.firebase.firestore.DocumentSnapshot
import com.sleke.library.model.firebase.Apk
import com.sleke.library.model.firebase.SlekeApkDto

interface ApkRepository {
    fun apkPagingSource(searchQuery: String? = null): PagingSource<DocumentSnapshot, Apk>
    suspend fun slekeApks(): List<SlekeApkDto>
}