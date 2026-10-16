package com.sleke.library.enterprise.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionMutationGateTest {
    @Test fun invalidatedNetworkResultCannotPersist() = runTest {
        val gate = SessionMutationGate(MemorySessionEpochStorage())
        val old = gate.capture()
        gate.invalidate()
        var persisted = false
        assertFalse(gate.commitIfCurrent(old) { persisted = true })
        assertFalse(persisted)
    }

    @Test fun freshGateRejectsEpochInvalidatedBeforeProcessDeath() = runTest {
        val storage = MemorySessionEpochStorage(7L)
        val first = SessionMutationGate(storage)
        val old = first.capture()
        val current = first.invalidate()
        val restarted = SessionMutationGate(storage)
        assertEquals(current, restarted.capture())
        assertFalse(restarted.commitIfCurrent(old) { error("obsolete write") })
        assertTrue(restarted.commitIfCurrent(current) {})
    }

    @Test fun failedPersistencePreventsStaleWritesAcrossRestart() = runTest {
        val storage = MemorySessionEpochStorage(7L)
        val first = SessionMutationGate(storage)
        val old = first.capture()
        storage.failAdvance = true
        runCatching { first.invalidate() }
        assertFalse(first.commitIfCurrent(old) { error("obsolete write") })
        assertFalse(first.commitIfCurrent(first.capture()) { error("unpersisted write") })
        storage.failAdvance = false
        val restarted = SessionMutationGate(storage)
        assertFalse(restarted.commitIfCurrent(old) { error("obsolete restored write") })
    }

    @Test fun fullCleanupSerializesAfterAlreadyStartedPersistence() = runTest {
        val gate = SessionMutationGate(MemorySessionEpochStorage())
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val epoch = gate.capture()
        val write =
            launch {
                gate.commitIfCurrent(epoch) {
                    started.complete(Unit)
                    release.await()
                    order +=
                        "write"
                }
            }
        started.await()
        val signOutEpoch = gate.invalidate()
        val cleanup = launch { gate.commitIfCurrent(signOutEpoch) { order += "cleanup" } }
        runCurrent()
        assertTrue(order.isEmpty())
        release.complete(Unit)
        write.join()
        cleanup.join()
        assertEquals(listOf("write", "cleanup"), order)
    }
}
