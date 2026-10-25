package com.imad.itube.ui.components

import com.imad.itube.data.model.FeedItem
import com.imad.itube.data.model.ItemPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PagedState(
    val items: List<FeedItem> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val next: Any? = null,
    val loaded: Boolean = false,
)

/** Loads a first page, then further pages on demand. Used by every infinite list. */
class Paged(
    private val scope: CoroutineScope,
    private val first: suspend () -> ItemPage<FeedItem>,
    private val more: suspend (Any) -> ItemPage<FeedItem>,
) {
    private val _state = MutableStateFlow(PagedState())
    val state: StateFlow<PagedState> = _state.asStateFlow()
    private var job: Job? = null

    fun load(refresh: Boolean = false) {
        job?.cancel()
        _state.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
        job = scope.launch {
            try {
                val page = first()
                _state.value = PagedState(items = page.items.distinctBy { it.url }, next = page.next, loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, refreshing = false, error = e.message ?: "Couldn't load", loaded = true)
                }
            }
        }
    }

    fun ensureLoaded() {
        if (!_state.value.loaded && !_state.value.loading) load()
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.next ?: return
        if (s.loadingMore || s.loading) return
        _state.update { it.copy(loadingMore = true) }
        job = scope.launch {
            try {
                val page = more(cursor)
                _state.update {
                    it.copy(
                        items = (it.items + page.items).distinctBy { item -> item.url },
                        next = page.next,
                        loadingMore = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, next = null) }
            }
        }
    }
}
