package com.imad.itube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Estimated dislike counts from https://returnyoutubedislike.com, since YouTube hides them. */
class ReturnYouTubeDislike(private val client: OkHttpClient) {

    @Serializable
    private data class Votes(val likes: Long = -1, val dislikes: Long = -1)

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 200
    }

    /** The dislike count, or null when it can't be fetched. */
    suspend fun dislikes(videoId: String): Long? {
        synchronized(cache) { cache[videoId] }?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://returnyoutubedislikeapi.com/votes".toHttpUrl().newBuilder()
                    .addQueryParameter("videoId", videoId)
                    .build()
                client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    json.decodeFromString<Votes>(r.body?.string().orEmpty()).dislikes.takeIf { it >= 0 }
                }
            }.getOrNull()?.also { synchronized(cache) { cache[videoId] = it } }
        }
    }
}
