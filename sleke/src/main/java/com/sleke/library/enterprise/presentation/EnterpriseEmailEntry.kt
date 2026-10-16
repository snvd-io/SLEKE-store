package com.sleke.library.enterprise.presentation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.google.firebase.auth.FirebaseAuth
import com.sleke.library.R

sealed interface EmailEntryResult {
    data object Completed : EmailEntryResult
    data object Cancelled : EmailEntryResult
    data class Failed(val cause: Throwable) : EmailEntryResult
}

/** FirebaseUI and its branding stay inside the feature boundary. */
@Composable
fun EnterpriseEmailEntry(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onStarted: () -> Unit = {},
    onResult: (EmailEntryResult) -> Unit
) {
    val launcher =
        rememberLauncherForActivityResult(FirebaseAuthUIActivityResultContract()) { result ->
            val outcome = when {
                result.resultCode == Activity.RESULT_OK &&
                    FirebaseAuth.getInstance().currentUser != null -> EmailEntryResult.Completed
                result.idpResponse?.error != null -> EmailEntryResult.Failed(
                    result.idpResponse!!.error!!
                )
                result.resultCode == Activity.RESULT_OK -> EmailEntryResult.Failed(
                    IllegalStateException("No authenticated enterprise identity")
                )
                else -> EmailEntryResult.Cancelled
            }
            onResult(outcome)
        }
    OutlinedButton(modifier = modifier, enabled = enabled, onClick = {
        onStarted()
        launcher.launch(
            AuthUI.getInstance().createSignInIntentBuilder()
                .setTheme(R.style.Sleke_EnterpriseEmailTheme)
                .setLogo(R.drawable.sleke_login_logo)
                .setAvailableProviders(listOf(AuthUI.IdpConfig.EmailBuilder().build()))
                .build()
        )
    }) { Text(stringResource(R.string.sleke_enterprise_email)) }
}

@Preview
@Composable
private fun EnterpriseEmailEntryPreview() {
    MaterialTheme { EnterpriseEmailEntry(onResult = {}) }
}
