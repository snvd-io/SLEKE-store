package com.sleke.library.enterprise.data

import com.sleke.library.enterprise.domain.CleanupReport
import com.sleke.library.enterprise.domain.CleanupStage
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import com.sleke.library.enterprise.domain.PlayPreparation
import com.sleke.library.enterprise.domain.PlaySessionGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignOutCoordinatorTest {
    @Test fun fullCleanupAttemptsEveryStageAfterEachOrdinaryFailure() = runTest {
        CleanupStage.entries.forEach { failed ->
            val attempts = mutableListOf<CleanupStage>()
            suspend fun stage(stage: CleanupStage): CleanupReport {
                attempts += stage
                if (stage == failed) throw IllegalStateException("failed")
                return CleanupReport()
            }
            val sessions = object : EnterpriseSessionRepository {
                override val state =
                    MutableStateFlow<EnterpriseSessionState>(EnterpriseSessionState.SignedOut)
                override suspend fun revokeAccess() {
                    state.value = EnterpriseSessionState.SignedOut
                }
                override suspend fun refresh() = state.value
                override suspend fun signOut() = stage(CleanupStage.ENTERPRISE)
            }
            val play = object : PlaySessionGateway {
                override suspend fun prepareAnonymous() = PlayPreparation.Ready
                override suspend fun resetCachedAuth() = stage(CleanupStage.PLAY_CACHE)
                override suspend fun removeAllAccounts() = stage(CleanupStage.PLAY_ACCOUNTS)
            }
            val result = SignOutCoordinator(
                sessions,
                play,
                SessionMutationGate(MemorySessionEpochStorage())
            ) {
            }.signOut(SignOutScope.FULL)
            assertEquals(CleanupStage.entries, attempts)
            assertEquals(listOf(failed), result.failures.map { it.stage })
        }
    }

    @Test fun epochWriteFailureStillAttemptsEverySignOutStage() = runTest {
        val epochs = MemorySessionEpochStorage()
        val gate = SessionMutationGate(epochs)
        val calls = mutableListOf<String>()
        val session = object : EnterpriseSessionRepository {
            override val state =
                MutableStateFlow<EnterpriseSessionState>(EnterpriseSessionState.SignedOut)
            override suspend fun revokeAccess() {
                calls += "revoke"
            }
            override suspend fun refresh() = state.value
            override suspend fun signOut(): CleanupReport {
                calls += "firebase"
                return CleanupReport()
            }
        }
        val play = object : PlaySessionGateway {
            override suspend fun prepareAnonymous() = PlayPreparation.Ready
            override suspend fun resetCachedAuth(): CleanupReport {
                calls += "cache"
                return CleanupReport()
            }
            override suspend fun removeAllAccounts(): CleanupReport {
                calls += "accounts"
                return CleanupReport()
            }
        }
        epochs.failAdvance = true
        val result = SignOutCoordinator(session, play, gate) {
            calls += "downloads"
        }.signOut(SignOutScope.FULL)
        assertEquals(listOf("revoke", "downloads", "firebase", "cache", "accounts"), calls)
        assertEquals(listOf(CleanupStage.PLAY_CACHE), result.failures.map { it.stage })
    }

    @Test fun concurrentResetCannotSkipFullAccountCleanup() = runTest {
        val gate = SessionMutationGate(MemorySessionEpochStorage())
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val epoch = gate.capture()
        val write =
            launch {
                gate.commitIfCurrent(epoch) {
                    started.complete(Unit)
                    release.await()
                }
            }
        started.await()
        val session = object : EnterpriseSessionRepository {
            override val state =
                MutableStateFlow<EnterpriseSessionState>(EnterpriseSessionState.SignedOut)
            override suspend fun revokeAccess() {
                state.value = EnterpriseSessionState.SignedOut
            }
            override suspend fun refresh() = state.value
            override suspend fun signOut(): CleanupReport {
                calls += "enterprise"
                return CleanupReport()
            }
        }
        val play = object : PlaySessionGateway {
            override suspend fun prepareAnonymous() = PlayPreparation.Ready
            override suspend fun resetCachedAuth(): CleanupReport {
                calls += "cache"
                return CleanupReport()
            }
            override suspend fun removeAllAccounts(): CleanupReport {
                calls += "accounts"
                return CleanupReport()
            }
        }
        val coordinator = SignOutCoordinator(session, play, gate) {}
        val full = launch { coordinator.signOut(SignOutScope.FULL) }
        runCurrent()
        val reset = launch { coordinator.signOut(SignOutScope.PLAY_RESET) }
        runCurrent()
        release.complete(Unit)
        write.join()
        full.join()
        reset.join()
        assertEquals(1, calls.count { it == "accounts" })
    }

    @Test fun privateAuthorityIsRevokedBeforeSlowPlatformCancellation() = runTest {
        val platform = CompletableDeferred<Unit>()
        var firebaseSignOuts = 0
        val session = object : EnterpriseSessionRepository {
            override val state =
                MutableStateFlow<EnterpriseSessionState>(
                    EnterpriseSessionState.Allowed(
                        com.sleke.library.enterprise.domain.EnterpriseIdentity("current"),
                        "enterprise",
                        "Company"
                    )
                )
            override suspend fun revokeAccess() {
                state.value = EnterpriseSessionState.SignedOut
            }
            override suspend fun refresh() = state.value
            override suspend fun signOut(): CleanupReport {
                firebaseSignOuts++
                return CleanupReport()
            }
        }
        val play = object : PlaySessionGateway {
            override suspend fun prepareAnonymous() = PlayPreparation.Ready
            override suspend fun resetCachedAuth() = CleanupReport()
            override suspend fun removeAllAccounts() = CleanupReport()
        }
        val coordinator =
            SignOutCoordinator(session, play, SessionMutationGate(MemorySessionEpochStorage())) {
                platform.await()
            }
        val signingOut = launch { coordinator.signOut(SignOutScope.FULL) }
        runCurrent()
        assertEquals(EnterpriseSessionState.SignedOut, session.state.value)
        assertEquals(0, firebaseSignOuts)
        platform.complete(Unit)
        signingOut.join()
        assertEquals(1, firebaseSignOuts)
    }

    @Test fun privateWorkIsInvalidatedBeforeFirebaseEvenWhenCancellationReportsFailure() = runTest {
        val calls = mutableListOf<String>()
        val session = object : EnterpriseSessionRepository {
            override val state =
                MutableStateFlow<EnterpriseSessionState>(EnterpriseSessionState.SignedOut)
            override suspend fun revokeAccess() {
                state.value = EnterpriseSessionState.SignedOut
            }
            override suspend fun refresh() = state.value
            override suspend fun signOut(): CleanupReport {
                calls += "firebase"
                return CleanupReport()
            }
        }
        val play = object : PlaySessionGateway {
            override suspend fun prepareAnonymous() = PlayPreparation.Ready
            override suspend fun resetCachedAuth(): CleanupReport {
                calls += "cache"
                return CleanupReport()
            }
            override suspend fun removeAllAccounts(): CleanupReport {
                calls += "accounts"
                return CleanupReport()
            }
        }
        val coordinator =
            SignOutCoordinator(session, play, SessionMutationGate(MemorySessionEpochStorage())) {
                calls += "invalidatePrivateWork"
                throw IllegalStateException("platform cancellation failed")
            }
        val result = coordinator.signOut(SignOutScope.FULL)
        assertEquals(listOf("invalidatePrivateWork", "firebase", "cache", "accounts"), calls)
        assertEquals(listOf(CleanupStage.ENTERPRISE), result.failures.map { it.stage })
    }

    @Test fun scopedCleanupPreservesOtherIdentity() = runTest {
        val calls = mutableListOf<String>()
        val sessions = object : EnterpriseSessionRepository {
            override val state =
                MutableStateFlow<EnterpriseSessionState>(EnterpriseSessionState.SignedOut)
            override suspend fun revokeAccess() {
                state.value = EnterpriseSessionState.SignedOut
            }
            override suspend fun refresh() = state.value
            override suspend fun signOut(): CleanupReport {
                calls += "enterprise"
                return CleanupReport()
            }
        }
        val play = object : PlaySessionGateway {
            override suspend fun prepareAnonymous() = PlayPreparation.Ready
            override suspend fun resetCachedAuth(): CleanupReport {
                calls += "cache"
                return CleanupReport()
            }
            override suspend fun removeAllAccounts(): CleanupReport {
                calls += "accounts"
                return CleanupReport()
            }
        }
        val coordinator =
            SignOutCoordinator(sessions, play, SessionMutationGate(MemorySessionEpochStorage())) {
                calls +=
                    "downloads"
            }
        coordinator.signOut(SignOutScope.PLAY_RESET)
        assertEquals(listOf("cache"), calls)
        calls.clear()
        coordinator.signOut(SignOutScope.ENTERPRISE)
        assertEquals(listOf("downloads", "enterprise"), calls)
    }
}
