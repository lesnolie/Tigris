package com.leslie.tvhome

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure geometry for app artwork. Android drawables are rendered by AppRepo. */
internal object AppTileArtwork {
    enum class Mode { COVER, CONTAIN }
    data class Placement(val left: Int, val top: Int, val right: Int, val bottom: Int, val mode: Mode) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /** Ignore tiny raster "banners" that look poor blown up on a television. */
    fun usableBanner(width: Int, height: Int): Boolean = width >= 220 && height >= 120

    /**
     * Cover only when <=10% of either edge would be cropped. Otherwise fit within
     * a modest inset to preserve logos, subtitles and unusual TV banner artwork.
     */
    fun banner(width: Int, height: Int, tileWidth: Int, tileHeight: Int): Placement {
        require(width > 0 && height > 0 && tileWidth > 0 && tileHeight > 0)
        val originalRatio = width.toFloat() / height
        val tileRatio = tileWidth.toFloat() / tileHeight
        val retained = min(originalRatio / tileRatio, tileRatio / originalRatio)
        val mode = if (retained >= 0.90f) Mode.COVER else Mode.CONTAIN
        val insetX = if (mode == Mode.CONTAIN) min(14, tileWidth / 10) else 0
        val insetY = if (mode == Mode.CONTAIN) min(12, tileHeight / 10) else 0
        val scale = if (mode == Mode.COVER)
            max(tileWidth.toFloat() / width, tileHeight.toFloat() / height)
        else min((tileWidth - 2 * insetX).toFloat() / width,
            (tileHeight - 2 * insetY).toFloat() / height)
        val renderedW = max(1, (width * scale).roundToInt())
        val renderedH = max(1, (height * scale).roundToInt())
        val left = (tileWidth - renderedW) / 2
        val top = (tileHeight - renderedH) / 2
        return Placement(left, top, left + renderedW, top + renderedH, mode)
    }

    /** Centrally spaced logo: enough optical weight without filling the entire tile. */
    fun iconSize(tileHeight: Int): Int = max(1, (tileHeight * 0.64f).roundToInt())
}
