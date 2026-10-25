package com.imad.itube.data.store

import android.content.Context
import com.imad.itube.data.model.ChannelItem
import com.imad.itube.data.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class LibraryData(
    val subscriptions: List<ChannelItem> = emptyList(),
    val history: List<VideoItem> = emptyList(),
    val watchLater: List<VideoItem> = emptyList(),
    val liked: List<VideoItem> = emptyList(),
    val searchHistory: List<String> = emptyList(),
)

/**
 * The on-device library: subscriptions, watch history, Watch later and liked videos.
 * Stored as one small JSON file; every change is written straight back to disk.
 */
class LibraryStore(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "library.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private val _data = MutableStateFlow(LibraryData())
    val data: StateFlow<LibraryData> = _data.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val loaded = runCatching {
                if (file.exists()) json.decodeFromString<LibraryData>(file.readText()) else null
            }.getOrNull()
            if (loaded != null) _data.value = loaded
        }
    }

    private fun mutate(block: (LibraryData) -> LibraryData) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val next = block(_data.value)
                _data.value = next
                runCatching {
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeText(json.encodeToString(LibraryData.serializer(), next))
                    tmp.renameTo(file)
                }
            }
        }
    }

    fun isSubscribed(channelUrl: String?): Boolean =
        channelUrl != null && _data.value.subscriptions.any { sameChannel(it.url, channelUrl) }

    fun toggleSubscription(channel: ChannelItem) = mutate { d ->
        if (d.subscriptions.any { sameChannel(it.url, channel.url) }) {
            d.copy(subscriptions = d.subscriptions.filterNot { sameChannel(it.url, channel.url) })
        } else {
            d.copy(subscriptions = listOf(channel) + d.subscriptions)
        }
    }

    fun addToHistory(video: VideoItem) = mutate { d ->
        d.copy(history = (listOf(video) + d.history.filterNot { it.url == video.url }).take(500))
    }

    fun removeFromHistory(url: String) = mutate { d -> d.copy(history = d.history.filterNot { it.url == url }) }

    fun clearHistory() = mutate { it.copy(history = emptyList()) }

    fun toggleWatchLater(video: VideoItem) = mutate { d ->
        if (d.watchLater.any { it.url == video.url }) {
            d.copy(watchLater = d.watchLater.filterNot { it.url == video.url })
        } else {
            d.copy(watchLater = listOf(video) + d.watchLater)
        }
    }

    fun toggleLiked(video: VideoItem) = mutate { d ->
        if (d.liked.any { it.url == video.url }) {
            d.copy(liked = d.liked.filterNot { it.url == video.url })
        } else {
            d.copy(liked = listOf(video) + d.liked)
        }
    }

    fun addSearch(query: String) = mutate { d ->
        val q = query.trim()
        if (q.isEmpty()) d else d.copy(searchHistory = (listOf(q) + d.searchHistory.filterNot { it == q }).take(30))
    }

    fun removeSearch(query: String) = mutate { d -> d.copy(searchHistory = d.searchHistory - query) }

    companion object {
        /** Channel URLs come in /channel/UC…, /@handle and other shapes; compare loosely. */
        fun sameChannel(a: String, b: String): Boolean =
            a.trimEnd('/').substringAfter("youtube.com") == b.trimEnd('/').substringAfter("youtube.com")
    }
}
