package com.imad.itube.ui.shorts

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.imad.itube.data.ShortsSession
import com.imad.itube.player.ITubeRenderersFactory
import com.imad.itube.player.MediaSourceBuilder
import com.imad.itube.player.StreamSelector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The Shorts feed's players, separate from the main one so the watch page and the mini player
 * keep their state.
 *
 * Like YouTube, each of the three pages around the one on screen (previous, current, next) has
 * its own player. The next short is loaded and buffered while the current one plays, and its
 * first frame is already drawn, so a swipe shows the video at once instead of a black screen
 * and playback starts the moment the swipe settles.
 */
@OptIn(UnstableApi::class)
class ShortsPlayer(private val context: Context, private val session: ShortsSession, private val scope: CoroutineScope) {

    inner class Slot {
        val player: ExoPlayer = ExoPlayer.Builder(context, ITubeRenderersFactory(context))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            // Start as soon as half a second is buffered; keep at most 15 s ahead (shorts are short).
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(5_000, 15_000, 500, 1_000)
                    .build(),
            )
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ONE }

        /** The short loaded in this slot. */
        var url: String? by mutableStateOf(null)
            private set
        /** True once the loaded short's first frame has been drawn. */
        var firstFrame by mutableStateOf(false)
            private set
        var videoSize by mutableStateOf(VideoSize.UNKNOWN)
            private set
        var error: String? by mutableStateOf(null)
            private set
        private var job: Job? = null

        init {
            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { firstFrame = true }
                override fun onVideoSizeChanged(size: VideoSize) { if (size != VideoSize.UNKNOWN) videoSize = size }
                override fun onPlayerError(e: PlaybackException) {
                    Log.w(TAG, "Playback failed for $url", e)
                    error = "This short can't be played"
                    url?.let(session::forget)
                }
            })
        }

        /** Loads [newUrl] into this slot (buffering, paused) unless it's already there. */
        fun load(newUrl: String) {
            if (url == newUrl && error == null) return
            job?.cancel()
            url = newUrl
            firstFrame = false
            videoSize = VideoSize.UNKNOWN
            error = null
            player.playWhenReady = false
            player.stop()
            player.clearMediaItems()
            job = scope.launch {
                try {
                    val info = session.info(newUrl).await()
                    if (url != newUrl) return@launch
                    val quality = StreamSelector.autoQuality(StreamSelector.qualities(info), 720)
                    val audio = StreamSelector.audioTracks(info).firstOrNull()
                    val metadata = MediaMetadata.Builder().setTitle(info.name).setArtist(info.uploaderName).build()
                    player.setMediaSource(sources.build(info, quality?.stream, audio?.stream, emptyList(), metadata))
                    player.prepare()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't load $newUrl", e)
                    session.forget(newUrl)
                    if (url == newUrl) error = com.imad.itube.data.playbackErrorMessage(e)
                }
            }
        }

        fun retry() {
            val u = url ?: return
            error = null
            url = null
            load(u)
        }

        fun release() {
            job?.cancel()
            player.release()
        }
    }

    private val sources = MediaSourceBuilder()
    private val slots = List(3) { Slot() }
    private var activeSlot: Slot? = null

    /** The player for pager page [page]: pages next to each other never share one. */
    fun slot(page: Int): Slot = slots[Math.floorMod(page, slots.size)]

    val activePlayer: ExoPlayer? get() = activeSlot?.player

    /**
     * Makes [page] the one playing: it plays from the start, the pages either side are loaded
     * and paused, ready for the next swipe.
     */
    fun activate(page: Int, urls: List<String>) {
        val url = urls.getOrNull(page) ?: return
        val current = slot(page)
        slots.forEach { s ->
            if (s !== current && s.player.playWhenReady) {
                s.player.playWhenReady = false
                s.player.seekTo(0)
            }
        }
        current.load(url)
        if (activeSlot !== current) current.player.seekTo(0)
        activeSlot = current
        current.player.playWhenReady = true
        urls.getOrNull(page + 1)?.let { slot(page + 1).load(it) }
        urls.getOrNull(page - 1)?.let { slot(page - 1).load(it) }
        session.prefetch(urls.drop(page + 2).take(3))
    }

    fun pause() = slots.forEach { it.player.playWhenReady = false }

    fun resume() { activeSlot?.player?.playWhenReady = true }

    fun release() = slots.forEach { it.release() }

    private companion object {
        const val TAG = "ShortsPlayer"
    }
}
