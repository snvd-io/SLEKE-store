package com.aurora.store.usecase

import android.content.Context
import com.aurora.store.data.providers.AccountProvider
import com.aurora.store.data.providers.AuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.sleke.library.data.datastore.SlekePreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authProvider: AuthProvider,
    private val slekePreferencesDataStore: SlekePreferencesDataStore,
) {
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    suspend operator fun invoke() {
        slekePreferencesDataStore.clean()
        firebaseAuth.signOut()
        authProvider.removeAuthData(context)
        AccountProvider.logout(context)
    }
}