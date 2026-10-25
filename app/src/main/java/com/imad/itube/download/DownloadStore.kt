package com.imad.itube.download

import android.content.Context
import com.imad.itube.data.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class DownloadKind { Video, Audio }

@Serializable
enum class DownloadStatus { Queued, Running, Paused, Done, Failed, Cancelled }

@Serializable
data class DownloadItem(
    val id: String,
    val video: VideoItem,
    val kind: DownloadKind,
    /** Maximum video height for video downloads. */
    val maxHeight: Int = 720,
    val status: DownloadStatus = DownloadStatus.Queued,
    val progress: Float = 0f,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val error: String? = null,
    /** content:// or file:// URI of the finished file. */
    val fileUri: String? = null,
    val qualityLabel: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Download list persisted as JSON so it survives restarts. */
class DownloadStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "downloads.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(DownloadItem.serializer())
    private val mutex = Mutex()

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    @Volatile
    var loaded = false
        private set

    init {
        scope.launch(Dispatchers.IO) {
            val list = runCatching { if (file.exists()) json.decodeFromString(serializer, file.readText()) else null }
                .getOrNull().orEmpty()
                // Anything that was running when the app died goes back in the queue.
                .map { if (it.status == DownloadStatus.Running) it.copy(status = DownloadStatus.Queued) else it }
            _items.value = list
            loaded = true
        }
    }

    fun add(items: List<DownloadItem>) = mutate { it + items }

    fun update(id: String, transform: (DownloadItem) -> DownloadItem) =
        mutate { list -> list.map { if (it.id == id) transform(it) else it } }

    fun remove(id: String) = mutate { list -> list.filterNot { it.id == id } }

    fun get(id: String): DownloadItem? = _items.value.firstOrNull { it.id == id }

    private fun mutate(block: (List<DownloadItem>) -> List<DownloadItem>) {
        _items.update(block)
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeText(json.encodeToString(serializer, _items.value))
                    tmp.renameTo(file)
                }
            }
        }
    }
}
