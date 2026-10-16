package com.sleke.integration.catalog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.store.data.AccountRepository
import com.aurora.store.data.providers.AuthProvider
import com.aurora.store.util.Preferences
import com.sleke.library.catalog.domain.CatalogMetadata
import com.sleke.library.catalog.domain.CatalogMetadataSource
import com.sleke.library.catalog.domain.MetadataContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class PlayCatalogMetadataSource @Inject constructor(
    private val auth: AuthProvider,
    accounts: AccountRepository,
    @ApplicationContext private val application: Context
) : CatalogMetadataSource {
    private val mutableContext = MutableStateFlow(MetadataContext(UUID.randomUUID().toString()))
    override val context = mutableContext.asStateFlow()
    private fun invalidate() {
        mutableContext.value = MetadataContext(UUID.randomUUID().toString())
    }
    private val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null ||
            key.startsWith("LOCALE_SPOOF_") ||
            key.startsWith("DEVICE_SPOOF_") ||
            key == Preferences.PREFERENCE_VENDING_VERSION ||
            key == Preferences.PREFERENCE_AUTH_DATA
        ) {
            invalidate()
        }
    }
    private val localeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            invalidate()
        }
    }
    init {
        // Both registrations have process lifetime, matching this singleton and its account observer.
        Preferences.getPrefs(
            application
        ).registerOnSharedPreferenceChangeListener(preferencesListener)
        application.registerReceiver(localeReceiver, IntentFilter(Intent.ACTION_LOCALE_CHANGED))
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            accounts.defaultAccount.collect { invalidate() }
        }
    }
    override suspend fun load(packageName: String, context: MetadataContext): CatalogMetadata? =
        withContext(Dispatchers.IO) {
            if (this@PlayCatalogMetadataSource.context.value !=
                context
            ) {
                throw CancellationException("Play context changed")
            }
            val authData = auth.authData ?: return@withContext null
            if (authData.email == "BOGUS") return@withContext null
            val app =
                AppDetailsHelper(authData).getAppByPackageName(packageName)
                    ?: return@withContext null
            if (this@PlayCatalogMetadataSource.context.value !=
                context
            ) {
                throw CancellationException("Play context changed")
            }
            CatalogMetadata(
                app.displayName,
                app.description,
                app.developerName,
                app.versionName,
                app.iconArtwork.url,
                app.rating.average.toFloat(),
                app.installs.toString()
            )
        }
}
