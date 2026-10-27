package com.leslie.tvhome

/**
 * tvOS Home uses one consistent column count for its glass Dock and app grid.
 * Existing installations keep five; six is an explicit opt-in in Home Settings.
 * This is shared UI layout policy, never a vendor or installation adapter.
 */
object DockLayout {
    const val DEFAULT_COLUMNS = 5
    const val SIX_COLUMNS = 6

    fun normalized(columns: Int): Int = if (columns == SIX_COLUMNS) SIX_COLUMNS else DEFAULT_COLUMNS

    fun next(columns: Int): Int =
        if (normalized(columns) == DEFAULT_COLUMNS) SIX_COLUMNS else DEFAULT_COLUMNS

    fun tileWidth(screenWidth: Int, side: Int, gap: Int, columns: Int): Int {
        val count = normalized(columns)
        return ((screenWidth - side * 2 - gap * (count - 1)) / count).coerceAtLeast(1)
    }

    /** Keep the Dock's lower edge in the same position when switching to six smaller tiles. */
    fun dockTop(baseTop: Int, defaultTileHeight: Int, selectedTileHeight: Int): Int =
        baseTop + defaultTileHeight - selectedTileHeight
}
