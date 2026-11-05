package com.leslie.tvhome

import android.graphics.Paint
import android.graphics.BlendMode
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.View
import android.content.Context
import android.graphics.Canvas
import android.widget.TextView

internal class GlassTextView(context: Context) : TextView(context) {
    var glassInkPaint: Paint? = null
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val paint = glassInkPaint
        if (paint == null) super.onDraw(canvas)
        else {
            val saved = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), paint)
            super.onDraw(canvas)
            canvas.restoreToCount(saved)
        }
    }
}

internal object GlassInk {
    private val modes = java.util.WeakHashMap<View, Int>()
    private val multiply = Paint().apply {
        if (android.os.Build.VERSION.SDK_INT >= 29) blendMode = BlendMode.MULTIPLY
        else xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
    }
    private val lighter = Paint().apply {
        if (android.os.Build.VERSION.SDK_INT >= 29) blendMode = BlendMode.PLUS
        else xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    fun apply(view: View, enabled: Boolean, light: Boolean) {
        val mode = if (!enabled) 0 else if (light) 1 else 2
        if ((modes[view] ?: 0) == mode) return
        modes[view] = mode
        if (view is GlassTextView) {
            view.glassInkPaint = if (!enabled) null else if (light) multiply else lighter
            return
        }
        view.setLayerType(if (enabled) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,
            if (!enabled) null else if (light) multiply else lighter)
    }
}
