package com.sleke.library.data.repository

import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.google.firebase.firestore.DocumentSnapshot
import com.sleke.library.model.firebase.Apk
import com.sleke.library.model.firebase.SlekeApkDto
import kotlinx.coroutines.flow.Flow

interface ApkRepository {
    suspend fun slekeApks(): List<SlekeApkDto>
    suspend fun getAllApks(): List<Apk>
}