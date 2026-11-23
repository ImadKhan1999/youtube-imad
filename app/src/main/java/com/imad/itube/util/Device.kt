package com.imad.itube.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.imad.itube.data.store.TvMode

/**
 * True on Android TV / Google TV and on the many TV boxes that don't say so properly.
 *
 * Checked in order: the system's TV UI mode, the Leanback / television features, and finally a
 * box without a touch screen (Android phones and tablets always have one; TV boxes and sticks
 * driven by a remote don't). Settings > TV layout overrides this when it guesses wrong.
 */
fun Context.isTelevision(): Boolean {
    val ui = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    if (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val pm = packageManager
    if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
    if (pm.hasSystemFeature("android.software.leanback_only")) return true
    if (pm.hasSystemFeature("android.hardware.type.television")) return true
    if (pm.hasSystemFeature("com.google.android.tv.installed")) return true
    // No touch screen and no phone radio: a box or stick driven by a remote.
    val touch = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    val phone = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    if (!touch && !phone) return true
    // Many cheap boxes report a touch screen anyway but keep "tv" in their build characteristics.
    val characteristics = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, "ro.build.characteristics") as? String
    }.getOrNull().orEmpty()
    if (characteristics.split(',').any { it.trim() == "tv" }) return true
    return false
}

/** The layout to use for [mode]: the user's choice, or the detected one for Auto. */
fun Context.useTvLayout(mode: TvMode): Boolean = when (mode) {
    TvMode.On -> true
    TvMode.Off -> false
    TvMode.Auto -> isTelevision()
}
