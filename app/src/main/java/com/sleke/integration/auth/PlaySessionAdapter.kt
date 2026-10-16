package com.sleke.integration.auth

import android.content.Context
import com.aurora.store.data.AccountRepository
import com.aurora.store.data.model.AccountType
import com.aurora.store.data.providers.AccountProvider
import com.aurora.store.data.providers.AuthProvider
import com.sleke.library.enterprise.data.SessionMutationGate
import com.sleke.library.enterprise.domain.CleanupFailure
import com.sleke.library.enterprise.domain.CleanupReport
import com.sleke.library.enterprise.domain.CleanupStage
import com.sleke.library.enterprise.domain.PlayPreparation
import com.sleke.library.enterprise.domain.PlaySessionGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaySessionAdapter @Inject constructor(
    private val auth: AuthProvider,
    private val accounts: AccountRepository,
    private val gate: SessionMutationGate,
    @ApplicationContext private val context: Context
) : PlaySessionGateway {
    override suspend fun prepareAnonymous(): PlayPreparation {
        val epoch = gate.captureAttempt()
        return withContext(Dispatchers.IO) {
            try {
                val data = auth.buildAnonymousAuthData().getOrThrow()
                require(data.authToken.isNotEmpty() && data.deviceConfigToken.isNotEmpty())
                if (auth.persistAccount(data, AccountType.ANONYMOUS, false, true, epoch)) {
                    PlayPreparation.Ready
                } else {
                    PlayPreparation.Failed(
                        IllegalStateException("Session preparation was superseded")
                    )
                }
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (failure: Exception) {
                PlayPreparation.Failed(failure)
            }
        }
    }

    override suspend fun resetCachedAuth(): CleanupReport = withContext(Dispatchers.IO) {
        var report = CleanupReport()
        suspend fun attempt(block: suspend () -> Unit) {
            try {
                block()
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (failure: Exception) {
                report +=
                    CleanupReport(listOf(CleanupFailure(CleanupStage.PLAY_CACHE, failure)))
            }
        }
        attempt { auth.removeAuthData(context) }
        attempt { AccountProvider.logout(context) }
        // Preserve tokens, account records and bindings, but never reuse a stale device session.
        attempt {
            accounts.getAll().forEach { accounts.upsertAccount(it.copy(authDataJson = null)) }
        }
        report
    }

    override suspend fun removeAllAccounts(): CleanupReport = withContext(Dispatchers.IO) {
        var report = CleanupReport()
        val all = accounts.getAll()
        all.forEach { account ->
            try {
                accounts.removeAccount(account.id)
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (failure: Exception) {
                report +=
                    CleanupReport(listOf(CleanupFailure(CleanupStage.PLAY_ACCOUNTS, failure)))
            }
        }
        report
    }
}
