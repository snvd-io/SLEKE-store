package com.aurora.store.data.repository

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.store.data.providers.AuthProvider
import com.sleke.library.model.firebase.AppDto
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

    suspend fun getAppDetails(packageName: String, firebaseApp: AppDto? = null): AppDto? = withContext(Dispatchers.IO) {
        try {
            val authData = authProvider.authData ?: return@withContext null
            val appDetailsHelper = AppDetailsHelper(authData)
            val appDetails: App = appDetailsHelper.getAppByPackageName(packageName) ?: return@withContext null
            return@withContext convertToApk(appDetails, firebaseApp)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    /**
     * Enrich Firebase apps with GPlayAPI details
     */
    suspend fun enrichWithAppDetails(firebaseApps: List<AppDto>): List<AppDto> = withContext(Dispatchers.IO) {
        val authData = authProvider.authData ?: return@withContext firebaseApps
        
        val appDetailsHelper = AppDetailsHelper(authData)
        
        val enrichedApps = mutableListOf<AppDto>()
        
        for (firebaseApp in firebaseApps) {
            try {
                val appDetails: App? = appDetailsHelper.getAppByPackageName(firebaseApp.packageName)
                
                if (appDetails != null) {
                    enrichedApps.add(convertToApk(appDetails, firebaseApp))
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
    
    private fun convertToApk(app: App, firebaseApp: AppDto? = null): AppDto {
        return AppDto(
            name = app.displayName,
            packageName = app.packageName,
            versionNameDisplay = app.versionName,
            description = app.description,
            publisher = app.developerName,
            type = "app",
            link = firebaseApp?.link.orEmpty(),
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