package com.imad.itube.data

import com.imad.itube.account.YouTubeAccount
import com.imad.itube.account.at
import com.imad.itube.account.lastImage
import com.imad.itube.account.str
import com.imad.itube.data.model.VideoItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Where the Shorts tab gets its videos: YouTube's own Shorts sequence, the same one
 * youtube.com/shorts scrolls through (personalised when signed in). If that ever fails, it
 * falls back to a "#shorts" search, keeping only short-form videos.
 */
class ShortsFeed(private val repository: YouTubeRepository, private val api: YouTubeAccount) {
    private var sequenceToken: String? = null
    private var sequenceFailed = false
    private val queries = ArrayDeque(listOf("#shorts", "#shorts funny", "#shorts viral", "#shorts trending"))
    private var cursor: Any? = null
    private val seen = HashSet<String>()

    suspend fun next(): List<VideoItem> {
        if (!sequenceFailed) {
            val page = runCatching { sequencePage() }.getOrNull()
            if (!page.isNullOrEmpty()) return page
            sequenceFailed = true
        }
        return searchPage()
    }

    private suspend fun sequencePage(): List<VideoItem> {
        val token = sequenceToken
            ?: api.initialData("/shorts")?.str("sequenceContinuation")
            ?: return emptyList()
        val res = api.call("reel/reel_watch_sequence", requireAuth = false) {
            buildJsonObject { put("sequenceParams", token) }
        }
        sequenceToken = res.str("continuationEndpoint", "continuationCommand", "token")
        return (res.at("entries") as? JsonArray).orEmpty().mapNotNull { e ->
            val endpoint = e.at("command", "reelWatchEndpoint") ?: return@mapNotNull null
            val id = endpoint.str("videoId") ?: return@mapNotNull null
            val details = endpoint.at("unserializedPrefetchData", "playerResponse", "videoDetails")
            VideoItem(
                url = "https://www.youtube.com/watch?v=$id",
                title = details.str("title").orEmpty(),
                thumbnail = endpoint.at("thumbnail").lastImage(),
                channelName = details.str("author"),
                channelUrl = details.str("channelId")?.let { "https://www.youtube.com/channel/$it" },
                isShort = true,
            ).takeIf { seen.add(id) }
        }
    }

    private suspend fun searchPage(): List<VideoItem> {
        repeat(4) {
            val page = cursor?.let { repository.searchMore(it) } ?: run {
                val q = queries.removeFirstOrNull() ?: "#shorts"
                queries.addLast(q)
                repository.search(q, SearchFilter.Videos)
            }
            cursor = page.next
            val shorts = page.items.filterIsInstance<VideoItem>()
                .filter { (it.isShort || it.duration in 1..180) && !it.isLive && seen.add(videoId(it.url)) }
            if (shorts.isNotEmpty()) return shorts.shuffled()
        }
        return emptyList()
    }

    private fun videoId(url: String) = Regex("(?:v=|/shorts/)([\\w-]{11})").find(url)?.groupValues?.get(1) ?: url
}
