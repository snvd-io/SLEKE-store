package com.sleke.library.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sleke.library.domain.EnterpriseAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SlekePreferencesDataStoreImpl(private val context: Context) : SlekePreferencesDataStore {

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("sleke_prefs") }
    )

    private object Keys {
        val ENTERPRISE_EMAIL = stringPreferencesKey("enterprise_account_email")
        val ENTERPRISE_ID = stringPreferencesKey("enterprise_account_id")
    }

    override val enterpriseAccount: Flow<EnterpriseAccount?> =
        dataStore.data.map {
            val email = it[Keys.ENTERPRISE_EMAIL]
            val id = it[Keys.ENTERPRISE_ID]

            if (email != null && id != null) {
                EnterpriseAccount(id, email)
            } else {
                null
            }
        }

    override suspend fun setEnterpriseAccount(id: String, email: String) {
        dataStore.edit {
            it[Keys.ENTERPRISE_EMAIL] = email
            it[Keys.ENTERPRISE_ID] = id
        }
    }

    override suspend fun clean() {
        dataStore.edit { it.clear() }
    }
}