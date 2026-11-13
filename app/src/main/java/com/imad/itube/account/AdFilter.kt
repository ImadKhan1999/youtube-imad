package com.imad.itube.account

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Strips every kind of ad out of an InnerTube response before it is parsed: the home masthead,
 * in-feed ad slots (which can wrap something that looks like a normal video), promoted videos in
 * search and the related list, sponsored shelves and promo banners.
 */
internal object AdFilter {

    private val AD_KEYS = setOf(
        "adSlotRenderer",
        "adSlotMetadata",
        "inFeedAdLayoutRenderer",
        "feedAdMetadataViewModel",
        "adBadgeViewModel",
        "promotedVideoRenderer",
        "compactPromotedVideoRenderer",
        "compactPromotedItemRenderer",
        "promotedSparklesWebRenderer",
        "promotedSparklesTextSearchRenderer",
        "searchPyvRenderer",
        "displayAdRenderer",
        "videoMastheadAdV3Renderer",
        "bannerPromoRenderer",
        "statementBannerRenderer",
        "brandVideoShelfRenderer",
        "brandVideoSingletonRenderer",
        "primetimePromoRenderer",
        "mealbarPromoRenderer",
        "playerAds",
        "adPlacements",
        "masthead",
    )

    /** Text that only ever shows up inside an ad. */
    private val AD_MARKERS = listOf(
        "\"adClientParams\"",
        "\"isAd\":true",
        "\"isPromoted\":true",
        "BADGE_STYLE_TYPE_AD",
        "googleadservices.com",
        "doubleclick.net",
        "/pagead/",
        "\"adSlotRenderer\"",
        "AdLayoutRenderer\"",
    )

    fun isAdKey(key: String): Boolean =
        key in AD_KEYS ||
            key.endsWith("AdRenderer") ||
            key.endsWith("AdLayoutRenderer") ||
            key.startsWith("adSlot") ||
            (key.startsWith("promoted") && (key.endsWith("Renderer") || key.endsWith("ViewModel")))

    /** True if this renderer (a video, lockup or reel entry) is an ad or carries ad tracking. */
    fun looksLikeAd(node: JsonElement?): Boolean {
        if (node == null) return false
        val text = node.toString()
        return AD_MARKERS.any { text.contains(it) }
    }

    /** A copy of this response with every ad renderer (and any feed item wrapping one) removed. */
    fun strip(node: JsonElement): JsonElement = when (node) {
        is JsonObject -> JsonObject(
            node.filterKeys { !isAdKey(it) }.mapValues { (_, v) -> strip(v) }
        )
        is JsonArray -> JsonArray(node.filterNot { isAdItem(it) }.map { strip(it) })
        else -> node
    }

    /**
     * A feed slot that holds an ad, e.g. `{richItemRenderer:{content:{adSlotRenderer:…}}}` or
     * `{adSlotRenderer:…}` directly.
     */
    private fun isAdItem(el: JsonElement): Boolean {
        val obj = el as? JsonObject ?: return false
        if (obj.keys.any(::isAdKey)) return true
        val wrapper = obj.values.singleOrNull() as? JsonObject ?: return false
        val content = wrapper["content"] as? JsonObject ?: return false
        return content.keys.any(::isAdKey)
    }
}
