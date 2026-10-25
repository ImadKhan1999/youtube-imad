package com.imad.itube.download

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Joins YouTube's separate video and audio streams into one playable file using the
 * platform MediaMuxer (MP4 for H.264 + AAC, WebM for VP9 + Opus). Also used to rewrite
 * fragmented DASH audio into a regular .m4a.
 */
object Muxer {

    @SuppressLint("WrongConstant")
    fun mux(inputs: List<File>, output: File, webm: Boolean) {
        val muxer = MediaMuxer(
            output.absolutePath,
            if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        )
        val extractors = inputs.map { f -> MediaExtractor().apply { setDataSource(f.absolutePath) } }
        try {
            val trackMap = extractors.map { ex ->
                ex.selectTrack(0)
                val format = ex.getTrackFormat(0)
                muxer.addTrack(format) to format
            }
            muxer.start()
            val info = MediaCodec.BufferInfo()
            extractors.forEachIndexed { i, ex ->
                val (track, format) = trackMap[i]
                val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
                } else {
                    4 * 1024 * 1024
                }
                val buffer = ByteBuffer.allocateDirect(size)
                while (true) {
                    buffer.clear()
                    val n = ex.readSampleData(buffer, 0)
                    if (n < 0) break
                    info.offset = 0
                    info.size = n
                    info.presentationTimeUs = ex.sampleTime
                    info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    muxer.writeSampleData(track, buffer, info)
                    ex.advance()
                }
            }
            muxer.stop()
        } finally {
            extractors.forEach { runCatching { it.release() } }
            runCatching { muxer.release() }
        }
    }
}
