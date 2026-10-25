package com.imad.itube.cast

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import com.imad.itube.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import android.net.Uri

data class CastDevice(val id: String, val name: String, val description: String?, val selected: Boolean)

/**
 * Google Cast: finds Chromecasts and Cast-enabled TVs on the network and plays the current
 * video on them. Needs Google Play services (or microG); without it casting just isn't offered.
 */
class CastController(
    private val context: Context,
    private val player: PlayerController,
    private val scope: CoroutineScope,
) {
    private var castContext: CastContext? = null
    private var router: MediaRouter? = null
    private var selector: MediaRouteSelector? = null

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()
    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = onConnected(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onConnected(session)
        override fun onSessionEnded(session: CastSession, error: Int) = onDisconnected()
        override fun onSessionSuspended(session: CastSession, reason: Int) = onDisconnected()
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) = onDisconnected()
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) = onDisconnected()
    }

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refreshDevices()
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refreshDevices()
    }

    /** Called once at start-up. Quietly does nothing when Play services are missing. */
    fun init() {
        runCatching {
            CastContext.getSharedInstance(context, ContextCompat.getMainExecutor(context))
                .addOnSuccessListener { ctx ->
                    castContext = ctx
                    router = MediaRouter.getInstance(context)
                    selector = ctx.mergedSelector
                    ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                    ctx.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let(::onConnected)
                    _available.value = true
                }
                .addOnFailureListener { _available.value = false }
        }
    }

    init {
        // While connected, whatever you open next plays on the TV too.
        scope.launch {
            player.state.map { it.details?.id }.distinctUntilChanged().collect { id ->
                if (id != null && _connected.value) castCurrent()
            }
        }
    }

    /** Starts looking for devices; call [stopDiscovery] when the picker closes. */
    fun startDiscovery() {
        val r = router ?: return
        val s = selector ?: return
        r.addCallback(s, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refreshDevices()
    }

    fun stopDiscovery() {
        router?.removeCallback(routerCallback)
    }

    private fun refreshDevices() {
        val r = router ?: return
        val s = selector ?: return
        _devices.value = r.routes
            .filter { !it.isDefault && it.isEnabled && it.matchesSelector(s) }
            .map { CastDevice(it.id, it.name, it.description, it.isSelected) }
    }

    fun connect(deviceId: String) {
        router?.routes?.firstOrNull { it.id == deviceId }?.select()
    }

    fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
        router?.let { it.selectRoute(it.defaultRoute) }
    }

    private fun onConnected(session: CastSession) {
        _connected.value = true
        _deviceName.value = session.castDevice?.friendlyName
        castCurrent()
    }

    private fun onDisconnected() {
        _connected.value = false
        _deviceName.value = null
    }

    /** Sends the video playing in iTube to the connected device and pauses it here. */
    fun castCurrent(): Boolean {
        val session = castContext?.sessionManager?.currentCastSession ?: return false
        val client = session.remoteMediaClient ?: return false
        val info = player.currentInfo ?: return false
        val (url, type) = pickStream(info) ?: return false
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, info.name.orEmpty())
            putString(MediaMetadata.KEY_SUBTITLE, info.uploaderName.orEmpty())
            info.thumbnails.maxByOrNull { it.height }?.url?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val live = info.streamType == StreamType.LIVE_STREAM
        val media = MediaInfo.Builder(url)
            .setStreamType(if (live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(type)
            .setMetadata(meta)
            .build()
        val local = player.player
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(media)
            .setAutoplay(true)
            .setCurrentTime(if (live) 0 else local.currentPosition)
            .build()
        client.load(request)
        local.pause()
        return true
    }

    /**
     * The Default Media Receiver can't merge YouTube's separate audio and video streams, so
     * cast the HLS manifest when there is one (live), else the best stream with sound in it.
     */
    private fun pickStream(info: StreamInfo): Pair<String, String>? {
        info.hlsUrl?.takeIf { it.isNotBlank() }?.let { return it to "application/x-mpegURL" }
        val muxed = info.videoStreams
            .filter { !it.isVideoOnly && it.isUrl && it.content.isNotBlank() }
            .maxByOrNull { it.height }
            ?: return null
        return muxed.content to (muxed.format?.mimeType ?: "video/mp4")
    }
}
