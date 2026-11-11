package com.imad.itube.data

import android.os.SystemClock
import com.imad.itube.data.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * The Shorts tab's feed and stream info, kept for the whole app session. The first page is
 * fetched a few seconds after start-up, so opening the tab shows a short straight away, and
 * leaving and coming back carries on where you were instead of starting over.
 */
class ShortsSession(private val newFeed: () -> ShortsFeed, private val repository: YouTubeRepository, private val scope: CoroutineScope) {

    data class State(
        val items: List<VideoItem> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
    )

    private var feed = newFeed()
    private val mutex = Mutex()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The page the user was on, restored when the tab is opened again. */
    var lastPage = 0

    private class CachedInfo(val info: Deferred<StreamInfo>, val at: Long)
    private val infos = HashMap<String, CachedInfo>()

    /** Stream info for [url], fetched once and shared. Stream links expire, so entries live an hour. */
    fun info(url: String): Deferred<StreamInfo> = synchronized(infos) {
        val now = SystemClock.elapsedRealtime()
        infos[url]?.takeIf { now - it.at < INFO_TTL_MS }?.let { return it.info }
        val d = scope.async { repository.streamInfo(url) }
        infos[url] = CachedInfo(d, now)
        if (infos.size > 60) infos.entries.sortedBy { it.value.at }.take(20).forEach { infos.remove(it.key) }
        d
    }

    fun forget(url: String) {
        synchronized(infos) { infos.remove(url) }
    }

    fun prefetch(urls: List<String>) = urls.forEach { info(it) }

    /** Loads the first page in the background (and the first short's streams) if nothing is loaded yet. */
    fun warm() {
        if (_state.value.items.isNotEmpty() || _state.value.loading) return
        scope.launch {
            loadMore()
            _state.value.items.firstOrNull()?.let { info(it.url) }
        }
    }

    suspend fun loadMore() {
        if (mutex.isLocked) return
        mutex.withLock {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { feed.next() }
                .onSuccess { page ->
                    val have = _state.value.items.mapTo(HashSet()) { it.url }
                    _state.value = State(items = _state.value.items + page.filter { have.add(it.url) })
                }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Couldn't load Shorts") }
            if (_state.value.loading) _state.value = _state.value.copy(loading = false)
        }
    }

    /** Starts a fresh feed (pull to refresh / "Try again"). */
    suspend fun restart() {
        mutex.withLock {
            feed = newFeed()
            lastPage = 0
            _state.value = State()
        }
        loadMore()
    }

    private companion object {
        const val INFO_TTL_MS = 60 * 60 * 1000L
    }
}
