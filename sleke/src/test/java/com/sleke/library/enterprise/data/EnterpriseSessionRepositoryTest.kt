package com.sleke.library.enterprise.data

import com.sleke.library.enterprise.domain.EnterpriseIdentity
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnterpriseSessionRepositoryTest {
    private val a = EnterpriseIdentity("a")
    private val b = EnterpriseIdentity("b")

    @Test fun storedEnterpriseHintCannotGrantAccess() = runTest {
        val source = Source()
        val storage = Storage().apply { saved = a }
        val repository = FirebaseEnterpriseSessionRepository(source, storage, backgroundScope)
        runCurrent()
        assertEquals(EnterpriseSessionState.SignedOut, repository.state.value)
        assertNull(storage.saved)
    }

    @Test fun outageIsRetryableAndDistinctFromDenial() = runTest {
        val source = Source().apply { identity.value = a }
        val storage = Storage()
        val repository = FirebaseEnterpriseSessionRepository(source, storage, backgroundScope)
        runCurrent()
        source.pending.complete(MembershipResult.Unavailable(a, IllegalStateException("offline")))
        runCurrent()
        assertEquals(EnterpriseSessionState.Unavailable(a), repository.state.value)
        source.pending = CompletableDeferred()
        val refresh = launch { repository.refresh() }
        runCurrent()
        source.pending.complete(MembershipResult.Allowed(a, "enterprise", "Company"))
        refresh.join()
        assertEquals(
            EnterpriseSessionState.Allowed(a, "enterprise", "Company"),
            repository.state.value
        )
        source.pending = CompletableDeferred()
        val deny = launch { repository.refresh() }
        runCurrent()
        source.pending.complete(MembershipResult.NoMembership(a))
        deny.join()
        assertEquals(EnterpriseSessionState.NoMembership(a), repository.state.value)
        assertEquals(false, storage.allowed)
    }

    @Test fun identityChangesDuringMembershipNeverPublishOldIdentity() = runTest {
        val source = Source().apply { identity.value = a }
        val storage = Storage()
        val repository = FirebaseEnterpriseSessionRepository(source, storage, backgroundScope)
        runCurrent()
        val old = source.pending
        source.pending = CompletableDeferred()
        source.identity.value = b
        runCurrent()
        old.complete(MembershipResult.Allowed(a, "old", "Old"))
        source.pending.complete(MembershipResult.NoMembership(b))
        runCurrent()
        assertEquals(EnterpriseSessionState.NoMembership(b), repository.state.value)
        assertEquals(b, storage.saved)
    }

    @Test fun lateCompletionAfterSignOutIsDiscarded() = runTest {
        val source = Source().apply { identity.value = a }
        val storage = Storage()
        val repository = FirebaseEnterpriseSessionRepository(source, storage, backgroundScope)
        runCurrent()
        val old = source.pending
        repository.signOut()
        old.complete(MembershipResult.Allowed(a, "old", "Old"))
        runCurrent()
        assertEquals(EnterpriseSessionState.SignedOut, repository.state.value)
        assertNull(storage.saved)
    }

    @Test fun accessRevocationPrecedesFirebaseAndPreferenceCleanup() = runTest {
        val source = Source().apply { identity.value = a }
        val storage = Storage()
        val repository = FirebaseEnterpriseSessionRepository(source, storage, backgroundScope)
        runCurrent()
        source.pending.complete(MembershipResult.Allowed(a, "enterprise", "Company"))
        runCurrent()
        storage.failClear = true
        repository.revokeAccess()
        assertEquals(EnterpriseSessionState.SignedOut, repository.state.value)
        assertEquals(0, source.signOuts)
        assertEquals(EnterpriseSessionState.SignedOut, repository.refresh())
    }

    @Test fun identityChangeRevokesAccessWhenPreferencesFail() = runTest {
        val source = Source().apply { identity.value = a }
        val storage = Storage()
        val repository = FirebaseEnterpriseSessionRepository(source, storage, backgroundScope)
        runCurrent()
        source.pending.complete(MembershipResult.Allowed(a, "enterprise-a", "A"))
        runCurrent()
        assertEquals(EnterpriseSessionState.Allowed(a, "enterprise-a", "A"), repository.state.value)
        storage.failClear = true
        source.pending = CompletableDeferred()
        source.identity.value = b
        runCurrent()
        assertEquals(EnterpriseSessionState.Unavailable(b), repository.state.value)
        storage.failClear = false
        val retry = launch { repository.refresh() }
        runCurrent()
        source.pending.complete(MembershipResult.Allowed(b, "enterprise-b", "B"))
        retry.join()
        assertEquals(EnterpriseSessionState.Allowed(b, "enterprise-b", "B"), repository.state.value)
        assertEquals(1, source.attaches)
    }

    @Test fun identityRejectedAtStorageBoundaryCannotPublishAllowance() = runTest {
        val source = Source().apply { identity.value = a }
        val storage = Storage().apply { rejectIdentity = true }
        val repository = FirebaseEnterpriseSessionRepository(source, storage, backgroundScope)
        runCurrent()
        source.pending.complete(MembershipResult.Allowed(a, "enterprise", "Company"))
        runCurrent()
        assertEquals(EnterpriseSessionState.Unavailable(a), repository.state.value)
        assertNull(storage.saved)
    }

    @Test fun signingOutAnAlreadySignedOutOwnerDoesNotBlockNextLogin() = runTest {
        val source = Source()
        val repository = FirebaseEnterpriseSessionRepository(source, Storage(), backgroundScope)
        runCurrent()
        repository.signOut()
        source.identity.value = a
        runCurrent()
        source.pending.complete(MembershipResult.NoMembership(a))
        runCurrent()
        assertEquals(EnterpriseSessionState.NoMembership(a), repository.state.value)
    }

    @Test fun ownerAttachesOneListenerAndClosesIt() = runTest {
        val source = Source()
        val repository = FirebaseEnterpriseSessionRepository(source, Storage(), backgroundScope)
        val one = launch { repository.state.collect {} }
        val two = launch { repository.state.collect {} }
        runCurrent()
        assertEquals(1, source.attaches)
        one.cancel()
        two.cancel()
        repository.close()
        runCurrent()
        assertEquals(1, source.detaches)
    }

    private class Source : FirebaseSessionSource {
        val identity = MutableStateFlow<EnterpriseIdentity?>(null)
        override val currentIdentity: EnterpriseIdentity? get() = identity.value
        var pending = CompletableDeferred<MembershipResult>()
        var attaches = 0
        var signOuts = 0
        var detaches = 0
        override val identities = flow {
            attaches++
            try {
                identity.collect { emit(it) }
            } finally {
                detaches++
            }
        }
        override suspend fun membership(): MembershipResult = pending.await()
        override suspend fun signOut() {
            signOuts++
            identity.value = null
        }
    }

    private class Storage : EnterpriseSessionStorage {
        var saved: EnterpriseIdentity? = null
        var allowed = false
        var rejectIdentity = false
        var failClear = false
        override suspend fun reconcile(identity: EnterpriseIdentity, isEnterprise: Boolean) {
            check(!rejectIdentity) { "Firebase identity changed before persistence" }
            saved = identity
            allowed = isEnterprise
        }
        override suspend fun clear() {
            check(!failClear) { "Disk unavailable" }
            saved = null
            allowed =
                false
        }
    }
}
