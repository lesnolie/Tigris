package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Test

class DockLayoutTest {
    @Test fun existingUsersKeepTheOriginalFiveColumns() {
        assertEquals(5, DockLayout.DEFAULT_COLUMNS)
        assertEquals(5, DockLayout.normalized(0))
        assertEquals(5, DockLayout.normalized(-1))
        assertEquals(5, DockLayout.normalized(7))
        assertEquals(5, DockLayout.normalized(5))
    }

    @Test fun sixIsExplicitAndTogglingIsReversible() {
        assertEquals(6, DockLayout.normalized(6))
        assertEquals(6, DockLayout.next(5))
        assertEquals(5, DockLayout.next(6))
    }

    @Test fun bothLayoutsFitTheOriginalScreenWidth() {
        val screen = 1920
        val side = 112
        val gap = 56
        assertEquals(294, DockLayout.tileWidth(screen, side, gap, 5))
        assertEquals(236, DockLayout.tileWidth(screen, side, gap, 6))
        assertEquals(1, DockLayout.tileWidth(100, 80, 56, 6))
    }

    @Test fun dockBottomPositionRemainsConstant() {
        val baseTop = 804
        val heightFive = 165
        val heightSix = 132
        assertEquals(baseTop, DockLayout.dockTop(baseTop, heightFive, heightFive))
        assertEquals(baseTop + heightFive, DockLayout.dockTop(baseTop, heightFive, heightSix) + heightSix)
    }
}
