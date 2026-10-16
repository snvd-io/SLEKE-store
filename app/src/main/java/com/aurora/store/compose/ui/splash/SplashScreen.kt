/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.splash

import android.accounts.Account
import android.accounts.AccountManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.Constants.PACKAGE_NAME_PLAY_STORE
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.store.BuildConfig
import com.aurora.store.R
import com.aurora.store.compose.composition.LocalNetworkStatus
import com.aurora.store.compose.navigation.Destination
import com.aurora.store.data.model.AuthState
import com.aurora.store.data.model.NetworkStatus
import com.aurora.store.data.work.ExodusTrackerWorker
import com.aurora.store.util.CertUtil.GOOGLE_ACCOUNT_TYPE
import com.aurora.store.util.CertUtil.GOOGLE_PLAY_AUTH_TOKEN_TYPE
import com.aurora.store.util.CertUtil.GOOGLE_PLAY_CERT
import com.aurora.store.util.PackageUtil
import com.aurora.store.util.Preferences
import com.aurora.store.viewmodel.auth.AuthViewModel
import com.sleke.integration.auth.EntryIntent
import com.sleke.library.enterprise.presentation.EmailEntryResult
import com.sleke.library.enterprise.presentation.EnterpriseEmailEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplashScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    deepLinkPackageName: String? = null,
    deepLinkDevId: String? = null,
    onNavigateTo: (Destination) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity

    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val entryNavigation by viewModel.entryNavigation.collectAsStateWithLifecycle()
    val emailBusy by viewModel.emailBusy.collectAsStateWithLifecycle()
    val entryIntent =
        remember(deepLinkPackageName, deepLinkDevId) {
            EntryIntent(deepLinkPackageName, deepLinkDevId)
        }
    val isOnline = LocalNetworkStatus.current == NetworkStatus.AVAILABLE

    val canMicroGLogin = PackageUtil.hasSupportedMicroGVariant(context) &&
        Preferences.getBoolean(context, Preferences.PREFERENCE_MICROG_AUTH, true)

    var anonymousLoading by remember { mutableStateOf(false) }
    var googleLoading by remember { mutableStateOf(false) }
    var emailLoading by remember { mutableStateOf(false) }
    var googleProviderEpoch by rememberSaveable { mutableStateOf(viewModel.captureEntryEpoch()) }

    val accountLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (!accountName.isNullOrBlank()) {
            requestAuthTokenForGoogle(
                viewModel = viewModel,
                context = context,
                accountName = accountName,
                oldToken = null,
                epoch = googleProviderEpoch,
                activity = activity,
                onError = { googleLoading = false }
            )
        } else {
            googleLoading = false
        }
    }

    LaunchedEffect(entryNavigation?.id) {
        val effect = entryNavigation ?: return@LaunchedEffect
        if (!viewModel.consumeEntryNavigation(effect.id)) return@LaunchedEffect
        ExodusTrackerWorker.enqueue(context)
        when {
            !effect.intent.developerId.isNullOrBlank() -> onNavigateTo(
                Destination.DevProfile(effect.intent.developerId)
            )
            !effect.intent.packageName.isNullOrBlank() -> onNavigateTo(
                Destination.AppDetails(effect.intent.packageName)
            )
            else -> onNavigateTo(
                Destination.Main(
                    Preferences.getInteger(context, Preferences.PREFERENCE_DEFAULT_SELECTED_TAB)
                )
            )
        }
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            AuthState.Valid, AuthState.SignedIn -> {
                anonymousLoading = false
                googleLoading = false
                emailLoading = false
                if (!emailBusy) viewModel.completePlayEntry(entryIntent)
            }
            is AuthState.PendingAccountManager -> requestAuthTokenForGoogle(
                viewModel = viewModel,
                context = context,
                accountName = state.email,
                oldToken = state.token,
                epoch = state.epoch,
                activity = activity,
                onError = { googleLoading = false }
            )
            is AuthState.Failed -> {
                anonymousLoading = false
                googleLoading = false
                emailLoading = false
            }
            else -> Unit
        }
    }

    LaunchedEffect(isOnline) {
        if (isOnline && authState is AuthState.Failed) {
            viewModel.retry()
        }
    }

    val isLoading = emailBusy ||
        authState == AuthState.Init ||
        authState == AuthState.Available ||
        authState == AuthState.Fetching ||
        authState == AuthState.Verifying ||
        authState is AuthState.PendingAccountManager

    val showLoginButtons = authState is AuthState.Unavailable ||
        authState is AuthState.Failed ||
        authState is AuthState.SignedOut

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { onNavigateTo(Destination.Settings) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu_settings),
                            contentDescription = stringResource(R.string.title_settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.50f),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(108.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.1f)
                    .padding(horizontal = dimensionResource(R.dimen.spacing_xlarge)),
                contentAlignment = Alignment.Center
            ) {
                val (statusText, statusColor) = when {
                    !isOnline && showLoginButtons -> stringResource(R.string.title_no_network) to
                        MaterialTheme.colorScheme.error
                    authState is AuthState.Failed -> (authState as AuthState.Failed).status to
                        MaterialTheme.colorScheme.error
                    else -> statusText(authState) to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                    textAlign = TextAlign.Center
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.40f),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else if (showLoginButtons) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            dimensionResource(R.dimen.spacing_small)
                        )
                    ) {
                        Button(
                            modifier = Modifier.width(dimensionResource(R.dimen.width_button)),
                            enabled =
                            !anonymousLoading && !googleLoading && !emailLoading && isOnline,
                            onClick = {
                                if (canMicroGLogin) {
                                    googleLoading = true
                                    googleProviderEpoch = viewModel.captureEntryEpoch()
                                    accountLauncher.launch(
                                        AccountManager.newChooseAccountIntent(
                                            null,
                                            null,
                                            arrayOf(GOOGLE_ACCOUNT_TYPE),
                                            null,
                                            null,
                                            null,
                                            null
                                        )
                                    )
                                } else {
                                    onNavigateTo(
                                        Destination.GoogleLogin(
                                            packageName = deepLinkPackageName,
                                            developerId = deepLinkDevId
                                        )
                                    )
                                }
                            }
                        ) {
                            Text(stringResource(R.string.account_google))
                        }
                        if (BuildConfig.SHOW_ANONYMOUS_LOGIN) {
                            OutlinedButton(
                                modifier = Modifier.width(dimensionResource(R.dimen.width_button)),
                                enabled =
                                !googleLoading && !anonymousLoading && !emailLoading && isOnline,
                                onClick = {
                                    anonymousLoading = true
                                    viewModel.buildAnonymousAuthData()
                                }
                            ) {
                                Text(stringResource(R.string.account_anonymous))
                            }
                        }
                        EnterpriseEmailEntry(
                            modifier = Modifier.width(dimensionResource(R.dimen.width_button)),
                            enabled =
                            !googleLoading && !anonymousLoading && !emailLoading && isOnline,
                            onStarted = {
                                emailLoading = true
                                viewModel.rememberEntryIntent(entryIntent)
                            },
                            onResult = { result ->
                                emailLoading = false
                                when (result) {
                                    EmailEntryResult.Completed -> viewModel.onEmailSignIn(
                                        entryIntent
                                    )
                                    EmailEntryResult.Cancelled -> viewModel.onEmailCancelled()
                                    is EmailEntryResult.Failed -> viewModel.onEmailFailure()
                                }
                            }
                        )
                        if (googleLoading || anonymousLoading || emailLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun statusText(authState: AuthState): String = when (authState) {
    AuthState.Init -> stringResource(R.string.session_init)
    AuthState.Available -> stringResource(R.string.session_verifying)
    AuthState.Fetching -> stringResource(R.string.requesting_new_session)
    AuthState.Verifying -> stringResource(R.string.verifying_new_session)
    AuthState.Valid -> stringResource(R.string.session_verifying)
    AuthState.SignedIn -> stringResource(R.string.session_good)
    AuthState.Unavailable, AuthState.SignedOut -> stringResource(R.string.session_login)
    is AuthState.PendingAccountManager -> stringResource(R.string.requesting_new_session)
    is AuthState.Failed -> authState.status
}

private fun requestAuthTokenForGoogle(
    viewModel: AuthViewModel,
    context: android.content.Context,
    accountName: String,
    oldToken: String?,
    epoch: Long,
    activity: ComponentActivity?,
    onError: () -> Unit
) {
    try {
        val accountManager = AccountManager.get(context)
        if (oldToken != null) {
            accountManager.invalidateAuthToken(GOOGLE_ACCOUNT_TYPE, oldToken)
        }
        accountManager.getAuthToken(
            Account(accountName, GOOGLE_ACCOUNT_TYPE),
            GOOGLE_PLAY_AUTH_TOKEN_TYPE,
            Bundle().apply {
                putString("overridePackage", PACKAGE_NAME_PLAY_STORE)
                putByteArray("overrideCertificate", Base64.decode(GOOGLE_PLAY_CERT, Base64.DEFAULT))
            },
            activity,
            { result ->
                val token = result.result.getString(AccountManager.KEY_AUTHTOKEN)
                if (token != null) {
                    viewModel.buildGoogleAuthData(accountName, token, AuthHelper.Token.AUTH, epoch)
                } else {
                    Log.e("SplashScreen", "AccountManager returned null auth token")
                    onError()
                }
            },
            Handler(Looper.getMainLooper())
        )
    } catch (_: Exception) {
        Log.e("SplashScreen", "Failed to get authToken for Google login")
        onError()
    }
}
