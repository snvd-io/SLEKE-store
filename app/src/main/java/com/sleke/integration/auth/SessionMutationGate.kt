package com.sleke.integration.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Invalidates results immediately; cleanup then waits for any already accepted write. */
@Singleton
class SessionMutationGate internal constructor(private val storage: SessionEpochStorage) {
    @Inject constructor(@ApplicationContext context: Context) :
        this(FileSessionEpochStorage(File(context.noBackupFilesDir, "sleke-auth")))

    @Volatile private var storageFailure: Exception? = null

    @Volatile private var durableReady = true
    private val epochWrite = Any()
    private val generation = AtomicLong(loadEpoch())
    private val mutation = Mutex()
    fun capture(): Long = generation.get()
    fun invalidate(): Long = synchronized(epochWrite) {
        durableReady = false
        val next = generation.incrementAndGet()
        storageFailure?.let { throw it }
        try {
            storage.advance(next)
            durableReady = true
            next
        } catch (failure: Exception) {
            storageFailure = failure
            throw failure
        }
    }
    internal fun isCurrent(epoch: Long): Boolean =
        durableReady && storageFailure == null && epoch == generation.get()

    private fun loadEpoch(): Long = try {
        storage.load()
    } catch (failure: Exception) {
        storageFailure = failure
        durableReady = false
        UUID.randomUUID().mostSignificantBits
    }
    internal suspend fun captureAttempt(): Long =
        currentCoroutineContext()[AttemptEpoch]?.epoch ?: capture()
    internal suspend fun <T> withAttempt(epoch: Long, block: suspend () -> T): T =
        withContext(AttemptEpoch(epoch)) { block() }
    internal suspend fun cleanup(block: suspend () -> Unit) = mutation.withLock { block() }
    suspend fun commitIfCurrent(epoch: Long, block: suspend () -> Unit): Boolean =
        mutation.withLock {
            if (!isCurrent(epoch)) return@withLock false
            block()
            true
        }
}

private class AttemptEpoch(val epoch: Long) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AttemptEpoch>
}
