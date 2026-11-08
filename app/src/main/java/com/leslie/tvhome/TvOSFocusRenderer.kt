package com.leslie.tvhome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

/**
 * A restrained specular band, rendered by Android's hardware-accelerated Canvas.
 * Construct shaders once; only shift the local matrix while the focus Tween is active.
 * No image decode, bitmap allocation, or wall/video sampling on the frame path.
 */
internal class TvOSSpecular(ctx: Context) : View(ctx) {
    private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-1f, 0f, 1f, 0f,
            intArrayOf(Color.TRANSPARENT, Color.argb(82, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    private val gradient = light.shader!!
    private val matrix = Matrix()
    private var center = 0.4f
    private var visible = 0f
    private var lean = 0f

    fun pose(x: Float, strength: Float, tilt: Float) {
        center = x
        visible = strength.coerceIn(0f, 1f)
        lean = tilt
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visible < 0.002f || width <= 0 || height <= 0) return
        // A soft diagonal band with 35% of the tile's width; transformed entirely on GPU.
        val half = width * 0.32f
        matrix.reset()
        matrix.setScale(half, 1f)
        matrix.postRotate(lean * 0.5f, center * width, height / 2f)
        matrix.postTranslate(center * width, 0f)
        gradient.setLocalMatrix(matrix)
        light.alpha = (visible * 120).toInt().coerceIn(0, 120)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), light)
    }
}

/** Interruptible D-pad-aware visual state; no idle frame loop and no OpenGL dependency. */
internal class TvOSFocusRenderer(
    private val card: FrameLayout,
    private val foreground: ImageView,
    private val specular: TvOSSpecular,
    private val shadow: ImageView,
    private val border: View,
    private val focusRing: View,
    private val glow: View,
    private val label: TextView,
    private val shadowPad: Int,
) {
    private val tween = Tween()
    private var lightX = 0.37f
    private var lightAlpha = 0f
    private var lightTilt = 0f

    fun focus(active: Boolean, dx: Int, dy: Int, showLabel: Boolean, detailed: Boolean) {
        // Tween.start() cancels its previous frame callback and samples these actual
        // properties, so a long held D-pad cannot queue stale transitions.
        val fromScale = card.scaleX
        val fromRx = card.rotationX
        val fromRy = card.rotationY
        val fromZ = card.translationZ
        val fromFgX = foreground.translationX
        val fromFgY = foreground.translationY
        val fromShadow = shadow.alpha
        val fromShadowY = shadow.translationY
        val fromBorder = border.alpha
        val fromRing = focusRing.alpha
        val fromGlow = glow.alpha
        val fromLabel = label.alpha
        val fromLightX = lightX
        val fromLightAlpha = lightAlpha
        val fromLightTilt = lightTilt

        val tilted = active && detailed
        val entryRx = if (tilted && dy != 0) -dy * 3.25f else fromRx
        val entryRy = if (tilted && dx != 0) dx * 3.25f else fromRy
        // Begin in direction of travel, land almost face-on. The layer parallax is real
        // only when a validated adaptive-foreground bitmap exists.
        if (tilted) {
            card.rotationX = entryRx
            card.rotationY = entryRy
            foreground.translationX = if (dx != 0) -dx * 7f else fromFgX
            foreground.translationY = if (dy != 0) -dy * 5f else fromFgY
        }
        val initialRx = card.rotationX
        val initialRy = card.rotationY
        val initialFgX = foreground.translationX
        val initialFgY = foreground.translationY
        val goalScale = if (active) TileCell.FOCUS_SCALE else 1f
        val goalZ = if (active) card.resources.displayMetrics.density * 5f else 0f
        val goalLightX = if (active) 0.5f + dx * 0.12f else 0.5f
        val goalLight = if (active) { if (detailed) 0.72f else 0.24f } else 0f
        val targetShadow = if (active) TileSkin.SHADOW_FOCUS_ALPHA else TileSkin.SHADOW_REST_ALPHA
        val targetShadowY = -shadowPad + (if (active) 14 else 4) * card.resources.displayMetrics.density
        val duration = if (active) 230L else 170L

        card.cameraDistance = card.width.coerceAtLeast(1) * 14f
        tween.start(duration, if (active) Motion.spring else Motion.ease) { amount ->
            val p = amount.coerceIn(0f, 1f)
            val settle = Motion.ease.getInterpolation(p)
            fun mix(a: Float, b: Float) = a + (b - a) * p
            fun smooth(a: Float, b: Float) = a + (b - a) * settle
            card.scaleX = mix(fromScale, goalScale)
            card.scaleY = card.scaleX
            card.rotationX = smooth(initialRx, 0f)
            card.rotationY = smooth(initialRy, 0f)
            card.translationZ = mix(fromZ, goalZ)
            foreground.translationX = smooth(initialFgX, 0f)
            foreground.translationY = smooth(initialFgY, if (active && detailed) -2f else 0f)
            shadow.alpha = mix(fromShadow, targetShadow)
            shadow.scaleX = card.scaleX
            shadow.scaleY = card.scaleY
            shadow.translationY = mix(fromShadowY, targetShadowY)
            border.alpha = mix(fromBorder, if (active) 0f else 1f)
            border.scaleX = card.scaleX
            border.scaleY = card.scaleX
            focusRing.alpha = mix(fromRing, if (active) 0.92f else 0f)
            focusRing.scaleX = card.scaleX
            focusRing.scaleY = card.scaleX
            glow.alpha = mix(fromGlow, if (active && detailed) 0.28f else 0f)
            glow.scaleX = card.scaleX
            glow.scaleY = card.scaleX
            label.alpha = mix(fromLabel, if (active && showLabel) 1f else 0f)
            lightX = mix(fromLightX, goalLightX)
            lightAlpha = mix(fromLightAlpha, goalLight)
            lightTilt = smooth(fromLightTilt, if (active && detailed) dx * 5f else 0f)
            specular.pose(lightX, lightAlpha, lightTilt)
        }
    }
}
