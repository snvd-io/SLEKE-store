package com.aurora.store.viewmodel.home

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firestoreRepository: ApkRepository,
    private val gplayRepository: GPlayRepository
) : ViewModel() {
    
    // StateFlow for detailed apps (used for non-paged data)
    private val _detailedApps = MutableStateFlow<List<Apk>>(emptyList())
    val detailedApps: StateFlow<List<Apk>> = _detailedApps
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    // Paging data
    private val _pagingData = MutableStateFlow<PagingData<Apk>?>(null)
    val pagingData: StateFlow<PagingData<Apk>?> = _pagingData
    
    // Get paged apps directly as Flow
    val pagedApps: Flow<PagingData<Apk>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            prefetchDistance = 10,
            enablePlaceholders = true,
            initialLoadSize = 40
        )
    ) {
        firestoreRepository.getPagedApks()
    }.flow.cachedIn(viewModelScope)
    
    /**
     * Load apps from Firebase and enrich them with GPlayAPI details
     * This method is used for initial load or small batches
     */
    fun loadAndEnrichApps(limit: Int = 20) {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Get apps from Firestore repository
            val firebaseApps = firestoreRepository.getApkList(limit)
            
            // Enrich with GPlayAPI details
            if (firebaseApps.isNotEmpty()) {
                val enrichedApps = gplayRepository.enrichWithAppDetails(firebaseApps)
                _detailedApps.value = enrichedApps
            }
            
            _isLoading.value = false
        }
    }
    
    /**
     * Get all APKs for paging
     */
    fun getAllApks(lifecycleOwner: LifecycleOwner): FirestorePagingOptions<Apk> {
        return firestoreRepository.getAllApks(lifecycleOwner)
    }
    
    /**
     * Search APKs by name
     */
    fun searchApks(query: String, lifecycleOwner: LifecycleOwner): FirestorePagingOptions<Apk> {
        return firestoreRepository.searchApksByName(query, lifecycleOwner)
    }
    
    /**
     * Get detailed app information from GPlayAPI
     */
    fun getAppDetails(packageName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val app = gplayRepository.getAppDetails(packageName)
            _isLoading.value = false
            // Process the result as needed
        }
    }
    
    /**
     * Enrich a list of apps with details from GPlayAPI
     * @deprecated Use loadAndEnrichApps() instead
     */
    @Deprecated("Use loadAndEnrichApps() instead", ReplaceWith("loadAndEnrichApps()"))
    fun enrichAppsWithDetails(firebaseApps: List<Apk>) {
        viewModelScope.launch {
            _isLoading.value = true
            val enrichedApps = gplayRepository.enrichWithAppDetails(firebaseApps)
            _detailedApps.value = enrichedApps
            _isLoading.value = false
        }
    }
    
    /**
     * Update list of installed apps
     */
    fun updateInstalledApp(packageName: String, appName: String) {
        gplayRepository.updateInstalledApp(packageName, appName)
    }
    
    /**
     * Check if app is installed by package name
     */
    fun getInstalledAppName(packageName: String): String? {
        return gplayRepository.getInstalledAppName(packageName)
    }
} 