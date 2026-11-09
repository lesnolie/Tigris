package com.leslie.tvhome

import android.graphics.Color

/**
 * One horizontal tile: brand banner artwork fills the card; an app without a banner gets a
 * uniform white card with its launcher icon. No extra frame, border, ring or glow.
 */
internal object TileSkin {
    /** The uniform backdrop for apps that expose no usable TV banner. */
    val WHITE: Int = Color.WHITE

    /** The gear is a light-on-dark glyph; on the white tile it needs a medium grey. */
    val SETTINGS_GEAR: Int = Color.rgb(0x6E, 0x76, 0x82)

    /** Keep the existing tile radius; the icon itself supplies the shape. */
    const val RADIUS_DP = 11f

    const val SHADOW_REST_ALPHA = 0.18f
    const val SHADOW_FOCUS_ALPHA = 0.46f
    const val FOCUS_SCALE = 1.12f
}
