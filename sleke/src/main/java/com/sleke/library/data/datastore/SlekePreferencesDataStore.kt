package com.sleke.library.data.datastore

interface SlekePreferencesDataStore {
    suspend fun enterpriseAccount(): String?
    suspend fun setEnterpriseAccount(email: String)
    suspend fun clean()
}