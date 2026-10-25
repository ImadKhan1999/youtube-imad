package com.imad.itube.player

import android.media.MediaCodecList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

/** One entry in the player's Quality menu, e.g. "1080p60". */
data class QualityOption(
    val label: String,
    val height: Int,
    val fps: Int,
    internal val stream: VideoStream,
)

/** One entry in the Audio track menu (for videos with dubbed / multi-language audio). */
data class AudioTrackOption(
    val id: String?,
    val label: String,
    internal val stream: AudioStream,
)

/** Picks playable video and audio streams out of a [StreamInfo], best codec first. */
object StreamSelector {

    private val av1Supported: Boolean by lazy { hasDecoder("video/av01") }
    private val vp9Supported: Boolean by lazy { hasDecoder("video/x-vnd.on2.vp9") }

    private fun hasDecoder(mime: String): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    }.getOrDefault(false)

    private fun VideoStream.playable(): Boolean {
        if (content.isNullOrEmpty()) return false
        if (deliveryMethod == DeliveryMethod.TORRENT) return false
        val c = codec.orEmpty().lowercase()
        if (c.startsWith("av01") && !av1Supported) return false
        if (c.startsWith("vp9") || c.startsWith("vp09")) return vp9Supported
        return true
    }

    private fun VideoStream.codecRank(): Int {
        val c = codec.orEmpty().lowercase()
        return when {
            c.startsWith("avc") -> 0 // H.264: hardware decoded everywhere, lowest battery cost
            c.startsWith("vp9") || c.startsWith("vp09") -> 1
            c.startsWith("av01") -> 2
            else -> 3
        }
    }

    private fun VideoStream.heightOrParsed(): Int =
        if (height > 0) height else resolution.orEmpty().substringBefore('p').toIntOrNull() ?: 0

    private fun VideoStream.fpsOrParsed(): Int =
        if (fps > 0) fps else resolution.orEmpty().substringAfter('p', "").toIntOrNull() ?: 30

    /** All selectable qualities, highest first, one stream per resolution+framerate. */
    fun qualities(info: StreamInfo): List<QualityOption> {
        val candidates = (info.videoOnlyStreams.orEmpty() + info.videoStreams.orEmpty())
            .filter { it.playable() && it.heightOrParsed() > 0 }
        return candidates
            .groupBy { it.heightOrParsed() to (if (it.fpsOrParsed() > 30) it.fpsOrParsed() else 30) }
            .map { (key, streams) ->
                val best = streams.sortedWith(
                    compareBy<VideoStream>({ it.codecRank() }, { if (it.isVideoOnly) 0 else 1 })
                ).first()
                val (h, fps) = key
                QualityOption(
                    label = if (fps > 30) "${h}p$fps" else "${h}p",
                    height = h,
                    fps = fps,
                    stream = best,
                )
            }
            .sortedWith(compareByDescending<QualityOption> { it.height }.thenByDescending { it.fps })
    }

    /** The quality "Auto" resolves to: the highest one not above [preferredHeight]. */
    fun autoQuality(options: List<QualityOption>, preferredHeight: Int): QualityOption? =
        options.firstOrNull { it.height <= preferredHeight } ?: options.lastOrNull()

    private fun AudioStream.playable(): Boolean =
        !content.isNullOrEmpty() && deliveryMethod != DeliveryMethod.TORRENT

    private fun AudioStream.rate(): Int = if (averageBitrate > 0) averageBitrate else bitrate

    /** Audio tracks (languages), original language first; each with its best stream. */
    fun audioTracks(info: StreamInfo): List<AudioTrackOption> {
        val streams = info.audioStreams.orEmpty().filter { it.playable() }
        return streams.groupBy { it.audioTrackId }
            .map { (id, group) ->
                val best = group.maxBy { it.rate() }
                val label = best.audioTrackName
                    ?: best.audioLocale?.displayName
                    ?: "Default"
                val suffix = when (best.audioTrackType) {
                    AudioTrackType.ORIGINAL -> " (original)"
                    AudioTrackType.DUBBED -> " (dubbed)"
                    AudioTrackType.DESCRIPTIVE -> " (descriptive)"
                    else -> ""
                }
                AudioTrackOption(id, label + suffix, best)
            }
            .sortedBy { if (it.stream.audioTrackType == AudioTrackType.ORIGINAL || it.id == null) 0 else 1 }
    }
}
