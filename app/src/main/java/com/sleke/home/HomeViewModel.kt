package com.aurora.sleke.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.aurora.store.data.repository.GPlayRepository
import com.sleke.library.model.firebase.Apk
import com.sleke.library.data.repository.ApkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

    @OptIn(FlowPreview::class)
    val pagedApps: Flow<PagingData<Apk>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            Pager(
                config = PagingConfig(
                    pageSize = 5,
                    prefetchDistance = 5,
                    enablePlaceholders = false,
                    initialLoadSize = 5
                ),
                pagingSourceFactory = {
                    firestoreRepository.apkPagingSource(
                        if (query.isBlank()) null else query
                    )
                }
            ).flow.map { pagingData ->
                pagingData.map { apk ->
                    gplayRepository.getAppDetails(apk.packageName) ?: apk
                }
            }
        }
        .cachedIn(viewModelScope)

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
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