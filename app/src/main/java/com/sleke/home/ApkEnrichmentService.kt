package com.aurora.sleke.home

import com.aurora.store.data.repository.GPlayRepository
import com.sleke.library.model.firebase.Apk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for enriching APK data with Google Play details
 */
@Singleton
class ApkEnrichmentService @Inject constructor(
    private val gplayRepository: GPlayRepository
) {
    /**
     * Enrich a list of APKs with Google Play data
     */
    suspend fun enrichWithAppDetails(apks: List<Apk>): List<Apk> = withContext(Dispatchers.IO) {
        val enrichedApks = mutableListOf<Apk>()
        
        for (apk in apks) {
            try {
                val enrichedApk = gplayRepository.getAppDetails(apk.packageName) ?: apk
                enrichedApks.add(enrichedApk)
            } catch (e: Exception) {
                Timber.tag("ApkEnrichmentService").e(e, "Failed to enrich ${apk.packageName}")
                enrichedApks.add(apk)
            }
        }
        
        return@withContext enrichedApks
    }
    
    /**
     * Enrich a single APK with Google Play data
     */
    suspend fun enrichAppDetails(apk: Apk): Apk = withContext(Dispatchers.IO) {
        return@withContext try {
            gplayRepository.getAppDetails(apk.packageName) ?: apk
        } catch (e: Exception) {
            Timber.tag("ApkEnrichmentService").e(e, "Failed to enrich ${apk.packageName}")
            apk
        }
    }
} 