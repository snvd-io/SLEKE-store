/*
 * Aurora Store
 *  Copyright (C) 2021, Rahul Kumar Patel <whyorean@gmail.com>
 *
 *  Aurora Store is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  Aurora Store is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.aurora.store.viewmodel.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.Constants
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.store.AuroraApp
import com.aurora.store.R
import com.aurora.store.data.event.AuthEvent
import com.aurora.store.data.model.AccountType
import com.aurora.store.data.model.AuthState
import com.aurora.store.data.providers.AccountProvider
import com.aurora.store.data.providers.AuthProvider
import com.aurora.store.usecase.LogoutUseCase
import com.aurora.store.util.AC2DMTask
import com.aurora.store.util.Preferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.sleke.library.data.datastore.SlekePreferencesDataStore
import com.sleke.library.data.repository.ApkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    val authProvider: AuthProvider,
    val dataStore: SlekePreferencesDataStore,
    val apkRepository: ApkRepository,
    val logoutUseCase: LogoutUseCase,
    @ApplicationContext private val context: Context,
    private val aC2DMTask: AC2DMTask
) : ViewModel() {

    private val TAG = AuthViewModel::class.java.simpleName

    private val _authState: MutableStateFlow<AuthState> = MutableStateFlow(AuthState.Init)
    val authState = _authState.asStateFlow()

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _currentFirebaseUser = MutableStateFlow<FirebaseUser?>(null)
    val currentFirebaseUser = _currentFirebaseUser.asStateFlow()

    init {
        updateAuthState()
        initializeFirebase()
    }

    private fun initializeFirebase() {
        _currentFirebaseUser.value = firebaseAuth.currentUser
        firebaseAuth.addAuthStateListener { auth ->
            _currentFirebaseUser.value = auth.currentUser
            Log.d(TAG, "Firebase auth state changed: ${auth.currentUser?.email ?: "No user"}")
        }
    }

    fun buildGoogleAuthData(email: String, token: String, tokenType: AuthHelper.Token) {
        _authState.value = AuthState.Fetching
        viewModelScope.launch(Dispatchers.IO) {
            try {
//                if (tokenType == AuthHelper.Token.AUTH) {
//                    val credential = GoogleAuthProvider.getCredential(token, null)
//                    firebaseAuth.signInWithCredential(credential).await()
//                    Log.d(TAG, "Firebase Auth successful with Google")
//                }

                verifyAndSaveAuth(
                    authProvider.buildGoogleAuthData(email, token, tokenType).getOrThrow(),
                    AccountType.GOOGLE
                )
            } catch (exception: Exception) {
                _authState.value =
                    AuthState.Failed(context.getString(R.string.failed_to_generate_session))
                Log.e(TAG, "Failed to generate Session", exception)
            }
        }
    }

    fun buildAnonymousAuthData() {
        _authState.value = AuthState.Fetching
        viewModelScope.launch(Dispatchers.IO) {
            try {
                verifyAndSaveAuth(
                    authProvider.buildAnonymousAuthData().getOrThrow(),
                    AccountType.ANONYMOUS
                )
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to generate Session", exception)
                _authState.value = AuthState.Failed(exception.message.toString())
            }
        }
    }

    fun buildAuthData(context: Context, email: String, oauthToken: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = aC2DMTask.getAC2DMResponse(email, oauthToken)
                if (response.isNotEmpty()) {
                    val aasToken = response["Token"]
                    if (aasToken != null) {
                        Preferences.putString(context, Constants.ACCOUNT_EMAIL_PLAIN, email)
                        Preferences.putString(context, Constants.ACCOUNT_AAS_PLAIN, aasToken)
                        AuroraApp.events.send(AuthEvent.GoogleLogin(true, email, aasToken))
                    } else {
                        Preferences.putString(context, Constants.ACCOUNT_EMAIL_PLAIN, "")
                        Preferences.putString(context, Constants.ACCOUNT_AAS_PLAIN, "")
                        AuroraApp.events.send(AuthEvent.GoogleLogin(false, "", ""))
                    }
                } else {
                    AuroraApp.events.send(AuthEvent.GoogleLogin(false, "", ""))
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to build AuthData", exception)
                AuroraApp.events.send(AuthEvent.GoogleLogin(false, "", ""))
            }
        }
    }

    private fun updateAuthState() {
        if (_authState.value != AuthState.Fetching) {
            if (AccountProvider.isLoggedIn(context)) {
                _authState.value = AuthState.Available
                buildSavedAuthData()
            } else {
                _authState.value = AuthState.Unavailable
            }
        }
    }

    private fun buildSavedAuthData() = viewModelScope.launch(Dispatchers.IO) {
        try {
            if (authProvider.isSavedAuthDataValid()) {
                _authState.value = AuthState.Valid
            } else {
                when (AccountProvider.getAccountType(context)) {
                    AccountType.ANONYMOUS -> buildAnonymousAuthData()
                    AccountType.GOOGLE -> {
                        val email = AccountProvider.getLoginEmail(context)
                        val tokenPair = AccountProvider.getLoginToken(context)

                        if (email == null || tokenPair == null) {
                            throw Exception()
                        }

                        when (tokenPair.second) {
                            AuthHelper.Token.AAS -> {
                                buildGoogleAuthData(email, tokenPair.first, AuthHelper.Token.AAS)
                            }

                            AuthHelper.Token.AUTH -> {
                                _authState.value =
                                    AuthState.PendingAccountManager(email, tokenPair.first)
                            }
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            val error = when (exception) {
                is UnknownHostException -> context.getString(R.string.title_no_network)
                is ConnectException -> context.getString(R.string.server_unreachable)
                else -> context.getString(R.string.bad_request)
            }
            _authState.value = AuthState.Failed(error)
        }
    }

    private fun verifyAndSaveAuth(authData: AuthData, accountType: AccountType) {
        _authState.value = AuthState.Verifying

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!isFirebaseUserSignedIn() && accountType == AccountType.GOOGLE) {
                    try {
//                        val credential = GoogleAuthProvider.getCredential(authData.authToken, null)
//                        firebaseAuth.signInWithCredential(credential).await()
                        Log.d(TAG, "Firebase Auth successful with Google token during verification")
                    } catch (e: Exception) {
                        Log.e(TAG, "Firebase Auth failed during verification", e)
                        logoutUseCase.invoke()
                        _authState.value =
                            AuthState.Failed(context.getString(R.string.failed_to_generate_session))
                        return@launch
                    }
                }

                if (authData.authToken.isNotEmpty() && authData.deviceConfigToken.isNotEmpty()) {
                    authProvider.saveAuthData(authData)
                    AccountProvider.login(
                        context,
                        authData.email,
                        authData.aasToken.ifBlank { authData.authToken },
                        if (authData.aasToken.isBlank()) AuthHelper.Token.AUTH else AuthHelper.Token.AAS,
                        accountType
                    )
                    _authState.value = AuthState.SignedIn
                } else {
                    logoutUseCase.invoke()
                    _authState.value =
                        AuthState.Failed(context.getString(R.string.failed_to_generate_session))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during auth verification process", e)
                logoutUseCase.invoke()
                _authState.value =
                    AuthState.Failed(context.getString(R.string.failed_to_generate_session))
            }
        }
    }

    fun isFirebaseUserSignedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }


    fun onEmailSignIn(uid: String, email: String) {
        viewModelScope.launch {
            val hasEnterpriseAccess = apkRepository.hasEnterpriseAccess(uid)
            if (hasEnterpriseAccess) {
                dataStore.setEnterpriseAccount(uid, email, true)
                buildAnonymousAuthData()
            } else {
                dataStore.setEnterpriseAccount(uid, email, false)
                buildAnonymousAuthData()
            }
        }
    }
}
