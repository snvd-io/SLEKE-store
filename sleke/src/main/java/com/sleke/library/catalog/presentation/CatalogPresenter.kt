package com.sleke.library.catalog.presentation

import com.sleke.library.catalog.domain.CatalogRepository
import com.sleke.library.catalog.domain.CatalogResult
import com.sleke.library.catalog.domain.CatalogScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Search generation owns its request: an obsolete query can never publish newer state. */
class CatalogPresenter(
    private val catalogScope: CatalogScope,
    private val repository: CatalogRepository,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L

    fun search(query: String) {
        mutableState.update { it.copy(query = query) }
        load(refresh = false, delayMillis = 300)
    }
    fun retry() = load(refresh = true, delayMillis = 0)
    fun load(refresh: Boolean = false, delayMillis: Long = 0) {
        job?.cancel()
        val request = ++generation
        val query = state.value.query
        job = scope.launch {
            delay(delayMillis)
            mutableState.update { it.copy(status = CatalogStatus.Loading, error = null) }
            val result = try {
                repository.load(catalogScope, refresh)
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (error: Exception) {
                CatalogResult.Failure(error)
            }
            if (request != generation) return@launch
            mutableState.update { previous ->
                when (result) {
                    is CatalogResult.Success -> {
                        val rows = result.apps.filter {
                            query.isBlank() ||
                                it.title.contains(query.trim(), true) ||
                                it.packageName.contains(query.trim(), true)
                        }
                        previous.copy(
                            status = if (rows.isEmpty()) {
                                CatalogStatus.Empty
                            } else {
                                CatalogStatus.Populated
                            },
                            apps = rows.toList(),
                            error = null
                        )
                    }
                    is CatalogResult.Failure -> previous.copy(
                        status = CatalogStatus.Error,
                        apps = emptyList(),
                        error = result.cause.message
                    )
                }
            }
        }
    }
}
