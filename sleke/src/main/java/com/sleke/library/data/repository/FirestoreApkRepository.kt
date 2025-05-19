package com.sleke.library.data.repository

import androidx.paging.PagingSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.sleke.library.data.Firestore
import com.sleke.library.data.paging.FirestorePagingSource
import com.sleke.library.model.firebase.Apk
import com.sleke.library.model.firebase.SlekeApkDto
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreApkRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ApkRepository {

    override suspend fun slekeApks(): List<SlekeApkDto> {
        val snapshot = firestore.collection(Firestore.Collection.SLEKE_APPS).get().await()
        return snapshot.toObjects(SlekeApkDto::class.java)
    }

    override suspend fun getAllApks(): List<Apk> {
        val snapshot = firestore.collection("apks").orderBy("name").get().await()
        return snapshot.toObjects(Apk::class.java)
    }
} 