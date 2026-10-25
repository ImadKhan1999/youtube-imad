package com.imad.itube.data

import androidx.core.text.HtmlCompat
import com.imad.itube.data.model.ChannelDetails
import com.imad.itube.data.model.ChannelItem
import com.imad.itube.data.model.ChannelTab
import com.imad.itube.data.model.CommentItem
import com.imad.itube.data.model.CommentsPage
import com.imad.itube.data.model.FeedItem
import com.imad.itube.data.model.ItemPage
import com.imad.itube.data.model.PlaylistDetails
import com.imad.itube.data.model.VideoDetails
import com.imad.itube.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** The YouTube categories iTube shows as home chips and Explore tiles. */
enum class Kiosk(val id: String, val title: String) {
    Music("trending_music", "Music"),
    Gaming("trending_gaming", "Gaming"),
    Live("live", "Live"),
    Podcasts("trending_podcasts_episodes", "Podcasts"),
    Movies("trending_movies_and_shows", "Movies & TV"),
    Trending("Trending", "Trending"),
}

enum class SearchFilter(val filter: String?, val label: String) {
    All(YoutubeSearchQueryHandlerFactory.ALL, "All"),
    Videos(YoutubeSearchQueryHandlerFactory.VIDEOS, "Videos"),
    Channels(YoutubeSearchQueryHandlerFactory.CHANNELS, "Channels"),
    Playlists(YoutubeSearchQueryHandlerFactory.PLAYLISTS, "Playlists"),
}

/**
 * All network reads go through here. Every call runs on [Dispatchers.IO] and maps
 * NewPipeExtractor types into iTube's own models.
 */
class YouTubeRepository {

    private val yt: StreamingService get() = ServiceList.YouTube

    // ---- Kiosks / home ----------------------------------------------------------------

    suspend fun kiosk(kiosk: Kiosk): ItemPage<FeedItem> = io {
        val url = yt.kioskList.getListLinkHandlerFactoryByType(kiosk.id).fromId(kiosk.id).url
        val info = KioskInfo.getInfo(yt, url)
        ItemPage(info.relatedItems.map { it.toVideoItem() }, info.nextPage.validOrNull()?.let { KioskCursor(url, it) })
    }

    suspend fun kioskMore(cursor: Any): ItemPage<FeedItem> = io {
        val c = cursor as KioskCursor
        val page = KioskInfo.getMoreItems(yt, c.url, c.page)
        ItemPage(page.items.map { it.toVideoItem() }, page.nextPageOrNull()?.let { KioskCursor(c.url, it) })
    }

    /**
     * A mixed "All" feed: the first page of several categories, interleaved so the feed
     * does not start with twenty music videos in a row.
     */
    suspend fun homeMix(): List<FeedItem> = coroutineScope {
        val sources = listOf(Kiosk.Music, Kiosk.Gaming, Kiosk.Movies, Kiosk.Podcasts, Kiosk.Live)
        val lists = sources.map { k -> async { runCatching { kiosk(k).items }.getOrDefault(emptyList()) } }
            .awaitAll()
        if (lists.all { it.isEmpty() }) throw IllegalStateException("Couldn't load the home feed")
        interleave(lists).distinctBy { it.url }
    }

    // ---- Search ------------------------------------------------------------------------

    suspend fun suggestions(query: String): List<String> = io {
        if (query.isBlank()) emptyList() else yt.suggestionExtractor.suggestionList(query)
    }

    suspend fun search(query: String, filter: SearchFilter): ItemPage<FeedItem> = io {
        val handler = yt.searchQHFactory.fromQuery(
            query,
            listOfNotNull(filter.filter),
            "",
        )
        val info = SearchInfo.getInfo(yt, handler)
        ItemPage(info.relatedItems.toFeedItems(), info.nextPage.validOrNull()?.let { SearchCursor(handler, it) })
    }

    suspend fun searchMore(cursor: Any): ItemPage<FeedItem> = io {
        val c = cursor as SearchCursor
        val page = SearchInfo.getMoreItems(yt, c.handler, c.page)
        ItemPage(page.items.toFeedItems(), page.nextPageOrNull()?.let { SearchCursor(c.handler, it) })
    }

    // ---- Videos ------------------------------------------------------------------------

    suspend fun streamInfo(url: String): StreamInfo = io { StreamInfo.getInfo(yt, url) }

    fun details(info: StreamInfo): VideoDetails {
        val description: Description? = info.description
        return VideoDetails(
            url = info.url,
            id = info.id,
            title = info.name.orEmpty(),
            description = description?.content().orEmpty(),
            descriptionIsHtml = description?.type() == Description.Type.HTML,
            viewCount = info.viewCount,
            likeCount = info.likeCount,
            uploadedText = info.textualUploadDate,
            uploadedAt = runCatching { info.uploadDate?.instant?.toEpochMilli() }.getOrNull(),
            channelName = info.uploaderName.orEmpty(),
            channelUrl = info.uploaderUrl.orEmpty(),
            channelAvatar = info.uploaderAvatars.best(88),
            channelSubscribers = info.uploaderSubscriberCount,
            channelVerified = info.isUploaderVerified,
            thumbnail = info.thumbnails.best(),
            duration = info.duration,
            isLive = info.streamType.isLive(),
            tags = info.tags.orEmpty(),
            category = info.category,
            related = info.relatedItems.orEmpty().toFeedItems(),
        )
    }

    suspend fun comments(videoUrl: String): CommentsPage = io {
        val info = CommentsInfo.getInfo(yt, videoUrl)
        CommentsPage(
            comments = info.relatedItems.map { it.toComment() },
            next = info.nextPage.validOrNull()?.let { CommentsCursor(videoUrl, it) },
            totalCount = info.commentsCount,
            disabled = info.isCommentsDisabled,
        )
    }

    suspend fun commentsMore(cursor: Any): CommentsPage = io {
        val c = cursor as CommentsCursor
        val page = CommentsInfo.getMoreItems(yt, c.url, c.page)
        CommentsPage(
            comments = page.items.map { it.toComment() },
            next = page.nextPageOrNull()?.let { CommentsCursor(c.url, it) },
            totalCount = -1,
            disabled = false,
        )
    }

    // ---- Channels ----------------------------------------------------------------------

    suspend fun channel(url: String): ChannelDetails = io {
        val info = ChannelInfo.getInfo(yt, url)
        ChannelDetails(
            url = info.url,
            name = info.name.orEmpty(),
            avatar = info.avatars.best(176),
            banner = info.banners.best(400),
            subscriberCount = info.subscriberCount,
            description = info.description,
            verified = info.isVerified,
            tabs = info.tabs.mapNotNull { tab ->
                val name = tab.contentFilters.firstOrNull() ?: return@mapNotNull null
                ChannelTab(name, tab)
            },
        )
    }

    suspend fun channelTab(tab: ChannelTab): ItemPage<FeedItem> = io {
        val handler = tab.handler as ListLinkHandler
        val info = ChannelTabInfo.getInfo(yt, handler)
        ItemPage(info.relatedItems.toFeedItems(), info.nextPage.validOrNull()?.let { TabCursor(handler, it) })
    }

    suspend fun channelTabMore(cursor: Any): ItemPage<FeedItem> = io {
        val c = cursor as TabCursor
        val page = ChannelTabInfo.getMoreItems(yt, c.handler, c.page)
        ItemPage(page.items.toFeedItems(), page.nextPageOrNull()?.let { TabCursor(c.handler, it) })
    }

    /** Latest uploads of the given channels, newest first (uses YouTube's RSS feeds). */
    suspend fun subscriptionFeed(channels: List<ChannelItem>): List<VideoItem> = coroutineScope {
        val gate = Semaphore(6)
        channels.map { ch ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    runCatching {
                        FeedInfo.getInfo(yt, ch.url).relatedItems.map {
                            it.toVideoItem().copy(
                                channelName = it.uploaderName ?: ch.name,
                                channelUrl = it.uploaderUrl ?: ch.url,
                                channelAvatar = ch.avatar,
                            )
                        }
                    }.getOrDefault(emptyList())
                }
            }
        }.awaitAll().flatten().sortedByDescending { it.uploadedAt ?: 0L }
    }

    // ---- Playlists ---------------------------------------------------------------------

    suspend fun playlist(url: String): PlaylistDetails = io {
        val info = PlaylistInfo.getInfo(yt, url)
        PlaylistDetails(
            url = info.url,
            title = info.name.orEmpty(),
            uploaderName = info.uploaderName,
            thumbnail = info.thumbnails.best(),
            videoCount = info.streamCount,
            firstPage = ItemPage(
                info.relatedItems.map { it.toVideoItem() },
                info.nextPage.validOrNull()?.let { PlaylistCursor(info.url, it) },
            ),
        )
    }

    suspend fun playlistMore(cursor: Any): ItemPage<FeedItem> = io {
        val c = cursor as PlaylistCursor
        val page = PlaylistInfo.getMoreItems(yt, c.url, c.page)
        ItemPage(page.items.map { it.toVideoItem() }, page.nextPageOrNull()?.let { PlaylistCursor(c.url, it) })
    }

    // ---- helpers -----------------------------------------------------------------------

    private class KioskCursor(val url: String, val page: Page)
    private class SearchCursor(val handler: SearchQueryHandler, val page: Page)
    private class TabCursor(val handler: ListLinkHandler, val page: Page)
    private class PlaylistCursor(val url: String, val page: Page)
    private class CommentsCursor(val url: String, val page: Page)

    private fun <T : InfoItem> InfoItemsPage<T>.nextPageOrNull(): Page? =
        if (hasNextPage()) nextPage else null

    private fun Page?.validOrNull(): Page? = this?.takeIf { Page.isValid(it) }

    private fun CommentsInfoItem.toComment() = CommentItem(
        author = uploaderName.orEmpty(),
        authorAvatar = uploaderAvatars.best(88),
        text = commentText?.let { d ->
            if (d.type() == Description.Type.HTML) {
                HtmlCompat.fromHtml(d.content(), HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            } else {
                d.content()
            }
        }.orEmpty(),
        likes = textualLikeCount?.takeIf { it.isNotBlank() }
            ?: likeCount.takeIf { it > 0 }?.toString(),
        published = textualUploadDate,
        replyCount = replyCount,
        pinned = isPinned,
        hearted = isHeartedByUploader,
    )

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val out = ArrayList<T>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        return out
    }
}
