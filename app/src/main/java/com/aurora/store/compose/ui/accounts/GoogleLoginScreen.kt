/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.accounts

import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.store.AuroraApp
import com.aurora.store.R
import com.aurora.store.compose.navigation.Destination
import com.aurora.store.data.event.AuthEvent
import com.aurora.store.data.model.AuthState
import com.aurora.store.util.AC2DMUtil
import com.aurora.store.util.Preferences
import com.aurora.store.viewmodel.auth.AuthViewModel
import com.sleke.library.enterprise.presentation.EntryIntent

private const val EMBEDDED_SETUP_URL = "https://accounts.google.com/EmbeddedSetup"
private const val AUTH_TOKEN = "oauth_token"

// Google's EmbeddedSetup post-login page renders the account as e.g.
// <div data-profile-identifier data-email="user@gmail.com">...</div>;
// the legacy id="profileIdentifier" selector no longer matches.
private const val JS_PROFILE_EMAIL = """
    (function() {
        var el = document.querySelector('[data-profile-identifier][data-email]');
        return el ? el.getAttribute('data-email') : null;
    })();
"""

@Composable
fun GoogleLoginScreen(
    onNavigateTo: (Destination) -> Unit,
    addAccount: Boolean = false,
    deepLinkPackageName: String? = null,
    deepLinkDeveloperId: String? = null,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val providerEpoch = remember { viewModel.captureEntryEpoch() }
    val entryNavigation by viewModel.entryNavigation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    var progress by remember { mutableFloatStateOf(0f) }
    var isIndeterminate by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        AuroraApp.events.authEvent.collect { event ->
            if (event is AuthEvent.GoogleLogin) {
                if (event.epoch != viewModel.captureEntryEpoch()) return@collect
                if (event.success) {
                    if (addAccount) {
                        viewModel.addGoogleAuthData(event.email, event.token, event.epoch)
                    } else {
                        viewModel.buildGoogleAuthData(
                            event.email,
                            event.token,
                            AuthHelper.Token.AAS,
                            event.epoch
                        )
                    }
                } else {
                    Toast.makeText(context, R.string.toast_aas_token_failed, Toast.LENGTH_LONG)
                        .show()
                    onNavigateTo(
                        if (addAccount) {
                            Destination.Accounts
                        } else {
                            Destination.Splash(
                                deepLinkPackageName,
                                deepLinkDeveloperId
                            )
                        }
                    )
                }
            }
        }
    }

    // In add-account mode the user is already signed in (authState is Valid), so don't let the
    // authState effect bounce to Main. Navigate back to the account screen once the add completes.
    if (addAccount) {
        LaunchedEffect(Unit) {
            viewModel.accountAdded.collect { ok ->
                if (!ok) {
                    Toast.makeText(context, R.string.toast_aas_token_failed, Toast.LENGTH_LONG)
                        .show()
                }
                onNavigateTo(Destination.Accounts)
            }
        }
    } else {
        LaunchedEffect(authState) {
            when (authState) {
                AuthState.SignedIn, AuthState.Valid -> viewModel.completePlayEntry(
                    EntryIntent(deepLinkPackageName, deepLinkDeveloperId)
                )
                is AuthState.Failed -> onNavigateTo(
                    Destination.Splash(deepLinkPackageName, deepLinkDeveloperId)
                )
                else -> Unit
            }
        }
    }

    LaunchedEffect(entryNavigation?.id) {
        val effect = entryNavigation ?: return@LaunchedEffect
        val (packageName, developerId) = effect.intent
        if (!viewModel.consumeEntryNavigation(effect.id)) return@LaunchedEffect
        when {
            !developerId.isNullOrBlank() -> onNavigateTo(
                Destination.DevProfile(developerId)
            )
            !packageName.isNullOrBlank() -> onNavigateTo(
                Destination.AppDetails(packageName)
            )
            else -> onNavigateTo(
                Destination.Main(
                    Preferences.getInteger(context, Preferences.PREFERENCE_DEFAULT_SELECTED_TAB)
                )
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            if (isIndeterminate) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val cookieManager = CookieManager.getInstance()
                WebView(ctx).apply {
                    cookieManager.removeAllCookies(null)
                    cookieManager.acceptThirdPartyCookies(this)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            if (newProgress != 0) {
                                isIndeterminate = false
                                progress = newProgress / 100f
                                isLoading = newProgress < 100
                            } else {
                                isIndeterminate = true
                                isLoading = true
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            val cookies = CookieManager.getInstance().getCookie(url) ?: return
                            val cookieMap = AC2DMUtil.parseCookieString(cookies)
                            val oauthToken = cookieMap[AUTH_TOKEN] ?: return
                            view.evaluateJavascript(JS_PROFILE_EMAIL) { result ->
                                val email = result.trim('"')
                                viewModel.buildAuthData(
                                    view.context,
                                    email,
                                    oauthToken,
                                    providerEpoch
                                )
                            }
                        }
                    }

                    settings.apply {
                        allowContentAccess = true
                        domStorageEnabled = true
                        javaScriptEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = false
                        }
                    }

                    loadUrl(EMBEDDED_SETUP_URL)
                }
            }
        )
    }
}
