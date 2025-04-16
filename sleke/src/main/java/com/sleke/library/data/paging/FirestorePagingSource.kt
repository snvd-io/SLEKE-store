package com.sleke.library.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sleke.library.data.model.Apk
import kotlinx.coroutines.tasks.await

class FirestorePagingSource(
    private val firestore: FirebaseFirestore,
    private val searchQuery: String? = null
) : PagingSource<DocumentSnapshot, Apk>() {

    private var lastVisibleItem: DocumentSnapshot? = null

    private val baseQuery: Query = createQuery()

    private fun createQuery(): Query {
        val query = firestore.collection("apks")
        return query.orderBy("name")
    }

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, Apk> {
        return try {
            val isSearchQuery = !searchQuery.isNullOrEmpty()
            
            val query = baseQuery.run {
                if (isSearchQuery) {
                    limit(30)
                } else {
                    limit(params.loadSize.toLong())
                        .run { params.key?.let { startAfter(it) } ?: this }
                }
            }
                
            val querySnapshot = query.get().await()
            val documents = querySnapshot.documents
            
            val filteredDocs = if (isSearchQuery) {
                val trimmedQuery = searchQuery.trim()
                documents.filter { doc ->
                    val app = doc.toObject(Apk::class.java)
                    app?.name?.contains(trimmedQuery, ignoreCase = true) == true
                }
            } else {
                documents
            }
            
            lastVisibleItem = if (!isSearchQuery) documents.lastOrNull() else null
            
            val apks = filteredDocs.mapNotNull { it.toObject(Apk::class.java) }
            
            LoadResult.Page(
                data = apks,
                prevKey = null,
                nextKey = if (!isSearchQuery) lastVisibleItem else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, Apk>): DocumentSnapshot? = null
} 