package com.sleke.library.data.datastore

import com.sleke.library.domain.EnterpriseAccount
import kotlinx.coroutines.flow.Flow

interface SlekePreferencesDataStore {
    val enterpriseAccount: Flow<EnterpriseAccount?>
    suspend fun setEnterpriseAccount(id: String, email: String)
    suspend fun clean()
}