/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aurora.extensions.TAG
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.store.data.model.AccountType
import com.aurora.store.data.providers.AccountProvider
import com.aurora.store.data.providers.AuthProvider
import com.aurora.store.data.providers.GoogleAccountTokenProvider
import com.aurora.store.usecase.LogoutUseCase
import com.aurora.store.util.Preferences
import com.sleke.library.enterprise.data.SessionMutationGate
import com.sleke.library.enterprise.data.SignOutScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Worker to refresh [AuthData] in background
 * @see UpdateWorker
 */
@HiltWorker
open class AuthWorker @AssistedInject constructor(
    private val authProvider: AuthProvider,
    private val tokenProvider: GoogleAccountTokenProvider,
    private val mutationGate: SessionMutationGate,
    private val logoutUseCase: LogoutUseCase,
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val epoch = mutationGate.capture()
        if (!AccountProvider.isLoggedIn(context)) {
            Log.i(TAG, "User has logged out!")
            return Result.failure()
        }

        if (authProvider.isSavedAuthDataValid()) {
            Log.i(TAG, "Saved AuthData is valid")
            return Result.success()
        }

        // Generate and validate new auth
        try {
            val accountType = AccountProvider.getAccountType(context)
            val authData = when (accountType) {
                AccountType.GOOGLE -> {
                    val email = AccountProvider.getLoginEmail(context)
                    val tokenPair = AccountProvider.getLoginToken(context)

                    if (email == null || tokenPair == null) {
                        throw Exception()
                    }

                    when (tokenPair.second) {
                        AuthHelper.Token.AAS -> {
                            Log.i(TAG, "Refreshing AuthData for personal account")
                            authProvider.buildGoogleAuthData(
                                email,
                                tokenPair.first,
                                AuthHelper.Token.AAS
                            ).getOrThrow()
                        }

                        AuthHelper.Token.AUTH -> {
                            Log.i(TAG, "Refreshing AuthData via AccountManager")
                            val newToken = tokenProvider.fetchAuthToken(email, tokenPair.first)
                            authProvider.buildGoogleAuthData(email, newToken, AuthHelper.Token.AAS)
                                .getOrThrow()
                        }
                    }
                }

                AccountType.ANONYMOUS -> {
                    Log.i(TAG, "Refreshing AuthData for anonymous account")
                    authProvider.buildAnonymousAuthData().getOrThrow()
                }
            }

            if (authData.authToken.isEmpty() || authData.deviceConfigToken.isEmpty()) {
                logoutUseCase(SignOutScope.PLAY_RESET)
                return Result.failure()
            }
            val committed = authProvider.persistAccount(
                authData = authData,
                accountType = accountType,
                authViaMicroG = Preferences.getBoolean(
                    context,
                    Preferences.PREFERENCE_AUTH_VIA_MICROG,
                    false
                ),
                makeDefault = true,
                epoch = epoch
            )
            if (!committed) return Result.failure()
            Log.i(TAG, "Refreshed AuthData successfully")
            return Result.success()
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            Log.e(TAG, "Failed to refresh authData!", exception)
            return Result.failure()
        }
    }
}
