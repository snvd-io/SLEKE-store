package com.sleke.library.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sleke.library.model.firebase.Apk
import kotlinx.coroutines.tasks.await

class FirestorePagingSource(
    private val firestore: FirebaseFirestore,
    private val searchQuery: String? = null
) : PagingSource<DocumentSnapshot, Apk>() {

    private fun buildBaseQuery(): Query {
        val col = firestore.collection("apks")
        return if (!searchQuery.isNullOrBlank()) {
            val trimmed = searchQuery.trim()
            col.orderBy("name")
                .whereGreaterThanOrEqualTo("name", trimmed)
                .whereLessThanOrEqualTo("name", "$trimmed\uf8ff")
        } else {
            col.orderBy("name")
        }
    }

    override suspend fun load(
        params: LoadParams<DocumentSnapshot>
    ): LoadResult<DocumentSnapshot, Apk> {
        return try {
            val q = buildBaseQuery()
                .limit(params.loadSize.toLong())
                .let { base ->
                    params.key?.let { base.startAfter(it) } ?: base
                }

            val snap = q.get().await()
            val docs = snap.documents
            val apks = docs.mapNotNull { it.toObject(Apk::class.java) }
            val nextKey = docs.lastOrNull()

            LoadResult.Page(
                data = apks,
                prevKey = null,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, Apk>): DocumentSnapshot? {
        return state.anchorPosition?.let { pos ->
            state.closestPageToPosition(pos)?.nextKey
                ?: state.closestPageToPosition(pos)?.prevKey
        }
    }
}