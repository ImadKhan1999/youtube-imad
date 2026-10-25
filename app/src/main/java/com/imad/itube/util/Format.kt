package com.imad.itube.util

import android.text.format.DateUtils
import java.util.Locale

object Format {

    /** 1234 → "1.2K", 1_250_000 → "1.2M", like YouTube's compact counters. */
    fun compact(n: Long): String {
        if (n < 0) return ""
        return when {
            n < 1_000 -> n.toString()
            n < 10_000 -> trim(n / 1_000.0) + "K"
            n < 1_000_000 -> (n / 1_000).toString() + "K"
            n < 10_000_000 -> trim(n / 1_000_000.0) + "M"
            n < 1_000_000_000 -> (n / 1_000_000).toString() + "M"
            else -> trim(n / 1_000_000_000.0) + "B"
        }
    }

    private fun trim(v: Double): String {
        val s = String.format(Locale.US, "%.1f", Math.floor(v * 10) / 10)
        return s.removeSuffix(".0")
    }

    fun views(n: Long): String = when {
        n < 0 -> ""
        n == 1L -> "1 view"
        else -> "${compact(n)} views"
    }

    fun watching(n: Long): String = if (n < 0) "" else "${compact(n)} watching"

    fun subscribers(n: Long): String = if (n < 0) "" else "${compact(n)} subscribers"

    /** 75 → "1:15", 3725 → "1:02:05". */
    fun duration(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun position(ms: Long): String = duration(ms.coerceAtLeast(0) / 1000).ifEmpty { "0:00" }

    fun ago(epochMillis: Long?, textual: String?): String {
        if (!textual.isNullOrBlank()) return textual
        if (epochMillis == null || epochMillis <= 0) return ""
        return DateUtils.getRelativeTimeSpanString(
            epochMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }

    /** "Channel · 1.2M views · 3 days ago", skipping blank parts. */
    fun joinDot(vararg parts: String?): String =
        parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")
}
