package com.sleke.library.enterprise.presentation

import androidx.lifecycle.SavedStateHandle
import com.sleke.library.enterprise.data.SessionMutationGate
import com.sleke.library.enterprise.domain.EnterpriseEntryPolicy
import com.sleke.library.enterprise.domain.EnterpriseSessionRepository
import com.sleke.library.enterprise.domain.EntryDestination
import com.sleke.library.enterprise.domain.PlayPreparation
import com.sleke.library.enterprise.domain.PlaySessionGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The original target survives provider UI and process recreation. */
data class EntryIntent(val packageName: String?, val developerId: String?)
data class EntryNavigation(val id: Long, val destination: EntryDestination, val intent: EntryIntent)

interface EntryStateStorage {
    var intent: EntryIntent
    var pending: EntryNavigation?
    var sequence: Long
    var providerPending: Boolean
    var providerEpoch: Long?
    var completedEpoch: Long?
    var pendingEpoch: Long?
}

class SavedEntryStateStorage(private val saved: SavedStateHandle) : EntryStateStorage {
    override var intent: EntryIntent
        get() = EntryIntent(saved["sleke.entry.package"], saved["sleke.entry.developer"])
        set(
        value
        ) {
            saved["sleke.entry.package"] = value.packageName
            saved["sleke.entry.developer"] =
                value.developerId
        }
    override var sequence: Long
        get() = saved["sleke.entry.sequence"] ?: 0L
        set(value) {
            saved["sleke.entry.sequence"] = value
        }
    override var providerPending: Boolean
        get() = saved["sleke.entry.providerPending"] ?: false
        set(value) {
            saved["sleke.entry.providerPending"] = value
        }
    override var providerEpoch: Long?
        get() = saved["sleke.entry.providerEpoch"]
        set(value) {
            saved["sleke.entry.providerEpoch"] = value
        }
    override var completedEpoch: Long?
        get() = saved["sleke.entry.completedEpoch"]
        set(value) {
            saved["sleke.entry.completedEpoch"] = value
        }
    override var pendingEpoch: Long?
        get() = saved["sleke.entry.pendingEpoch"]
        set(value) {
            saved["sleke.entry.pendingEpoch"] = value
        }
    override var pending: EntryNavigation?
        get() {
            val id = saved.get<Long>("sleke.entry.pending") ?: return null
            val destination = saved.get<String>("sleke.entry.destination") ?: return null
            return EntryNavigation(id, EntryDestination.valueOf(destination), intent)
        }
        set(value) {
            saved["sleke.entry.pending"] = value?.id
            saved["sleke.entry.destination"] = value?.destination?.name
        }
}

class SlekeEntryCoordinator(
    private val session: EnterpriseSessionRepository,
    private val play: PlaySessionGateway,
    private val gate: SessionMutationGate,
    private val storage: EntryStateStorage
) {
    private val mutableNavigation = MutableStateFlow(storage.pending)
    val navigation = mutableNavigation.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableFailure = MutableStateFlow<Throwable?>(null)
    val failure = mutableFailure.asStateFlow()

    fun rememberIntent(intent: EntryIntent) {
        storage.intent = intent
        storage.providerPending = true
        storage.providerEpoch = gate.capture()
        storage.completedEpoch = null
    }
    fun providerCancelled() {
        storage.providerPending = false
        storage.providerEpoch = null
        mutableBusy.value =
            false
    }

    suspend fun completeEmailEntry(intent: EntryIntent) {
        if (mutableBusy.value) return
        val epoch = if (storage.providerPending) {
            storage.providerEpoch ?: gate.capture()
        } else {
            gate.capture()
        }
        if (epoch != gate.capture()) {
            // FirebaseUI can complete outside this coroutine after full application sign-out.
            // Its obsolete result must not establish a new enterprise session.
            session.signOut()
            providerCancelled()
            return
        }
        if (!storage.providerPending) storage.intent = intent
        mutableBusy.value = true
        mutableFailure.value = null
        try {
            session.refresh()
            if (gate.capture() != epoch) return
            if (EnterpriseEntryPolicy.destination(session.state.value, true) ==
                EntryDestination.Wait
            ) {
                return
            }
            when (val preparation = gate.withAttempt(epoch) { play.prepareAnonymous() }) {
                is PlayPreparation.Failed -> mutableFailure.value = preparation.cause
                PlayPreparation.Ready -> gate.commitIfCurrent(epoch) {
                    val destination = EnterpriseEntryPolicy.destination(session.state.value, true)
                    if (destination != EntryDestination.Wait) publish(destination, epoch)
                }
            }
        } finally {
            storage.providerPending = false
            storage.providerEpoch = null
            mutableBusy.value =
                false
        }
    }

    /** Google/microG entry changes only Play identity and uses the same consumed effect. */
    suspend fun completePlayEntry(intent: EntryIntent, epoch: Long) {
        gate.commitIfCurrent(epoch) {
            if (storage.completedEpoch == epoch) return@commitIfCurrent
            storage.intent = intent
            if (mutableNavigation.value == null) publish(EntryDestination.RegularCatalog, epoch)
        }
    }

    private fun publish(destination: EntryDestination, epoch: Long) {
        storage.sequence += 1
        val effect = EntryNavigation(storage.sequence, destination, storage.intent)
        storage.pending = effect
        storage.pendingEpoch = epoch
        storage.completedEpoch = epoch
        mutableNavigation.value = effect
    }

    fun consumeNavigation(id: Long): Boolean {
        if (mutableNavigation.value?.id != id) return false
        val current = storage.pendingEpoch?.let(gate::isCurrent) == true
        storage.pending = null
        storage.pendingEpoch = null
        mutableNavigation.value = null
        return current
    }
}
