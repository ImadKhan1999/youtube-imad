package com.imad.itube.util

/**
 * YouTube thumbnail URLs from feeds are often the small 320px or 480px (letterboxed 4:3)
 * versions, which look blurry on a phone-wide card. Every video also has bigger versions at
 * fixed paths, so this lists them best first. Not every video has every size (older uploads
 * have no hq720 / maxres), so callers try them in order and fall back on a load error.
 */
object Thumbs {

    // Built lazily: a regex compiled at class load once crashed the app on Android's ICU.
    private val videoThumb by lazy {
        Regex("""^https?://i\d?\.ytimg\.com/(?:vi|vi_webp)/([A-Za-z0-9_-]{11})/([A-Za-z0-9_]+)\.(?:jpg|webp)""")
    }

    private val upgradable = setOf("default", "mqdefault", "hqdefault", "sddefault", "hq720", "maxresdefault")

    /** The video id of an i.ytimg.com video thumbnail, or null for any other image. */
    fun videoId(url: String?): String? = url?.let { videoThumb.find(it)?.groupValues?.get(1) }

    /** Sharpest 16:9 candidates first, ending with the original URL. */
    fun candidates(url: String?): List<String?> {
        val m = url?.let { videoThumb.find(it) } ?: return listOf(url)
        val id = m.groupValues[1]
        val name = m.groupValues[2]
        // Only the whole-video covers. Shorts covers (oar*), chapter stills (hqdefault_123)
        // and animated previews are left as they are.
        if (name !in upgradable) return listOf(url)
        val base = "https://i.ytimg.com/vi/$id/"
        return listOf(
            base + "hq720.jpg", // 1280x720, no black bars, present on most videos
            base + "maxresdefault.jpg", // 1280x720 or larger, for HD uploads
            base + "sddefault.jpg", // 640x480
            url,
        ).distinct()
    }

    private val working = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Index into [candidates] that loaded last time for this URL, so lists don't retry 404s. */
    fun knownIndex(url: String?): Int = url?.let { working[it] } ?: 0

    fun remember(url: String?, index: Int) {
        if (url != null) working[url] = index
    }

    /** Best guess without a network round trip, for places that can't fall back (notifications). */
    fun high(url: String?): String? {
        val id = videoId(url) ?: return url
        return "https://i.ytimg.com/vi/$id/hq720.jpg"
    }
}
