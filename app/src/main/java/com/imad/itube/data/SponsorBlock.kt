package com.imad.itube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** A stretch of a video that SponsorBlock users marked (sponsor read, intro, outro...). */
data class SkipSegment(val startMs: Long, val endMs: Long, val category: String)

/** Community-sourced sponsor-segment data from https://sponsor.ajay.app. */
class SponsorBlock(private val client: OkHttpClient) {

    @Serializable
    private data class ApiSegment(val segment: List<Double>, val category: String, val actionType: String = "skip")

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun segments(videoId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://sponsor.ajay.app/api/skipSegments".toHttpUrl().newBuilder()
                .addQueryParameter("videoID", videoId)
                .addQueryParameter("categories", CATEGORIES)
                .build()
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@use emptyList()
                val body = r.body?.string() ?: return@use emptyList()
                json.decodeFromString<List<ApiSegment>>(body)
                    .filter { it.actionType == "skip" && it.segment.size == 2 }
                    .map { SkipSegment((it.segment[0] * 1000).toLong(), (it.segment[1] * 1000).toLong(), it.category) }
                    .sortedBy { it.startMs }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val CATEGORIES = "[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"music_offtopic\"]"

        fun label(category: String): String = when (category) {
            "sponsor" -> "sponsor"
            "selfpromo" -> "self-promotion"
            "interaction" -> "subscribe reminder"
            "intro" -> "intro"
            "outro" -> "outro"
            "music_offtopic" -> "non-music section"
            else -> "segment"
        }
    }
}
