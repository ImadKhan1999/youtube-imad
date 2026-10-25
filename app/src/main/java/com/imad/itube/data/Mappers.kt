package com.imad.itube.data

import com.imad.itube.data.model.ChannelItem
import com.imad.itube.data.model.FeedItem
import com.imad.itube.data.model.PlaylistItem
import com.imad.itube.data.model.VideoItem
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Picks the image closest to [targetHeight], preferring larger ones. */
fun List<Image>.best(targetHeight: Int = 720): String? {
    if (isEmpty()) return null
    val known = filter { it.height > 0 }
    if (known.isNotEmpty()) {
        val atLeast = known.filter { it.height >= targetHeight }.minByOrNull { it.height }
        return (atLeast ?: known.maxBy { it.height }).url
    }
    val byLevel = firstOrNull { it.estimatedResolutionLevel.name == "HIGH" }
        ?: firstOrNull { it.estimatedResolutionLevel.name == "MEDIUM" }
    return (byLevel ?: last()).url
}

fun StreamType.isLive(): Boolean =
    this == StreamType.LIVE_STREAM || this == StreamType.AUDIO_LIVE_STREAM

fun StreamInfoItem.toVideoItem(): VideoItem = VideoItem(
    url = url,
    title = name.orEmpty(),
    thumbnail = thumbnails.best(),
    channelName = uploaderName,
    channelUrl = uploaderUrl,
    channelAvatar = uploaderAvatars.best(88),
    viewCount = viewCount,
    uploadedText = textualUploadDate,
    uploadedAt = runCatching { uploadDate?.instant?.toEpochMilli() }.getOrNull(),
    duration = duration,
    isLive = streamType.isLive(),
    isShort = isShortFormContent,
    channelVerified = isUploaderVerified,
)

fun ChannelInfoItem.toChannelItem(): ChannelItem = ChannelItem(
    url = url,
    name = name.orEmpty(),
    avatar = thumbnails.best(176),
    subscriberCount = subscriberCount,
    videoCount = streamCount,
    description = description,
    verified = isVerified,
)

fun PlaylistInfoItem.toPlaylistItem(): PlaylistItem = PlaylistItem(
    url = url,
    title = name.orEmpty(),
    thumbnail = thumbnails.best(),
    uploaderName = uploaderName,
    videoCount = streamCount,
)

fun InfoItem.toFeedItem(): FeedItem? = when (this) {
    is StreamInfoItem -> toVideoItem()
    is ChannelInfoItem -> toChannelItem()
    is PlaylistInfoItem -> toPlaylistItem()
    else -> null
}

fun List<InfoItem>.toFeedItems(): List<FeedItem> = mapNotNull { it.toFeedItem() }
