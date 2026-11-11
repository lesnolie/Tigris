package com.leslie.tvhome

import android.graphics.Color

/**
 * Colour sets for the clock screensaver, one list per style. Each style remembers its own
 * choice ([Prefs.clockColor]); the index wraps so an old or out-of-range value still works.
 */
object ClockThemes {
    /** [hour], [minute], [second]: the tubes; [accent]: colons and the date line. */
    class Neon(val name: Int, val hour: Int, val minute: Int, val second: Int, val accent: Int)

    /** [page] behind the cards, [card] face, [digit] ink. */
    class Flip(val name: Int, val page: Int, val card: Int, val digit: Int)

    class Minimal(val name: Int, val background: Int, val ink: Int)

    /** [lit] and [unlit] segments on [background]; [glow]: lit segments cast light (backlit panels). */
    class Lcd(val name: Int, val background: Int, val lit: Int, val unlit: Int, val glow: Boolean)

    val neon = listOf(
        Neon(R.string.clock_color_cyber, Color.rgb(255, 46, 150), Color.rgb(30, 220, 255), Color.rgb(170, 120, 255), Color.rgb(255, 176, 64)),
        Neon(R.string.clock_color_glacier, Color.rgb(90, 200, 255), Color.rgb(40, 140, 255), Color.rgb(160, 235, 255), Color.rgb(200, 240, 255)),
        Neon(R.string.clock_color_sunset, Color.rgb(255, 110, 40), Color.rgb(255, 60, 120), Color.rgb(255, 200, 60), Color.rgb(255, 170, 90)),
        Neon(R.string.clock_color_aurora, Color.rgb(60, 255, 160), Color.rgb(40, 210, 220), Color.rgb(190, 110, 255), Color.rgb(140, 255, 200)),
        Neon(R.string.clock_color_warm, Color.rgb(255, 214, 160), Color.rgb(255, 214, 160), Color.rgb(255, 180, 110), Color.rgb(255, 196, 130)),
    )

    val flip = listOf(
        Flip(R.string.clock_color_graphite, Color.BLACK, Color.rgb(30, 30, 32), Color.rgb(236, 236, 236)),
        Flip(R.string.clock_color_ivory, Color.rgb(18, 18, 18), Color.rgb(236, 232, 222), Color.rgb(34, 34, 34)),
        Flip(R.string.clock_color_midnight, Color.rgb(6, 10, 22), Color.rgb(27, 36, 64), Color.rgb(226, 234, 255)),
        Flip(R.string.clock_color_retro, Color.BLACK, Color.rgb(34, 34, 34), Color.rgb(255, 154, 46)),
    )

    val minimal = listOf(
        Minimal(R.string.clock_color_night, Color.BLACK, Color.rgb(240, 240, 240)),
        Minimal(R.string.clock_color_paper, Color.rgb(244, 244, 242), Color.rgb(20, 20, 20)),
        Minimal(R.string.clock_color_amber, Color.BLACK, Color.rgb(255, 179, 71)),
        Minimal(R.string.clock_color_mint, Color.rgb(12, 26, 23), Color.rgb(150, 240, 205)),
    )

    val lcd = listOf(
        Lcd(R.string.clock_color_classic, Color.rgb(195, 196, 192), Color.rgb(27, 27, 27), Color.rgb(182, 183, 179), false),
        Lcd(R.string.clock_color_retro_green, Color.rgb(157, 176, 138), Color.rgb(30, 42, 24), Color.rgb(145, 163, 127), false),
        Lcd(R.string.clock_color_night_glow, Color.rgb(5, 8, 12), Color.rgb(56, 215, 255), Color.rgb(14, 24, 32), true),
        Lcd(R.string.clock_color_red, Color.BLACK, Color.rgb(255, 59, 48), Color.rgb(28, 8, 8), true),
    )

    fun count(style: String) = when (style) {
        Prefs.CLOCK_SAVER_FLIP -> flip.size
        Prefs.CLOCK_SAVER_MINIMAL -> minimal.size
        Prefs.CLOCK_SAVER_LCD -> lcd.size
        else -> neon.size
    }

    fun name(style: String, index: Int): Int = when (style) {
        Prefs.CLOCK_SAVER_FLIP -> flip[wrap(index, flip.size)].name
        Prefs.CLOCK_SAVER_MINIMAL -> minimal[wrap(index, minimal.size)].name
        Prefs.CLOCK_SAVER_LCD -> lcd[wrap(index, lcd.size)].name
        else -> neon[wrap(index, neon.size)].name
    }

    fun wrap(index: Int, size: Int) = ((index % size) + size) % size
}
