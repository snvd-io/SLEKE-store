package com.aurora.store.data.repository

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.store.data.providers.AuthProvider
import com.sleke.library.data.model.Apk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for GPlayAPI operations
 */
@Singleton
class GPlayRepository @Inject constructor(
    private val authProvider: AuthProvider
) {
    private val appInstalled: HashMap<String, String> = hashMapOf()

    /**
     * Get detailed app information from GPlayAPI for a given package name
     */
    suspend fun getAppDetails(packageName: String): Apk? = withContext(Dispatchers.IO) {
        try {
            // Get auth data from AuthProvider
            val authData = authProvider.authData ?: return@withContext null
            
            // Create instance of AppDetailsHelper with auth data
            val appDetailsHelper = AppDetailsHelper(authData)
            
            // Use GPlayAPI to fetch app details
            val appDetails: App = appDetailsHelper.getAppByPackageName(packageName) ?: return@withContext null
            
            // Convert GPlayAPI app details to our Apk model
            return@withContext convertToApk(appDetails)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    /**
     * Enrich Firebase apps with GPlayAPI details
     */
    suspend fun enrichWithAppDetails(firebaseApps: List<Apk>): List<Apk> = withContext(Dispatchers.IO) {
        // Get auth data from AuthProvider
        val authData = authProvider.authData ?: return@withContext firebaseApps
        
        // Create instance of AppDetailsHelper with auth data
        val appDetailsHelper = AppDetailsHelper(authData)
        
        // Create a mutable list to store enriched apps
        val enrichedApps = mutableListOf<Apk>()
        
        // Process each Firebase app to get complete details
        for (firebaseApp in firebaseApps) {
            try {
                // Try to get details from GPlayAPI
                val appDetails: App? = appDetailsHelper.getAppByPackageName(firebaseApp.packageName)
                
                // If details are found, add the enriched app, otherwise use the Firebase data
                if (appDetails != null) {
                    enrichedApps.add(convertToApk(appDetails))
                } else {
                    enrichedApps.add(firebaseApp)
                }
            } catch (e: Exception) {
                enrichedApps.add(firebaseApp)
                e.printStackTrace()
            }
        }
        
        return@withContext enrichedApps
    }
    
    private fun convertToApk(app: App): Apk {
        return Apk(
            name = app.displayName,
            packageName = app.packageName,
            versionNameDisplay = app.versionName,
            description = app.description,
            publisher = app.developerName,
            type = "app",
            link = "",
            iconUrl = app.iconArtwork.url,
            rating = app.rating.average.toFloat(),
            installs = app.installs.toString(),
            isGplayEnriched = true
        )
    }

    fun updateInstalledApp(packageName: String, appName: String) {
        appInstalled[packageName] = appName
    }

    fun getInstalledAppName(packageName: String): String? {
        return appInstalled[packageName]
    }
} 