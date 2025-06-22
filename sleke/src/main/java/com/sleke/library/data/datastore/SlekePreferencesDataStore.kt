package com.sleke.library.data.datastore

import com.sleke.library.domain.SignedAccount
import kotlinx.coroutines.flow.Flow

interface SlekePreferencesDataStore {
    val signedAccount: Flow<SignedAccount?>
    suspend fun setEnterpriseAccount(signedUid: String, signedEmail: String, isEnterprise: Boolean = true)
    suspend fun clean()
}