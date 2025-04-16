package com.aurora.sleke.home

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.aurora.store.data.repository.GPlayRepository
import com.firebase.ui.firestore.paging.FirestorePagingOptions
import com.sleke.library.data.model.Apk
import com.sleke.library.data.repository.ApkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firestoreRepository: ApkRepository,
    private val gplayRepository: GPlayRepository
) : ViewModel() {

    private val _detailedApps = MutableStateFlow<List<Apk>>(emptyList())
    val detailedApps: StateFlow<List<Apk>> = _detailedApps

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _searchQuery = MutableStateFlow<String>("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Internal query with debounce applied
    private val debouncedSearchQuery = _searchQuery
        .debounce(300) // 300ms debounce to wait for user to stop typing

    val pagedApps: Flow<PagingData<Apk>> = debouncedSearchQuery
        .flatMapLatest { query ->
            createPager(query)
        }
        .cachedIn(viewModelScope)

    private fun createPager(query: String): Flow<PagingData<Apk>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 10,
                enablePlaceholders = false,
                initialLoadSize = 40
            )
        ) {
            if (query.isEmpty()) {
                firestoreRepository.getPagedApks()
            } else {
                firestoreRepository.searchPagedApks(query)
            }
        }.flow
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadAndEnrichApps(limit: Int = 20) {
        viewModelScope.launch {
            _isLoading.value = true
            
            val firebaseApps = firestoreRepository.getApkList(limit)

            if (firebaseApps.isNotEmpty()) {
                val enrichedApps = gplayRepository.enrichWithAppDetails(firebaseApps)
                _detailedApps.value = enrichedApps
            }

            _isLoading.value = false
        }
    }

    fun getAllApks(lifecycleOwner: LifecycleOwner): FirestorePagingOptions<Apk> {
        return firestoreRepository.getAllApks(lifecycleOwner)
    }

    fun searchApks(query: String, lifecycleOwner: LifecycleOwner): FirestorePagingOptions<Apk> {
        return firestoreRepository.searchApksByName(query, lifecycleOwner)
    }

    fun getAppDetails(packageName: String, callback: (Apk?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val app = gplayRepository.getAppDetails(packageName)
            _isLoading.value = false
            callback(app)
        }
    }

    @Deprecated("Use loadAndEnrichApps() instead", ReplaceWith("loadAndEnrichApps()"))
    fun enrichAppsWithDetails(firebaseApps: List<Apk>) {
        viewModelScope.launch {
            _isLoading.value = true
            val enrichedApps = gplayRepository.enrichWithAppDetails(firebaseApps)
            _detailedApps.value = enrichedApps
            _isLoading.value = false
        }
    }
} 