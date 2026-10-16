/*
 * SPDX-FileCopyrightText: 2021 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.viewmodel.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.extensions.TAG
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
import com.aurora.store.util.PackageUtil
import com.sleke.integration.auth.EntryIntent
import com.sleke.integration.auth.SavedEntryStateStorage
import com.sleke.integration.auth.SessionMutationGate
import com.sleke.integration.auth.SignOutScope
import com.sleke.integration.auth.SlekeEntryCoordinator
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import com.sleke.library.enterprise.domain.PlaySessionGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    val authProvider: AuthProvider,
    val logoutUseCase: LogoutUseCase,
    @ApplicationContext private val context: Context,
    private val aC2DMTask: AC2DMTask,
    private val mutationGate: SessionMutationGate,
    session: EnterpriseSessionRepository,
    play: PlaySessionGateway,
    savedState: SavedStateHandle
) : ViewModel() {

    private val _authState: MutableStateFlow<AuthState> = MutableStateFlow(AuthState.Init)
    val authState = _authState.asStateFlow()

    private val entryCoordinator =
        SlekeEntryCoordinator(session, play, mutationGate, SavedEntryStateStorage(savedState))
    val entryNavigation = entryCoordinator.navigation
    val emailBusy = entryCoordinator.busy
    private var successfulPlayEpoch: Long? = null
    fun captureEntryEpoch(): Long = mutationGate.capture()
    fun consumeEntryNavigation(id: Long) = entryCoordinator.consumeNavigation(id)
    fun rememberEntryIntent(intent: EntryIntent) = entryCoordinator.rememberIntent(intent)
    fun onEmailCancelled() = entryCoordinator.providerCancelled()
    fun onEmailFailure() {
        _authState.value =
            AuthState.Failed(context.getString(R.string.failed_to_generate_session))
    }
    fun onEmailSignIn(intent: EntryIntent) {
        viewModelScope.launch {
            entryCoordinator.completeEmailEntry(intent)
            if (entryCoordinator.failure.value != null) onEmailFailure()
        }
    }
    fun completePlayEntry(intent: EntryIntent) {
        val epoch = successfulPlayEpoch ?: return
        viewModelScope.launch { entryCoordinator.completePlayEntry(intent, epoch) }
    }

    /** Emits the outcome of adding a Google account (non-default) so the caller can navigate back. */
    private val _accountAdded = MutableSharedFlow<Boolean>()
    val accountAdded = _accountAdded.asSharedFlow()

    init {
        updateAuthState()
    }

    fun buildGoogleAuthData(
        email: String,
        token: String,
        tokenType: AuthHelper.Token,
        epoch: Long = mutationGate.capture()
    ) {
        if (mutationGate.capture() != epoch) return
        _authState.value = AuthState.Fetching
        viewModelScope.launch(Dispatchers.IO) {
            try {
                verifyAndSaveAuth(
                    authProvider.buildGoogleAuthData(email, token, tokenType).getOrThrow(),
                    AccountType.GOOGLE,
                    epoch
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _authState.value =
                    AuthState.Failed(context.getString(R.string.failed_to_generate_session))
                Log.e(TAG, "Failed to generate Session", exception)
            }
        }
    }

    /**
     * Adds a Google account WITHOUT changing the active default (used by the account screen's
     * "add account" flow). Does not touch [authState], so the current session/UI is unaffected.
     * Mirrors the existing default afterwards so adding an account preserves the active session.
     */
    fun addGoogleAuthData(email: String, token: String, epoch: Long = mutationGate.capture()) {
        if (mutationGate.capture() != epoch) return
        viewModelScope.launch(Dispatchers.IO) {
            val added = runCatching {
                val authData = authProvider
                    .buildGoogleAuthData(email, token, AuthHelper.Token.AAS)
                    .getOrThrow()
                require(authData.authToken.isNotEmpty() && authData.deviceConfigToken.isNotEmpty())
                authProvider.persistAccount(
                    authData = authData,
                    accountType = AccountType.GOOGLE,
                    authViaMicroG = false,
                    makeDefault = false,
                    epoch = epoch
                )
            }.onFailure { if (it is CancellationException) throw it }.getOrDefault(false)
            authProvider.syncDefaultToPrefs()
            _accountAdded.emit(added)
        }
    }

    fun buildAnonymousAuthData(epoch: Long = mutationGate.capture()) {
        if (mutationGate.capture() != epoch) return
        _authState.value = AuthState.Fetching
        viewModelScope.launch(Dispatchers.IO) {
            try {
                verifyAndSaveAuth(
                    authProvider.buildAnonymousAuthData().getOrThrow(),
                    AccountType.ANONYMOUS,
                    epoch
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e(TAG, "Failed to generate Session", exception)
                val message = when (exception) {
                    is UnknownHostException -> context.getString(R.string.check_connectivity)
                    else -> exception.message.toString()
                }

                _authState.value = AuthState.Failed(message)
            }
        }
    }

    fun buildAuthData(
        context: Context,
        email: String,
        oauthToken: String,
        epoch: Long = mutationGate.capture()
    ) {
        if (mutationGate.capture() != epoch) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = aC2DMTask.getAC2DMResponse(email, oauthToken)
                if (response.isNotEmpty()) {
                    val aasToken = response["Token"]
                    if (aasToken != null) {
                        val accountEmail = response["Email"]?.takeIf { it.isNotBlank() } ?: email
                        mutationGate.commitIfCurrent(epoch) {
                            AuroraApp.events.send(
                                AuthEvent.GoogleLogin(true, accountEmail, aasToken, epoch)
                            )
                        }
                    } else {
                        AuroraApp.events.send(AuthEvent.GoogleLogin(false, "", "", epoch))
                    }
                } else {
                    AuroraApp.events.send(AuthEvent.GoogleLogin(false, "", "", epoch))
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e(TAG, "Failed to build AuthData", exception)
                AuroraApp.events.send(AuthEvent.GoogleLogin(false, "", "", epoch))
            }
        }
    }

    fun retry() = updateAuthState()

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

    private fun buildSavedAuthData() {
        val epoch = mutationGate.capture()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (authProvider.isSavedAuthDataValid()) {
                    mutationGate.commitIfCurrent(epoch) {
                        successfulPlayEpoch = epoch
                        _authState.value =
                            AuthState.Valid
                    }
                } else {
                    when (AccountProvider.getAccountType(context)) {
                        AccountType.ANONYMOUS -> buildAnonymousAuthData(epoch)

                        AccountType.GOOGLE -> {
                            val email = AccountProvider.getLoginEmail(context)
                            val tokenPair = AccountProvider.getLoginToken(context)

                            if (email == null || tokenPair == null) {
                                throw Exception()
                            }

                            when (tokenPair.second) {
                                AuthHelper.Token.AAS -> {
                                    buildGoogleAuthData(
                                        email,
                                        tokenPair.first,
                                        AuthHelper.Token.AAS,
                                        epoch
                                    )
                                }

                                AuthHelper.Token.AUTH -> {
                                    mutationGate.commitIfCurrent(epoch) {
                                        _authState.value =
                                            AuthState.PendingAccountManager(
                                                email,
                                                tokenPair.first,
                                                epoch
                                            )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                val error = when (exception) {
                    is UnknownHostException -> context.getString(R.string.title_no_network)
                    is ConnectException -> context.getString(R.string.server_unreachable)
                    else -> context.getString(R.string.bad_request)
                }
                mutationGate.commitIfCurrent(epoch) { _authState.value = AuthState.Failed(error) }
            }
        }
    }

    private suspend fun verifyAndSaveAuth(
        authData: AuthData,
        accountType: AccountType,
        epoch: Long
    ) {
        if (mutationGate.capture() != epoch) return
        _authState.value = AuthState.Verifying
        if (authData.authToken.isEmpty() || authData.deviceConfigToken.isEmpty()) {
            logoutUseCase(SignOutScope.PLAY_RESET)
            _authState.value =
                AuthState.Failed(context.getString(R.string.failed_to_generate_session))
            return
        }
        val tokenType = if (authData.aasToken.isBlank()) {
            AuthHelper.Token.AUTH
        } else {
            AuthHelper.Token.AAS
        }
        val stored = authProvider.persistAccount(
            authData,
            accountType,
            accountType == AccountType.GOOGLE &&
                tokenType == AuthHelper.Token.AUTH &&
                PackageUtil.hasSupportedMicroGVariant(context),
            makeDefault = true,
            epoch = epoch
        )
        if (stored) {
            mutationGate.commitIfCurrent(epoch) {
                successfulPlayEpoch = epoch
                _authState.value =
                    AuthState.SignedIn
            }
        }
    }
}
