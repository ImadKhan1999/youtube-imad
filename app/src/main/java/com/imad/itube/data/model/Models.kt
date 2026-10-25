package com.imad.itube.data.model

import kotlinx.serialization.Serializable

/** Anything that can show up in a feed, search results or related list. */
@Serializable
sealed interface FeedItem {
    val url: String
}

@Serializable
data class VideoItem(
    override val url: String,
    val title: String,
    val thumbnail: String? = null,
    val channelName: String? = null,
    val channelUrl: String? = null,
    val channelAvatar: String? = null,
    val viewCount: Long = -1,
    val uploadedText: String? = null,
    /** Upload time in epoch millis, when known. Used to sort subscription feeds. */
    val uploadedAt: Long? = null,
    /** Duration in seconds; 0 or less when unknown. */
    val duration: Long = -1,
    val isLive: Boolean = false,
    val isShort: Boolean = false,
    val channelVerified: Boolean = false,
) : FeedItem

@Serializable
data class ChannelItem(
    override val url: String,
    val name: String,
    val avatar: String? = null,
    val subscriberCount: Long = -1,
    val videoCount: Long = -1,
    val description: String? = null,
    val verified: Boolean = false,
) : FeedItem

@Serializable
data class PlaylistItem(
    override val url: String,
    val title: String,
    val thumbnail: String? = null,
    val uploaderName: String? = null,
    val videoCount: Long = -1,
) : FeedItem

/** A page of items plus an opaque continuation used to fetch the next page. */
data class ItemPage<T>(
    val items: List<T>,
    val next: Any? = null,
) {
    val hasMore: Boolean get() = next != null
}

/** One uploader-set chapter of a video. */
data class Chapter(val title: String, val startMs: Long, val thumbnail: String? = null)

/** The chapter playing at [positionMs], or null when there are no chapters. */
fun List<Chapter>.at(positionMs: Long): Chapter? = lastOrNull { it.startMs <= positionMs } ?: firstOrNull()

/** Everything the watch page shows about a video, apart from the streams themselves. */
data class VideoDetails(
    val url: String,
    val id: String,
    val title: String,
    val description: String,
    val descriptionIsHtml: Boolean,
    val viewCount: Long,
    val likeCount: Long,
    val uploadedText: String?,
    val uploadedAt: Long?,
    val channelName: String,
    val channelUrl: String,
    val channelAvatar: String?,
    val channelSubscribers: Long,
    val channelVerified: Boolean,
    val thumbnail: String?,
    val duration: Long,
    val isLive: Boolean,
    val tags: List<String>,
    val category: String?,
    val related: List<FeedItem>,
    /** Uploader-set chapters, in order, the first one starting at 0. Empty when the video has none. */
    val chapters: List<Chapter> = emptyList(),
) {
    fun toVideoItem() = VideoItem(
        url = url,
        title = title,
        thumbnail = thumbnail,
        channelName = channelName,
        channelUrl = channelUrl,
        channelAvatar = channelAvatar,
        viewCount = viewCount,
        uploadedText = uploadedText,
        uploadedAt = uploadedAt,
        duration = duration,
        isLive = isLive,
        channelVerified = channelVerified,
    )
}

data class ChannelDetails(
    val url: String,
    val name: String,
    val avatar: String?,
    val banner: String?,
    val subscriberCount: Long,
    val description: String?,
    val verified: Boolean,
    val tabs: List<ChannelTab>,
)

data class ChannelTab(val name: String, val handler: Any)

data class PlaylistDetails(
    val url: String,
    val title: String,
    val uploaderName: String?,
    val thumbnail: String?,
    val videoCount: Long,
    val firstPage: ItemPage<FeedItem>,
)

data class CommentItem(
    val author: String,
    val authorAvatar: String?,
    val text: String,
    val likes: String?,
    val published: String?,
    val replyCount: Int,
    val pinned: Boolean,
    val hearted: Boolean,
    /** Opaque cursor for this comment's replies, if it has any. */
    val replies: Any? = null,
    /** Like / dislike / reply tokens, only present when signed in. */
    val actions: CommentActions? = null,
    val id: String? = null,
)

data class CommentActions(
    val like: String?,
    val unlike: String?,
    val dislike: String?,
    val undislike: String?,
    val replyParams: String?,
    val liked: Boolean,
    val disliked: Boolean,
)

data class CommentsPage(
    val comments: List<CommentItem>,
    val next: Any?,
    val totalCount: Int,
    val disabled: Boolean,
    /** Token for posting a new top-level comment (signed in only). */
    val postParams: String? = null,
)
