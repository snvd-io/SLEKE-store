package com.sleke.library.catalog.data

import com.sleke.library.catalog.domain.CatalogApp
import com.sleke.library.catalog.domain.CatalogMetadata
import com.sleke.library.catalog.domain.CatalogMetadataSource
import com.sleke.library.catalog.domain.MetadataContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Only unfinished requests are shared, within one account/device/locale generation. */
class CatalogEnricher(
    private val source: CatalogMetadataSource,
    private val scope: CoroutineScope
) {
    private val lock = Any()
    private val permits = Semaphore(4)
    private var context = source.context.value
    private var owner = SupervisorJob(scope.coroutineContext[Job])
    private val pending = mutableMapOf<String, Deferred<CatalogMetadata?>>()

    init {
        scope.launch {
            source.context.collect { current -> synchronized(lock) { switchContext(current) } }
        }
    }

    private fun switchContext(current: MetadataContext) {
        if (context != current) {
            owner.cancel()
            owner = SupervisorJob(scope.coroutineContext[Job])
            pending.clear()
            context = current
        }
    }

    suspend fun enrich(apps: List<CatalogApp>, context: MetadataContext): List<CatalogApp> =
        coroutineScope {
            apps.map { app ->
                async { app.withMetadata(metadata(app.packageName, context)) }
            }.awaitAll()
        }

    private suspend fun metadata(
        packageName: String,
        requestedContext: MetadataContext
    ): CatalogMetadata? {
        val request = synchronized(lock) {
            switchContext(source.context.value)
            if (context != requestedContext) throw CancellationException("Metadata context changed")
            pending[packageName]
                ?: CoroutineScope(
                    scope.coroutineContext + owner
                ).async(start = CoroutineStart.LAZY) {
                    permits.withPermit {
                        try {
                            source.load(packageName, requestedContext)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                }.also { deferred ->
                    pending[packageName] = deferred
                    deferred.invokeOnCompletion {
                        synchronized(lock) {
                            if (pending[packageName] ===
                                deferred
                            ) {
                                pending.remove(packageName)
                            }
                        }
                    }
                    deferred.start()
                }
        }
        val result = request.await()
        if (source.context.value !=
            requestedContext
        ) {
            throw CancellationException("Metadata context changed")
        }
        return result
    }
}
