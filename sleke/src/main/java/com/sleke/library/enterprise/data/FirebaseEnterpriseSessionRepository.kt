package com.sleke.library.enterprise.data

import com.sleke.library.enterprise.domain.CleanupFailure
import com.sleke.library.enterprise.domain.CleanupReport
import com.sleke.library.enterprise.domain.CleanupStage
import com.sleke.library.enterprise.domain.EnterpriseIdentity
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import com.sleke.library.enterprise.domain.EnterpriseSessionState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class FirebaseEnterpriseSessionRepository internal constructor(
    private val source: FirebaseSessionSource,
    private val storage: EnterpriseSessionStorage,
    private val scope: CoroutineScope
) : EnterpriseSessionRepository, AutoCloseable {
    @Inject constructor(source: FirebaseSessionSource, storage: EnterpriseSessionStorage) :
        this(source, storage, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private val mutableState =
        MutableStateFlow<EnterpriseSessionState>(EnterpriseSessionState.Restoring)
    override val state = mutableState.asStateFlow()
    private val mutation = Mutex()
    private val generation = AtomicLong()
    private var identity: EnterpriseIdentity? = null
    private val accessRevoked = AtomicBoolean(false)
    private val listener = scope.launch {
        source.identities.collectLatest { current ->
            if (current != source.currentIdentity) return@collectLatest
            val shouldRefresh = mutation.withLock {
                if (accessRevoked.get() && current != null) return@withLock false
                if (current == identity &&
                    state.value != EnterpriseSessionState.Restoring
                ) {
                    return@withLock false
                }
                generation.incrementAndGet()
                identity = current
                // Revoke the previous identity before touching fallible preference storage.
                mutableState.value = current?.let { EnterpriseSessionState.Checking(it) }
                    ?: EnterpriseSessionState.SignedOut
                clearForTransition(current) && current != null
            }
            if (shouldRefresh) refresh()
        }
    }

    override suspend fun refresh(): EnterpriseSessionState {
        state.first { it != EnterpriseSessionState.Restoring }
        val request = mutation.withLock {
            if (accessRevoked.get()) return state.value
            val current = source.currentIdentity
            if (current == null) {
                generation.incrementAndGet()
                identity = null
                mutableState.value = EnterpriseSessionState.SignedOut
                clearForTransition(null)
                return state.value
            }
            val changed = identity != current
            generation.incrementAndGet()
            identity = current
            mutableState.value = EnterpriseSessionState.Checking(current)
            if (changed && !clearForTransition(current)) return state.value
            generation.get() to current
        }
        val result = try {
            source.membership()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            MembershipResult.Unavailable(request.second, failure)
        }
        return mutation.withLock {
            if (accessRevoked.get() ||
                generation.get() != request.first ||
                identity != request.second ||
                result.identity != identity ||
                source.currentIdentity != identity
            ) {
                return@withLock state.value
            }
            val resolved = when (result) {
                is MembershipResult.Allowed -> EnterpriseSessionState.Allowed(
                    result.identity,
                    result.enterpriseId,
                    result.enterpriseName
                )
                is MembershipResult.NoMembership -> EnterpriseSessionState.NoMembership(
                    result.identity
                )
                is MembershipResult.Unavailable -> EnterpriseSessionState.Unavailable(
                    request.second
                )
            }
            val persisted = try {
                storage.reconcile(request.second, resolved is EnterpriseSessionState.Allowed)
                resolved
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // A changed SDK identity or unavailable storage cannot authorize this result.
                EnterpriseSessionState.Unavailable(request.second)
            }
            if (accessRevoked.get()) return@withLock state.value
            if (source.currentIdentity != request.second) {
                mutableState.value = EnterpriseSessionState.Unavailable(request.second)
                clearForTransition(request.second)
            } else {
                mutableState.value = persisted
            }
            state.value
        }
    }

    private suspend fun clearForTransition(current: EnterpriseIdentity?): Boolean = try {
        storage.clear()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        if (current != null &&
            !accessRevoked.get()
        ) {
            mutableState.value = EnterpriseSessionState.Unavailable(current)
        }
        false
    }

    override suspend fun revokeAccess() {
        // This must not wait for a suspended preference write or platform cancellation.
        accessRevoked.set(true)
        generation.incrementAndGet()
        mutableState.value = EnterpriseSessionState.SignedOut
    }

    override suspend fun signOut(): CleanupReport {
        revokeAccess()
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
                    CleanupReport(listOf(CleanupFailure(CleanupStage.ENTERPRISE, failure)))
            }
        }
        attempt { source.signOut() }
        attempt { mutation.withLock { storage.clear() } }
        mutation.withLock { if (source.currentIdentity == null) accessRevoked.set(false) }
        return report
    }

    override fun close() {
        listener.cancel()
    }
}
