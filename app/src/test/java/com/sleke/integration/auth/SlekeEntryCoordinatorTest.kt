package com.sleke.integration.auth

import com.sleke.library.enterprise.domain.CleanupReport
import com.sleke.library.enterprise.domain.EnterpriseIdentity
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import com.sleke.library.enterprise.domain.EntryDestination
import com.sleke.library.enterprise.domain.PlayPreparation
import com.sleke.library.enterprise.domain.PlaySessionGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SlekeEntryCoordinatorTest {
    @Test fun entryWaitsForPlayAndRetainsOriginalIntent() = runTest {
        val play = Play()
        val storage = MemoryEntryStateStorage()
        val coordinator =
            SlekeEntryCoordinator(
                Session(),
                play,
                SessionMutationGate(MemorySessionEpochStorage()),
                storage
            )
        val intent = EntryIntent("com.example.target", "developer")
        val entry = launch { coordinator.completeEmailEntry(intent) }
        runCurrent()
        assertNull(coordinator.navigation.value)
        play.result.complete(PlayPreparation.Ready)
        entry.join()
        assertEquals(intent, coordinator.navigation.value?.intent)
        assertEquals(EntryDestination.RegularCatalog, coordinator.navigation.value?.destination)
        val id = coordinator.navigation.value!!.id
        coordinator.consumeNavigation(id)
        val restored =
            SlekeEntryCoordinator(
                Session(),
                play,
                SessionMutationGate(MemorySessionEpochStorage()),
                storage
            )
        assertNull(restored.navigation.value)
    }

    @Test fun fullSignOutInvalidatesDelayedPlayCompletion() = runTest {
        val play = Play()
        val gate = SessionMutationGate(MemorySessionEpochStorage())
        val session = Session()
        val coordinator = SlekeEntryCoordinator(session, play, gate, MemoryEntryStateStorage())
        val entry = launch { coordinator.completeEmailEntry(EntryIntent(null, null)) }
        runCurrent()
        SignOutCoordinator(session, play, gate) {}.signOut(SignOutScope.FULL)
        play.result.complete(PlayPreparation.Ready)
        entry.join()
        assertNull(coordinator.navigation.value)
        assertFalse(coordinator.busy.value)
    }

    @Test fun providerCancellationDoesNotClearExistingSession() = runTest {
        val session = Session()
        val coordinator =
            SlekeEntryCoordinator(
                session,
                Play(),
                SessionMutationGate(MemorySessionEpochStorage()),
                MemoryEntryStateStorage()
            )
        coordinator.providerCancelled()
        assertEquals(0, session.signOuts)
        assertFalse(coordinator.busy.value)
    }

    @Test fun signOutDiscardsAnAlreadyPendingNavigationEffect() = runTest {
        val gate = SessionMutationGate(MemorySessionEpochStorage())
        val session = Session()
        val play = Play().apply { result.complete(PlayPreparation.Ready) }
        val coordinator = SlekeEntryCoordinator(session, play, gate, MemoryEntryStateStorage())
        coordinator.completeEmailEntry(EntryIntent(null, null))
        val pending = coordinator.navigation.value!!
        SignOutCoordinator(session, play, gate) {}.signOut(SignOutScope.FULL)
        assertEquals(false, coordinator.consumeNavigation(pending.id))
        assertNull(coordinator.navigation.value)
    }

    @Test fun lateMembershipAfterFullSignOutCannotStartPlayBootstrap() = runTest {
        val gate = SessionMutationGate(MemorySessionEpochStorage())
        val membership = CompletableDeferred<EnterpriseSessionState>()
        var preparations = 0
        val session = object : EnterpriseSessionRepository {
            override val state =
                MutableStateFlow<EnterpriseSessionState>(
                    EnterpriseSessionState.Unavailable(EnterpriseIdentity("current"))
                )
            override suspend fun revokeAccess() {
                state.value = EnterpriseSessionState.SignedOut
            }
            override suspend fun refresh() = membership.await()
            override suspend fun signOut(): CleanupReport {
                state.value =
                    EnterpriseSessionState.SignedOut
                return CleanupReport()
            }
        }
        val play = object : PlaySessionGateway {
            override suspend fun prepareAnonymous(): PlayPreparation {
                preparations++
                return PlayPreparation.Ready
            }
            override suspend fun resetCachedAuth() = CleanupReport()
            override suspend fun removeAllAccounts() = CleanupReport()
        }
        val coordinator = SlekeEntryCoordinator(session, play, gate, MemoryEntryStateStorage())
        val entry = launch { coordinator.completeEmailEntry(EntryIntent(null, null)) }
        runCurrent()
        SignOutCoordinator(session, play, gate) {}.signOut(SignOutScope.FULL)
        membership.complete(EnterpriseSessionState.Unavailable(EnterpriseIdentity("old")))
        entry.join()
        assertEquals(0, preparations)
        assertNull(coordinator.navigation.value)
    }

    @Test fun providerStartedBeforeFullSignOutCannotRestoreItsLateFirebaseIdentity() = runTest {
        val gate = SessionMutationGate(MemorySessionEpochStorage())
        val session = Session()
        val play = Play().apply { result.complete(PlayPreparation.Ready) }
        val coordinator = SlekeEntryCoordinator(session, play, gate, MemoryEntryStateStorage())
        val original = EntryIntent("com.example.original", null)
        coordinator.rememberIntent(original)
        SignOutCoordinator(session, play, gate) {}.signOut(SignOutScope.FULL)
        session.state.value =
            EnterpriseSessionState.Unavailable(EnterpriseIdentity("late-provider"))
        coordinator.completeEmailEntry(original)
        assertNull(coordinator.navigation.value)
        assertEquals(EnterpriseSessionState.SignedOut, session.state.value)
    }

    @Test fun savedProviderAfterNonzeroEpochSurvivesProcessRestart() = runTest {
        val epochs = MemorySessionEpochStorage(7L)
        val firstGate = SessionMutationGate(epochs)
        val saved = MemoryEntryStateStorage()
        val session = Session()
        val play = Play().apply { result.complete(PlayPreparation.Ready) }
        val original = EntryIntent("com.example.original", "developer")
        SlekeEntryCoordinator(session, play, firstGate, saved).rememberIntent(original)
        val restarted = SlekeEntryCoordinator(session, play, SessionMutationGate(epochs), saved)
        restarted.completeEmailEntry(EntryIntent(null, null))
        assertEquals(original, restarted.navigation.value?.intent)
        assertEquals(0, session.signOuts)
    }

    @Test fun savedProviderInvalidatedBeforeDeathStaysInvalidInNewProcess() = runTest {
        val epochs = MemorySessionEpochStorage()
        val firstGate = SessionMutationGate(epochs)
        val saved = MemoryEntryStateStorage()
        val session = Session()
        val play = Play().apply { result.complete(PlayPreparation.Ready) }
        val original = EntryIntent("com.example.original", null)
        SlekeEntryCoordinator(session, play, firstGate, saved).rememberIntent(original)
        firstGate.invalidate()
        val restarted = SlekeEntryCoordinator(session, play, SessionMutationGate(epochs), saved)
        restarted.completeEmailEntry(original)
        assertNull(restarted.navigation.value)
        assertEquals(EnterpriseSessionState.SignedOut, session.state.value)
    }

    @Test fun providerResultAfterRecreationUsesSavedOriginalIntent() = runTest {
        val storage = MemoryEntryStateStorage()
        val gate = SessionMutationGate(MemorySessionEpochStorage())
        val session = Session()
        val play = Play().apply { result.complete(PlayPreparation.Ready) }
        val original = EntryIntent("com.example.original", "original-developer")
        SlekeEntryCoordinator(session, play, gate, storage).rememberIntent(original)
        val recreated = SlekeEntryCoordinator(session, play, gate, storage)
        recreated.completeEmailEntry(EntryIntent(null, null))
        assertEquals(original, recreated.navigation.value?.intent)
    }

    @Test fun recreatedPlayCompletionCannotNavigateTwice() = runTest {
        val gate = SessionMutationGate(MemorySessionEpochStorage())
        val storage = MemoryEntryStateStorage()
        val session = Session()
        val play = Play()
        val first = SlekeEntryCoordinator(session, play, gate, storage)
        val intent = EntryIntent("com.example.original", null)
        first.completePlayEntry(intent, gate.capture())
        first.consumeNavigation(first.navigation.value!!.id)
        val recreated = SlekeEntryCoordinator(session, play, gate, storage)
        recreated.completePlayEntry(intent, gate.capture())
        assertNull(recreated.navigation.value)
    }

    @Test fun identityChangeWhilePlayPreparesUsesCurrentMembership() = runTest {
        val gate = SessionMutationGate(MemorySessionEpochStorage())
        val session = Session()
        val play = Play()
        val coordinator = SlekeEntryCoordinator(session, play, gate, MemoryEntryStateStorage())
        val entry = launch { coordinator.completeEmailEntry(EntryIntent(null, null)) }
        runCurrent()
        session.state.value = EnterpriseSessionState.SignedOut
        play.result.complete(PlayPreparation.Ready)
        entry.join()
        assertNull(coordinator.navigation.value)
    }

    private class Session : EnterpriseSessionRepository {
        override val state =
            MutableStateFlow<EnterpriseSessionState>(
                EnterpriseSessionState.Unavailable(EnterpriseIdentity("current"))
            )
        var signOuts = 0
        override suspend fun revokeAccess() {
            state.value = EnterpriseSessionState.SignedOut
        }
        override suspend fun refresh() = state.value
        override suspend fun signOut(): CleanupReport {
            signOuts++
            state.value =
                EnterpriseSessionState.SignedOut
            return CleanupReport()
        }
    }
    private class Play : PlaySessionGateway {
        val result = CompletableDeferred<PlayPreparation>()
        override suspend fun prepareAnonymous() = result.await()
        override suspend fun resetCachedAuth() = CleanupReport()
        override suspend fun removeAllAccounts() = CleanupReport()
    }
}
