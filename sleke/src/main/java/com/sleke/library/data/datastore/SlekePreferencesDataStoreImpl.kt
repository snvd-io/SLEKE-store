package com.sleke.library.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sleke.library.domain.SignedAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SlekePreferencesDataStoreImpl(
    private val context: Context,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : SlekePreferencesDataStore {

    private val dataStore = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("sleke_prefs") }
    )

    private object Keys {
        val SIGNED_EMAIL = stringPreferencesKey("signed_email")
        val SIGNED_UID = stringPreferencesKey("signed_uid")
        val IS_ENTERPRISE = booleanPreferencesKey("is_enterprise")
    }

    override val signedAccount: Flow<SignedAccount?> =
        dataStore.data.map { preferences ->
            preferences.toEnterpriseAccount()
        }

    override suspend fun setEnterpriseAccount(
        signedUid: String,
        signedEmail: String,
        isEnterprise: Boolean
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.SIGNED_EMAIL] = signedEmail
            preferences[Keys.SIGNED_UID] = signedUid
            preferences[Keys.IS_ENTERPRISE] = isEnterprise
        }
    }

    override suspend fun clean() {
        dataStore.edit {
            it.remove(Keys.SIGNED_UID)
            it.remove(Keys.SIGNED_EMAIL)
            it.remove(Keys.IS_ENTERPRISE)
        }
    }

    private fun Preferences.toEnterpriseAccount(): SignedAccount? =
        this[Keys.SIGNED_EMAIL]?.let { email ->
            SignedAccount(
                signedUid = this[Keys.SIGNED_UID].orEmpty(),
                signedEmail = email,
                isEnterprise = this[Keys.IS_ENTERPRISE] ?: false
            )
        }
}
