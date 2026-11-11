package com.imad.itube.data

import android.content.Context
import com.imad.itube.data.model.ChannelItem
import com.imad.itube.data.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The Subscriptions tab's feed. The last feed is saved on the device and shown the moment the
 * tab opens; fresh uploads are then fetched (12 channels at a time) and the list fills in as
 * each channel answers, instead of waiting for the slowest one.
 */
class SubscriptionFeed(context: Context, private val repository: YouTubeRepository, private val scope: CoroutineScope) {

    data class State(
        val items: List<VideoItem> = emptyList(),
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val error: String? = null,
        val loaded: Boolean = false,
    )

    @Serializable
    private data class Saved(val key: String, val at: Long, val items: List<VideoItem>)

    private val file = File(context.filesDir, "subscription_feed.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null
    private var key: String? = null
    private var fetchedAt = 0L
    private var diskRead = false

    /**
     * Shows the saved feed for [channels] right away, then refreshes it when it's older than a few
     * minutes, or always when [force] (pull to refresh).
     */
    fun load(channels: List<ChannelItem>, force: Boolean = false) {
        val newKey = channels.map { it.url }.sorted().joinToString("|")
        val sameChannels = newKey == key
        if (sameChannels && !force && job?.isActive == true) return
        if (sameChannels && !force && System.currentTimeMillis() - fetchedAt < FRESH_MS && _state.value.items.isNotEmpty()) return
        key = newKey
        job?.cancel()
        job = scope.launch {
            if (!diskRead) {
                diskRead = true
                readDisk()?.takeIf { it.key == newKey && it.items.isNotEmpty() }?.let { saved ->
                    fetchedAt = saved.at
                    _state.value = State(items = saved.items, loaded = true)
                    if (!force && System.currentTimeMillis() - saved.at < FRESH_MS) return@launch
                }
            }
            if (!sameChannels && _state.value.items.isNotEmpty()) {
                // Channels changed (subscribed or unsubscribed): drop the ones no longer followed.
                val urls = channels.mapTo(HashSet()) { it.url }
                _state.update { s -> s.copy(items = s.items.filter { it.channelUrl == null || it.channelUrl in urls }) }
            }
            val hadItems = _state.value.items.isNotEmpty()
            _state.update { it.copy(loading = !hadItems, refreshing = force, error = null) }
            val fresh = HashMap<String, List<VideoItem>>()
            try {
                val all = repository.subscriptionFeed(channels) { ch, videos ->
                    if (videos.isEmpty()) return@subscriptionFeed
                    fresh[ch.url] = videos
                    // On a first load, show videos as soon as channels answer.
                    if (!hadItems) _state.update { it.copy(items = merge(fresh.values), loaded = true) }
                }
                if (all.isEmpty() && hadItems) {
                    _state.update { it.copy(loading = false, refreshing = false) }
                    return@launch
                }
                fetchedAt = System.currentTimeMillis()
                val items = all.distinctBy { it.url }
                _state.value = State(items = items, loaded = true)
                writeDisk(Saved(newKey, fetchedAt, items.take(MAX_SAVED)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false, loaded = true, error = e.message ?: "Couldn't load") }
            }
        }
    }

    private fun merge(lists: Collection<List<VideoItem>>) =
        lists.flatten().distinctBy { it.url }.sortedByDescending { it.uploadedAt ?: 0L }

    private suspend fun readDisk(): Saved? = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) json.decodeFromString<Saved>(file.readText()) else null }.getOrNull()
    }

    private suspend fun writeDisk(saved: Saved) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(Saved.serializer(), saved))
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val FRESH_MS = 10 * 60 * 1000L
        const val MAX_SAVED = 300
    }
}
