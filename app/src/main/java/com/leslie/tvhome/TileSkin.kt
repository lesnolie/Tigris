package com.leslie.tvhome

import android.graphics.Color

/**
 * One skin for every app tile: a frost-grey card that reads as the same family on dark, light,
 * photo and video wallpapers. Keep every colour, radius and alpha here; no per-app colours.
 */
internal object TileSkin {
    /** The requested base #E1E5EA; the two-stop gradient averages to this. */
    val FILL: Int = 0xFFE1E5EA.toInt()
    val TOP: Int = 0xFFE5E8EC.toInt()
    val BOTTOM: Int = 0xFFDDE2E8.toInt()

    /** A hairline that remains visible when the wallpaper behind a tile is white. */
    val BORDER: Int = Color.argb(0x80, 0xAE, 0xB7, 0xC2)
    val FOCUS_RING: Int = Color.argb(0xFF, 0xFF, 0xFF, 0xFF)
    val FOCUS_GLOW: Int = Color.argb(0xD8, 0xEE, 0xF5, 0xFF)

    /** The gear is a light-on-dark glyph; on this light tile it needs a medium grey. */
    val SETTINGS_GEAR: Int = Color.rgb(0x76, 0x7E, 0x89)

    const val RADIUS_DP = 11f
    const val BORDER_DP = 1f
    const val FOCUS_RING_DP = 2.5f
    const val GLOW_DP = 5f
    const val GLOW_BLUR_DP = 8f
    const val GLOW_PAD_DP = 26

    const val SHADOW_REST_ALPHA = 0.22f
    const val SHADOW_FOCUS_ALPHA = 0.5f
    const val GLOW_FOCUS_ALPHA = 0.9f
}
