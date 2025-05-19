package com.sleke.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.aurora.store.data.repository.GPlayRepository
import com.sleke.home.AppDetailsPagingSource
import com.sleke.library.data.repository.ApkRepository
import com.sleke.library.model.firebase.Apk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val apkRepository: ApkRepository,
    private val gplayRepository: GPlayRepository,
) : ViewModel() {

    private val _firebaseApps = MutableStateFlow<List<Apk>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _searchQuery = MutableStateFlow<String>("")
    val searchQuery: StateFlow<String> = _searchQuery
    
    private val _triggerRefresh = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            val apps = apkRepository.getAllApks()
            _firebaseApps.update { apps }
            _isLoading.value = false
            _triggerRefresh.value += 1
        }
    }

    @OptIn(FlowPreview::class)
    val pagedApps: Flow<PagingData<Apk>> = combine(
        _searchQuery.debounce(300).distinctUntilChanged(),
        _triggerRefresh
    ) { query, _ -> query }
        .flatMapLatest { query ->
            Pager(
                config = PagingConfig(
                    pageSize = 8,
                    enablePlaceholders = false,
                    prefetchDistance = 5
                )
            ) {
                AppDetailsPagingSource(
                    allApks = _firebaseApps.value,
                    gplayRepository = gplayRepository,
                    query = query
                )
            }.flow
        }
        .cachedIn(viewModelScope)

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}