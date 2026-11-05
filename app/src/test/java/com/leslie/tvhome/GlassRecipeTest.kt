package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class GlassRecipeTest {
    @Test fun darkThemeForcesLightInkEvenOverBrightWallpaper() {
        val tone = GlassMaterial.tone(0.9f, 1f, darkTheme = true)
        val recipe = GlassMaterial.recipe(tone)
        assertFalse(tone.light)
        assertEquals(0.38f, recipe.tintAlpha, 0.001f)
        assertEquals(0.8f, recipe.brightness, 0.001f)
        assertEquals(1f / 3f, recipe.edgeStrength, 0.001f)
    }

    @Test fun darkBackdropInLightThemeHasItsOwnRecipe() {
        val tone = GlassMaterial.tone(0.1f, 1f)
        val recipe = GlassMaterial.recipe(tone)
        assertEquals(0xff101216.toInt(), tone.tint)
        assertEquals(0.30f, recipe.tintAlpha, 0.001f)
        assertEquals(1.7f, recipe.saturation, 0.001f)
        assertEquals(0.85f, recipe.brightness, 0.001f)
    }

    @Test fun removingTintAlsoRemovesBrightnessAdjustment() {
        for (darkTheme in listOf(false, true)) {
            val recipe = GlassMaterial.recipe(GlassMaterial.tone(0.8f, 1f, darkTheme = darkTheme), 0f)
            assertEquals(0f, recipe.tintAlpha, 0.001f)
            assertEquals(1f, recipe.brightness, 0.001f)
            assertEquals(1f, recipe.saturation, 0.001f)
        }
    }
}
