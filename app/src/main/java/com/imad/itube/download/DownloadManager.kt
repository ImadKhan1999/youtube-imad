package com.imad.itube.download

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.imad.itube.data.model.VideoItem
import com.imad.itube.data.store.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.util.UUID

/** Queues, runs and manages downloads. The work itself runs inside [DownloadService]. */
class DownloadManager(
    private val app: Application,
    private val store: DownloadStore,
    private val settings: SettingsStore,
    client: OkHttpClient,
) {
    private val downloader = StreamDownloader(client)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)

    val items: StateFlow<List<DownloadItem>> get() = store.items

    private val _waitingForWifi = MutableStateFlow(false)
    /** True while queued downloads are held back because "Wi-Fi only" is on and we're not on Wi-Fi. */
    val waitingForWifi: StateFlow<Boolean> = _waitingForWifi.asStateFlow()

    init {
        // Start held-back downloads when Wi-Fi comes back, and hold the running one if it drops.
        runCatching {
            connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onNetworkChanged()
                override fun onLost(network: Network) = onNetworkChanged()
            })
        }
    }

    private fun onNetworkChanged() {
        val allowed = networkAllowed()
        if (allowed && store.items.value.any { it.status == DownloadStatus.Queued } && currentId == null) {
            runCatching { startService() }
        }
        if (!allowed) {
            currentId?.let { id ->
                store.update(id) { if (it.status == DownloadStatus.Running) it.copy(status = DownloadStatus.Queued) else it }
                currentJob?.cancel()
            }
        }
    }

    private fun networkAllowed(): Boolean {
        if (!settings.settings.value.downloadWifiOnly) return true
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    @Volatile private var currentId: String? = null
    @Volatile private var currentJob: Job? = null

    /** Adds videos to the queue (skipping ones already queued or downloaded the same way). */
    fun enqueue(videos: List<VideoItem>, kind: DownloadKind, maxHeight: Int): Int {
        val existing = store.items.value
        val fresh = videos.filter { v ->
            existing.none {
                it.video.url == v.url && it.kind == kind &&
                    it.status in setOf(DownloadStatus.Queued, DownloadStatus.Running, DownloadStatus.Paused, DownloadStatus.Done)
            }
        }.map { DownloadItem(id = UUID.randomUUID().toString(), video = it, kind = kind, maxHeight = maxHeight) }
        if (fresh.isNotEmpty()) {
            store.add(fresh)
            startService()
        }
        return fresh.size
    }

    fun cancel(id: String) {
        store.update(id) { if (it.status == DownloadStatus.Done) it else it.copy(status = DownloadStatus.Cancelled) }
        if (currentId == id) currentJob?.cancel() else partialDir(id).deleteRecursively()
    }

    /** Stops a download but keeps what it has so far, to carry on later. */
    fun pause(id: String) {
        store.update(id) {
            if (it.status == DownloadStatus.Queued || it.status == DownloadStatus.Running) it.copy(status = DownloadStatus.Paused) else it
        }
        if (currentId == id) currentJob?.cancel()
    }

    fun resume(id: String) {
        store.update(id) { if (it.status == DownloadStatus.Paused) it.copy(status = DownloadStatus.Queued) else it }
        startService()
    }

    fun pauseAll() {
        store.items.value.filter { it.status == DownloadStatus.Queued || it.status == DownloadStatus.Running }.forEach { pause(it.id) }
    }

    fun resumeAll() {
        store.items.value.filter { it.status == DownloadStatus.Paused }.forEach {
            store.update(it.id) { d -> d.copy(status = DownloadStatus.Queued) }
        }
        startService()
    }

    fun retry(id: String) {
        partialDir(id).deleteRecursively()
        store.update(id) { it.copy(status = DownloadStatus.Queued, error = null, progress = 0f, bytesDone = 0) }
        startService()
    }

    private fun partialDir(id: String) = File(app.cacheDir, "dl/$id")

    fun delete(id: String) {
        cancel(id)
        store.get(id)?.fileUri?.let { uri ->
            runCatching {
                val u = Uri.parse(uri)
                when {
                    u.scheme == "file" -> File(u.path!!).delete()
                    DocumentsContract.isDocumentUri(app, u) -> DocumentsContract.deleteDocument(app.contentResolver, u)
                    else -> app.contentResolver.delete(u, null, null)
                }
            }
        }
        store.remove(id)
    }

    private fun startService() {
        // From the background (e.g. Wi-Fi coming back) Android may refuse; the queue then
        // carries on the next time the app is opened.
        runCatching { ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java)) }
    }

    /** Picks up anything left queued, e.g. when the app is opened again. */
    suspend fun resumePending() {
        while (!store.loaded) delay(50)
        if (store.items.value.any { it.status == DownloadStatus.Queued }) startService()
    }

    /** Runs queued downloads one by one until none are left. Called by [DownloadService]. */
    suspend fun runQueue(onUpdate: (DownloadItem?) -> Unit) {
        while (!store.loaded) delay(50)
        while (true) {
            val next = store.items.value.firstOrNull { it.status == DownloadStatus.Queued } ?: break
            if (!networkAllowed()) {
                _waitingForWifi.value = true
                break
            }
            _waitingForWifi.value = false
            currentId = next.id
            try {
                withContext(Dispatchers.IO) {
                    currentJob = currentCoroutineContext().job
                    process(next, onUpdate)
                }
            } catch (e: CancellationException) {
                // Paused, cancelled or held for Wi-Fi: keep going. Otherwise the whole service stopped.
                if (store.get(next.id)?.status == DownloadStatus.Running) throw e
            } catch (e: Exception) {
                Log.w(TAG, "Download failed", e)
                store.update(next.id) { it.copy(status = DownloadStatus.Failed, error = e.message ?: "Download failed") }
            } finally {
                currentId = null
                currentJob = null
                // Paused and held-back downloads keep their partial files to resume from.
                val status = store.get(next.id)?.status
                if (status != DownloadStatus.Paused && status != DownloadStatus.Queued && status != DownloadStatus.Running) {
                    partialDir(next.id).deleteRecursively()
                }
            }
        }
        if (store.items.value.none { it.status == DownloadStatus.Queued }) _waitingForWifi.value = false
        onUpdate(null)
    }

    private sealed interface Plan {
        val label: String
        data class Muxed(val video: VideoStream?, val audio: AudioStream, val webm: Boolean, override val label: String) : Plan
        data class Single(val stream: Stream, val ext: String, override val label: String) : Plan
    }

    private suspend fun process(item: DownloadItem, onUpdate: (DownloadItem?) -> Unit) {
        store.update(item.id) { it.copy(status = DownloadStatus.Running, error = null) }
        val info = StreamInfo.getInfo(ServiceList.YouTube, item.video.url)
        val plan = plan(info, item)
        val dir = partialDir(item.id).apply { mkdirs() }

        val parts: List<Stream> = when (plan) {
            is Plan.Muxed -> listOfNotNull(plan.video, plan.audio)
            is Plan.Single -> listOf(plan.stream)
        }
        val total = parts.sumOf { (it.itagItem?.contentLength ?: -1L).coerceAtLeast(0L) }
        var done = 0L
        var lastPush = 0L
        store.update(item.id) { it.copy(bytesTotal = total, qualityLabel = plan.label) }

        val files = parts.mapIndexed { i, stream ->
            val f = File(dir, "part$i")
            downloader.download(stream.content, stream.itagItem?.contentLength ?: -1L, f) { n ->
                done += n
                val now = System.currentTimeMillis()
                if (now - lastPush > 500) {
                    lastPush = now
                    val p = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                    store.update(item.id) { it.copy(bytesDone = done, progress = p) }
                    store.get(item.id)?.let(onUpdate)
                }
            }
            f
        }

        val title = info.name.orEmpty().ifBlank { item.video.title }
        val (output, mime, ext) = when (plan) {
            is Plan.Muxed -> {
                val ext = when {
                    plan.webm -> "webm"
                    plan.video == null -> "m4a"
                    else -> "mp4"
                }
                val out = File(dir, "out.$ext")
                Muxer.mux(files, out, plan.webm)
                val mime = when (ext) { "webm" -> if (plan.video == null) "audio/webm" else "video/webm"; "m4a" -> "audio/mp4"; else -> "video/mp4" }
                Triple(out, mime, ext)
            }
            is Plan.Single -> Triple(files[0], if (item.kind == DownloadKind.Audio) "audio/${plan.ext}" else "video/${plan.ext}", plan.ext)
        }
        val uri = publish(output, safeName(title) + "." + ext, mime, item.kind == DownloadKind.Audio)
        store.update(item.id) {
            it.copy(status = DownloadStatus.Done, progress = 1f, bytesDone = output.length(), bytesTotal = output.length(), fileUri = uri.toString())
        }
        store.get(item.id)?.let(onUpdate)
    }

    private fun AudioStream.rate() = if (averageBitrate > 0) averageBitrate else bitrate

    private fun plan(info: StreamInfo, item: DownloadItem): Plan {
        val audios = info.audioStreams.orEmpty().filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.content.isNullOrEmpty() }
        val original = audios.filter { it.audioTrackId == null || it.audioTrackType == AudioTrackType.ORIGINAL }.ifEmpty { audios }
        val m4a = original.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.rate() }
        val opus = original.filter { it.format == MediaFormat.WEBMA_OPUS || it.format == MediaFormat.WEBMA }.maxByOrNull { it.rate() }

        if (item.kind == DownloadKind.Audio) {
            m4a?.let { return Plan.Muxed(null, it, webm = false, label = "Audio · ${it.rate() / 1000} kbps") }
            opus?.let { return Plan.Muxed(null, it, webm = true, label = "Audio · ${it.rate() / 1000} kbps") }
            throw IllegalStateException("No audio stream available")
        }

        val videoOnly = info.videoOnlyStreams.orEmpty().filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.content.isNullOrEmpty() && it.height in 1..item.maxHeight
        }
        val mp4 = videoOnly.filter { it.format == MediaFormat.MPEG_4 && it.codec.orEmpty().startsWith("avc") }
            .maxWithOrNull(compareBy<VideoStream>({ it.height }, { it.fps }))
        val webm = videoOnly.filter { it.format == MediaFormat.WEBM && it.codec.orEmpty().startsWith("vp9") }
            .maxWithOrNull(compareBy<VideoStream>({ it.height }, { it.fps }))

        if (mp4 != null && m4a != null && (webm == null || opus == null || mp4.height >= webm.height)) {
            return Plan.Muxed(mp4, m4a, webm = false, label = mp4.resolution ?: "${mp4.height}p")
        }
        if (webm != null && opus != null) {
            return Plan.Muxed(webm, opus, webm = true, label = webm.resolution ?: "${webm.height}p")
        }
        val muxed = info.videoStreams.orEmpty()
            .filter { !it.isVideoOnly && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.content.isNullOrEmpty() }
            .maxByOrNull { it.height }
        if (muxed != null) return Plan.Single(muxed, muxed.format?.suffix ?: "mp4", muxed.resolution ?: "${muxed.height}p")
        throw IllegalStateException("No downloadable video stream found")
    }

    /** Copies the finished file into the picked folder, or Movies/iTube or Music/iTube. */
    private fun publish(file: File, name: String, mime: String, audio: Boolean): Uri {
        settings.settings.value.downloadFolder?.let { tree ->
            runCatching { publishToTree(Uri.parse(tree), file, name, mime) }
                .onSuccess { return it }
                .onFailure { Log.w(TAG, "Couldn't save to the picked folder, using the default", it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = app.contentResolver
            val collection = if (audio) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/iTube",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IllegalStateException("Couldn't create the file")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: throw IllegalStateException("Couldn't write the file")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri
        }
        val dir = File(
            app.getExternalFilesDir(if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES),
            "iTube",
        ).apply { mkdirs() }
        var target = File(dir, name)
        var i = 1
        while (target.exists()) target = File(dir, name.substringBeforeLast('.') + " ($i)." + name.substringAfterLast('.')).also { i++ }
        file.copyTo(target)
        return Uri.fromFile(target)
    }

    private fun publishToTree(tree: Uri, file: File, name: String, mime: String): Uri {
        val resolver = app.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val uri = DocumentsContract.createDocument(resolver, parent, mime, name)
            ?: throw IllegalStateException("Couldn't create the file")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("Couldn't write the file")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw e
        }
        return uri
    }

    private fun safeName(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(120).ifBlank { "video" }

    private companion object {
        const val TAG = "DownloadManager"
    }
}
