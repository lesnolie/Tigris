package com.leslie.tvhome

import kotlin.math.max
import kotlin.math.min

/** No device-dependent state: decisions are reproducible in JVM tests and on TV. */
internal object BrandTilePolicy {
    enum class Source { BANNER, ADAPTIVE, ICON, PLACEHOLDER, CLASSIC }

    fun select(
        globalClassic: Boolean, requested: String, hasBanner: Boolean,
        adaptiveCanSeparate: Boolean, hasIcon: Boolean
    ): Source {
        if (globalClassic || requested == Prefs.TILE_CLASSIC) return Source.CLASSIC
        if (requested != Prefs.TILE_ICON && hasBanner) return Source.BANNER
        if (adaptiveCanSeparate) return Source.ADAPTIVE
        return if (hasIcon) Source.ICON else Source.PLACEHOLDER
    }

    /** Banners lower than this are too blurry for large TV focus magnification. */
    fun goodBanner(w: Int, h: Int): Boolean =
        w >= 240 && h >= 132 && w.toFloat() / max(1, h) in 1.2f..3.2f

    /**
     * Adaptive foregrounds are only separate visual layers if enough clear pixels exist,
     * but their visible content is neither vanishingly small nor bleeding to every edge.
     */
    fun validForeground(visible: Int, total: Int, touchesEverySide: Boolean): Boolean =
        total > 0 && visible.toFloat() / total in 0.04f..0.72f && !touchesEverySide

    /** Keep the icon's real colour (white stays white); only lift near-black for contrast. */
    fun brandColor(rgb: Int): Int {
        fun ch(shift: Int) = (rgb ushr shift) and 0xFF
        var r = ch(16); var g = ch(8); var b = ch(0)
        if (r < 20 && g < 20 && b < 20) { r = 40; g = 44; b = 52 }
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    /** Geometry is decided independently of wallpaper, avoiding per-frame sampling. */
    fun renderedHeight(width: Int, aspect: String): Int =
        if (aspect == Prefs.TILE_TVOS) width * 3 / 5 else width * 9 / 16
}
