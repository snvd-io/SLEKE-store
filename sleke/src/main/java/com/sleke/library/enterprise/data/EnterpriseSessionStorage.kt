package com.sleke.library.enterprise.data

import com.google.firebase.auth.FirebaseAuth
import com.sleke.library.data.datastore.SlekePreferencesDataStore
import com.sleke.library.enterprise.domain.EnterpriseIdentity
import javax.inject.Inject

interface EnterpriseSessionStorage {
    suspend fun reconcile(identity: EnterpriseIdentity, isEnterprise: Boolean)
    suspend fun clear()
}

class PreferencesEnterpriseSessionStorage @Inject constructor(
    private val preferences: SlekePreferencesDataStore,
    private val auth: FirebaseAuth
) : EnterpriseSessionStorage {
    override suspend fun reconcile(identity: EnterpriseIdentity, isEnterprise: Boolean) {
        val current = auth.currentUser
        check(current?.uid == identity.uid) { "Enterprise identity changed before persistence" }
        preferences.setEnterpriseAccount(identity.uid, current.email.orEmpty(), isEnterprise)
    }
    override suspend fun clear() = preferences.clean()
}
