package com.sleke.integration.auth

import com.sleke.library.downloads.domain.CustomDownloadCoordinator
import com.sleke.library.enterprise.domain.CleanupFailure
import com.sleke.library.enterprise.domain.CleanupReport
import com.sleke.library.enterprise.domain.CleanupStage
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import com.sleke.library.enterprise.domain.PlaySessionGateway
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

enum class SignOutScope { ENTERPRISE, PLAY_RESET, FULL }

class SignOutCoordinator(
    private val enterprise: EnterpriseSessionRepository,
    private val play: PlaySessionGateway,
    private val gate: SessionMutationGate,
    private val cancelEnterpriseWork: suspend () -> Unit
) {
    @Inject constructor(
        enterprise: EnterpriseSessionRepository,
        play: PlaySessionGateway,
        gate: SessionMutationGate,
        downloads: CustomDownloadCoordinator
    ) :
        this(enterprise, play, gate, { downloads.cancelEnterpriseWork() })

    suspend fun signOut(scope: SignOutScope): CleanupReport {
        var report = CleanupReport()
        suspend fun attempt(stage: CleanupStage, operation: suspend () -> CleanupReport) {
            try {
                report += operation()
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (failure: Exception) {
                report +=
                    CleanupReport(listOf(CleanupFailure(stage, failure)))
            }
        }
        if (scope != SignOutScope.PLAY_RESET) {
            attempt(CleanupStage.ENTERPRISE) {
                enterprise.revokeAccess()
                CleanupReport()
            }
        }
        attempt(
            if (scope ==
                SignOutScope.ENTERPRISE
            ) {
                CleanupStage.ENTERPRISE
            } else {
                CleanupStage.PLAY_CACHE
            }
        ) {
            gate.invalidate()
            CleanupReport()
        }
        // An accepted write may already be suspended. It finishes before cleanup, never after it.
        gate.cleanup {
            if (scope != SignOutScope.PLAY_RESET) {
                attempt(CleanupStage.ENTERPRISE) {
                    cancelEnterpriseWork()
                    CleanupReport()
                }
                attempt(CleanupStage.ENTERPRISE) { enterprise.signOut() }
            }
            if (scope !=
                SignOutScope.ENTERPRISE
            ) {
                attempt(CleanupStage.PLAY_CACHE) { play.resetCachedAuth() }
            }
            if (scope ==
                SignOutScope.FULL
            ) {
                attempt(CleanupStage.PLAY_ACCOUNTS) { play.removeAllAccounts() }
            }
        }
        return report
    }
}
