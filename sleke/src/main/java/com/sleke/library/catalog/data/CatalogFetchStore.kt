package com.sleke.library.catalog.data

import com.sleke.library.catalog.domain.CatalogResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel

class CatalogFetchStore(private val scope: CoroutineScope) {
    private val lock = Any()
    private var generation = 0L
    private val success = mutableMapOf<String, CatalogResult.Success>()
    private val pending = mutableMapOf<String, Deferred<CatalogResult>>()

    fun invalidate() = synchronized(lock) {
        generation++
        success.clear()
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    suspend fun load(
        key: String,
        refresh: Boolean,
        fetch: suspend () -> CatalogResult
    ): CatalogResult {
        val captured: Long
        val request: Deferred<CatalogResult>
        synchronized(lock) {
            captured = generation
            if (!refresh) success[key]?.let { return it }
            if (refresh) success.remove(key)
            request = pending[key] ?: scope.async(start = CoroutineStart.LAZY) {
                try {
                    fetch()
                } catch (
                    cancelled: CancellationException
                ) {
                    throw cancelled
                } catch (error: Exception) {
                    CatalogResult.Failure(error)
                }
            }.also { deferred ->
                pending[key] = deferred
                deferred.invokeOnCompletion {
                    synchronized(lock) {
                        if (pending[key] ===
                            deferred
                        ) {
                            pending.remove(key)
                        }
                    }
                }
                deferred.start()
            }
        }
        val result = request.await()
        synchronized(lock) {
            if (captured != generation) throw CancellationException("Catalog identity changed")
            if (result is CatalogResult.Success) success[key] = result
        }
        return result
    }
}
