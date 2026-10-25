package com.imad.itube.ui.shorts

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.imad.itube.data.YouTubeRepository
import com.imad.itube.player.ITubeRenderersFactory
import com.imad.itube.player.MediaSourceBuilder
import com.imad.itube.player.StreamSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * The Shorts feed's own looping player, separate from the main one so the watch page and the
 * mini player keep their state. Stream info for the next shorts is fetched ahead of time, so a
 * swipe starts the next video almost at once.
 */
class ShortsPlayer(context: Context, private val repository: YouTubeRepository, private val scope: CoroutineScope) {

    val player: ExoPlayer = ExoPlayer.Builder(context, ITubeRenderersFactory(context))
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        .build()
        .apply { repeatMode = Player.REPEAT_MODE_ONE }

    private val sources = MediaSourceBuilder()
    private val infos = HashMap<String, Deferred<StreamInfo>>()
    private var playJob: Job? = null

    /** Stream info for [url], fetched once and shared. */
    fun info(url: String): Deferred<StreamInfo> = synchronized(infos) {
        infos.getOrPut(url) { scope.async { repository.streamInfo(url) } }
    }

    fun prefetch(urls: List<String>) = urls.forEach { info(it) }

    /** Starts [url] from the beginning; [onError] gets a message if it can't play. */
    fun play(url: String, onError: (String) -> Unit) {
        playJob?.cancel()
        player.stop()
        player.clearMediaItems()
        playJob = scope.launch {
            try {
                val info = info(url).await()
                val quality = StreamSelector.autoQuality(StreamSelector.qualities(info), 720)
                val audio = StreamSelector.audioTracks(info).firstOrNull()
                val metadata = MediaMetadata.Builder().setTitle(info.name).setArtist(info.uploaderName).build()
                player.setMediaSource(sources.build(info, quality?.stream, audio?.stream, emptyList(), metadata))
                player.prepare()
                player.playWhenReady = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ShortsPlayer", "Couldn't play $url", e)
                synchronized(infos) { infos.remove(url) }
                onError(e.message ?: "This short can't be played")
            }
        }
    }

    fun release() {
        playJob?.cancel()
        player.release()
    }
}
