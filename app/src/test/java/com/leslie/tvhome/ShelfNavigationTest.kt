package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class ShelfNavigationTest {
    @Test fun wrapsManualNavigationInBothDirections() {
        assertEquals(0, ShelfNavigation.step(2, 3, 1))
        assertEquals(2, ShelfNavigation.step(0, 3, -1))
        assertEquals(0, ShelfNavigation.step(0, 1, 1))
        assertEquals(-1, ShelfNavigation.step(0, 0, 1))
    }

    @Test fun acceptsOnlyCurrentFocusedRequest() {
        assertTrue(ShelfNavigation.accepts(7, 7, 11, 11, "a", "a", true, false))
        assertFalse(ShelfNavigation.accepts(6, 7, 11, 11, "a", "a", true, false))
        assertFalse(ShelfNavigation.accepts(7, 7, 10, 11, "a", "a", true, false))
        assertFalse(ShelfNavigation.accepts(7, 7, 11, 11, "a", "b", true, false))
        assertFalse(ShelfNavigation.accepts(7, 7, 11, 11, "a", null, true, false))
        assertFalse(ShelfNavigation.accepts(7, 7, 11, 11, "a", "a", false, false))
        assertFalse(ShelfNavigation.accepts(7, 7, 11, 11, "a", "a", true, true))
    }
}
