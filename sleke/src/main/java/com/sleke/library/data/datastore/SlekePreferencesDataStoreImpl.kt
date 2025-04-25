package com.sleke.library.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SlekePreferencesDataStoreImpl(private val context: Context) : SlekePreferencesDataStore {

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("sleke_prefs") }
    )

    private object Keys {
        val ENTERPRISE = stringPreferencesKey("enterprise_account")
    }

    override suspend fun enterpriseAccount(): String? =
        dataStore.data.map { it[Keys.ENTERPRISE] }.first()

    override suspend fun setEnterpriseAccount(email: String) {
        dataStore.edit { it[Keys.ENTERPRISE] = email }
    }

    override suspend fun clean() {
        dataStore.edit { it.clear() }
    }
}