package com.imad.itube.player

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.imad.itube.data.SkipSegment
import com.imad.itube.data.SponsorBlock
import com.imad.itube.data.YouTubeRepository
import com.imad.itube.data.model.VideoDetails
import com.imad.itube.data.model.VideoItem
import com.imad.itube.data.store.LibraryStore
import com.imad.itube.data.store.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo

/** What the watch page and mini player render. */
data class WatchState(
    val item: VideoItem? = null,
    val details: VideoDetails? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val qualities: List<QualityOption> = emptyList(),
    /** null means "Auto". */
    val selectedQuality: QualityOption? = null,
    /** What Auto currently resolves to. */
    val autoQuality: QualityOption? = null,
    val audioTracks: List<AudioTrackOption> = emptyList(),
    val selectedAudioTrack: AudioTrackOption? = null,
    val speed: Float = 1f,
    val loop: Boolean = false,
    val isLive: Boolean = false,
    val captions: List<CaptionOption> = emptyList(),
    /** null = captions off. */
    val selectedCaption: CaptionOption? = null,
    /** Playing a downloaded file; there are no details, qualities or related videos. */
    val offline: Boolean = false,
    /** Play-all queue (a playlist); empty when playing a single video. */
    val queue: List<VideoItem> = emptyList(),
    val queueIndex: Int = -1,
    val queueTitle: String? = null,
    val skipSegments: List<SkipSegment> = emptyList(),
) {
    val activeQuality: QualityOption? get() = selectedQuality ?: autoQuality
}

/**
 * Owns the single app-wide [ExoPlayer]. The watch page, mini player, PiP window and the media
 * notification ([PlaybackService]) all drive this one instance, which is what lets playback
 * continue seamlessly in the background.
 */
@OptIn(UnstableApi::class)
class PlayerController(
    private val app: Application,
    private val repository: YouTubeRepository,
    private val settings: SettingsStore,
    private val library: LibraryStore,
    private val sponsorBlock: SponsorBlock,
    private val scope: CoroutineScope,
) {
    val player: ExoPlayer = ExoPlayer.Builder(app, ITubeRenderersFactory(app))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()

    private val sourceBuilder = MediaSourceBuilder()

    private val _state = MutableStateFlow(WatchState())
    val state: StateFlow<WatchState> = _state.asStateFlow()

    private var info: StreamInfo? = null
    private var loadJob: Job? = null
    private var errorRetries = 0
    private val skipped = HashSet<Long>()

    /** Shown by the UI as a snackbar, e.g. "Skipped sponsor". */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
                if (playbackState == Player.STATE_READY) errorRetries = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Playback error", error)
                if (_state.value.offline) {
                    _state.update { it.copy(error = "Couldn't play this file: ${error.errorCodeName}") }
                    return
                }
                // Stream URLs expire after a few hours and YouTube sometimes answers 403;
                // fetching fresh URLs and resuming fixes most of these.
                if (errorRetries < 1 && _state.value.item != null) {
                    errorRetries++
                    val position = player.currentPosition
                    _state.value.item?.let { load(it, position, keepState = true) }
                } else {
                    _state.update { it.copy(error = "Playback error: ${error.errorCodeName}") }
                }
            }
        })
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        scope.launch { sponsorTicker() }
    }

    // ---- public API ---------------------------------------------------------------------

    fun play(item: VideoItem) = playInternal(item, queue = emptyList(), index = -1, queueTitle = null)

    /** "Play all": plays [items] in order starting at [index]. */
    fun playQueue(items: List<VideoItem>, index: Int = 0, title: String? = null) {
        if (items.isEmpty()) return
        playInternal(items[index.coerceIn(items.indices)], items, index.coerceIn(items.indices), title)
    }

    private fun playInternal(item: VideoItem, queue: List<VideoItem>, index: Int, queueTitle: String?) {
        val current = _state.value
        if (current.item?.url == item.url && player.mediaItemCount > 0 && current.error == null && !current.offline) {
            if (queue.isNotEmpty()) _state.update { it.copy(queue = queue, queueIndex = index, queueTitle = queueTitle) }
            player.play()
            return
        }
        errorRetries = 0
        skipped.clear()
        player.stop()
        player.clearMediaItems()
        info = null
        _state.value = WatchState(
            item = item,
            loading = true,
            speed = settings.settings.value.defaultSpeed,
            queue = queue,
            queueIndex = index,
            queueTitle = queueTitle,
        )
        load(item, startPosition = 0L, keepState = false)
        startService()
    }

    /** Plays a downloaded file. */
    fun playLocal(item: VideoItem, uri: Uri) {
        loadJob?.cancel()
        errorRetries = 0
        info = null
        player.stop()
        player.clearMediaItems()
        _state.value = WatchState(item = item, offline = true, speed = settings.settings.value.defaultSpeed)
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setDisplayTitle(item.title)
            .setArtist(item.channelName)
            .setArtworkUri(item.thumbnail?.let(Uri::parse))
            .build()
        player.setMediaSource(sourceBuilder.local(app, uri, item.url, metadata))
        player.playbackParameters = PlaybackParameters(_state.value.speed)
        player.prepare()
        player.playWhenReady = true
        startService()
    }

    fun playUrl(url: String) = play(VideoItem(url = url, title = ""))

    fun retry() {
        val item = _state.value.item ?: return
        if (_state.value.offline) return
        errorRetries = 0
        _state.update { it.copy(error = null, loading = true) }
        load(item, player.currentPosition.coerceAtLeast(0), keepState = true)
    }

    fun selectQuality(option: QualityOption?) {
        _state.update { it.copy(selectedQuality = option) }
        rebuildSource()
    }

    fun selectAudioTrack(option: AudioTrackOption) {
        _state.update { it.copy(selectedAudioTrack = option) }
        rebuildSource()
    }

    fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun setLoop(loop: Boolean) {
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _state.update { it.copy(loop = loop) }
    }

    val hasNext: Boolean
        get() {
            val s = _state.value
            return (s.queueIndex >= 0 && s.queueIndex < s.queue.lastIndex) ||
                s.details?.related?.any { it is VideoItem } == true
        }

    fun playNext() {
        val s = _state.value
        if (s.queueIndex >= 0 && s.queueIndex < s.queue.lastIndex) {
            val i = s.queueIndex + 1
            playInternal(s.queue[i], s.queue, i, s.queueTitle)
            return
        }
        val next = s.details?.related?.filterIsInstance<VideoItem>()?.firstOrNull { !it.isLive } ?: return
        play(next)
    }

    fun playPrevious() {
        val s = _state.value
        if (player.currentPosition > 3_000 || s.queueIndex <= 0) {
            player.seekTo(0)
        } else {
            val i = s.queueIndex - 1
            playInternal(s.queue[i], s.queue, i, s.queueTitle)
        }
    }

    fun playQueueItem(index: Int) {
        val s = _state.value
        if (index in s.queue.indices) playInternal(s.queue[index], s.queue, index, s.queueTitle)
    }

    fun selectCaption(option: CaptionOption?) {
        _state.update { it.copy(selectedCaption = option) }
        settings.update { it.copy(captionsLanguage = option?.languageTag) }
        applyCaptionSelection(option)
    }

    private fun applyCaptionSelection(option: CaptionOption?) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, option == null)
            .setPreferredTextLanguage(option?.languageTag)
            .setPreferredTextRoleFlags(if (option?.autoGenerated == true) C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND else C.ROLE_FLAG_CAPTION)
            .build()
    }

    /** Stops playback entirely (the mini player's ✕). */
    fun close() {
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        info = null
        _state.value = WatchState()
        app.stopService(Intent(app, PlaybackService::class.java))
    }

    /**
     * Turns video decoding off while the app is in the background, so background play only
     * downloads and decodes audio. Turned back on when the video is visible again.
     */
    fun setVideoTrackEnabled(enabled: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
            .build()
    }

    // ---- internals ----------------------------------------------------------------------

    private fun load(item: VideoItem, startPosition: Long, keepState: Boolean) {
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val newInfo = repository.streamInfo(item.url)
                info = newInfo
                val details = repository.details(newInfo)
                val qualities = StreamSelector.qualities(newInfo)
                val audioTracks = StreamSelector.audioTracks(newInfo)
                val captions = StreamSelector.captions(newInfo)
                val prev = _state.value
                val wantedCaption = if (keepState) {
                    prev.selectedCaption?.let { sel -> captions.firstOrNull { it.languageTag == sel.languageTag } }
                } else {
                    settings.settings.value.captionsLanguage?.let { lang -> captions.firstOrNull { it.languageTag == lang } }
                }
                val auto = StreamSelector.autoQuality(qualities, preferredHeight())
                _state.value = prev.copy(
                    item = details.toVideoItem().copy(thumbnail = details.thumbnail ?: item.thumbnail),
                    details = details,
                    loading = false,
                    error = null,
                    qualities = qualities,
                    autoQuality = auto,
                    selectedQuality = if (keepState) {
                        prev.selectedQuality?.let { sel -> qualities.firstOrNull { it.label == sel.label } }
                    } else {
                        null
                    },
                    audioTracks = audioTracks,
                    selectedAudioTrack = if (keepState) {
                        prev.selectedAudioTrack?.let { sel -> audioTracks.firstOrNull { it.id == sel.id } }
                    } else {
                        null
                    },
                    isLive = details.isLive,
                    captions = captions,
                    selectedCaption = wantedCaption,
                )
                library.addToHistory(details.toVideoItem())
                applyCaptionSelection(wantedCaption)
                prepare(startPosition)
                if (settings.settings.value.sponsorBlock && !details.isLive) {
                    val segments = sponsorBlock.segments(details.id)
                    if (_state.value.item?.url == details.url) _state.update { it.copy(skipSegments = segments) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load ${item.url}", e)
                _state.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    private fun rebuildSource() {
        if (info == null) return
        prepare(player.currentPosition.coerceAtLeast(0), keepPlayWhenReady = true)
    }

    private fun prepare(startPosition: Long, keepPlayWhenReady: Boolean = false) {
        val info = info ?: return
        val s = _state.value
        val video = s.activeQuality?.stream
        val audio = (s.selectedAudioTrack ?: s.audioTracks.firstOrNull())?.stream
        val metadata = MediaMetadata.Builder()
            .setTitle(info.name)
            .setDisplayTitle(info.name)
            .setArtist(info.uploaderName)
            .setArtworkUri(s.details?.thumbnail?.let(Uri::parse))
            .build()
        try {
            val source = sourceBuilder.build(info, video, audio, s.captions, metadata)
            val playWhenReady = if (keepPlayWhenReady) player.playWhenReady else true
            player.setMediaSource(source, startPosition)
            player.playbackParameters = PlaybackParameters(s.speed)
            player.repeatMode = if (s.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            player.prepare()
            player.playWhenReady = playWhenReady
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't build media source", e)
            _state.update { it.copy(error = e.message ?: "This video can't be played") }
        }
    }

    /** Skips SponsorBlock segments as playback reaches them. */
    private suspend fun sponsorTicker() {
        while (true) {
            delay(400)
            val segments = _state.value.skipSegments
            if (segments.isEmpty() || !player.isPlaying) continue
            val pos = player.currentPosition
            val seg = segments.firstOrNull { pos >= it.startMs && pos < it.endMs - 500 } ?: continue
            if (!skipped.add(seg.startMs)) continue
            player.seekTo(seg.endMs)
            _messages.tryEmit("Skipped ${SponsorBlock.label(seg.category)}")
        }
    }

    private fun onEnded() {
        val s = _state.value
        if (!s.loop && settings.settings.value.autoplay) playNext()
    }

    private fun preferredHeight(): Int {
        val metered = app.getSystemService<ConnectivityManager>()?.isActiveNetworkMetered == true
        val st = settings.settings.value
        return if (metered) st.mobileDataQuality else st.defaultQuality
    }

    private fun startService() {
        try {
            app.startService(Intent(app, PlaybackService::class.java))
        } catch (e: IllegalStateException) {
            // Not allowed from the background; the service is already running in that case.
            Log.w(TAG, "Couldn't start playback service", e)
        }
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is AgeRestrictedContentException -> "This video is age-restricted. Sign-in support is coming."
        is GeographicRestrictionException -> "This video isn't available in your country."
        is PaidContentException -> "This video is for members or requires payment."
        is ReCaptchaException -> "YouTube is asking to verify you're not a bot. Try again later."
        is ContentNotAvailableException -> e.message ?: "This video isn't available."
        is java.io.IOException -> "No connection. Check your network and try again."
        else -> e.message ?: "Something went wrong"
    }

    private companion object {
        const val TAG = "PlayerController"
    }
}
