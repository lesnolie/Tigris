package com.leslie.tvhome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Visual only: its containing settings row is the single accessible, focusable switch. */
internal class UpdateSwitch(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val motion = Tween()
    private var position = 0f
    var checked = false
        set(value) {
            if (field == value) return
            field = value
            val from = position
            motion.start(200) { p ->
                position = lerp(from, if (value) 1f else 0f, p)
                invalidate()
            }
        }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        val inset = dp(2).toFloat()
        rect.set(inset, inset, width - inset, height - inset)
        val r = rect.height() / 2
        paint.color = Color.rgb(lerp(110f, 47f, position).toInt(),
            lerp(118f, 195f, position).toInt(), lerp(130f, 108f, position).toInt())
        canvas.drawRoundRect(rect, r, r, paint)
        val knob = r - dp(2)
        val x = lerp(rect.left + r, rect.right - r, position)
        paint.color = Color.argb(30, 0, 0, 0)
        canvas.drawCircle(x, rect.centerY() + dp(1), knob, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(x, rect.centerY(), knob, paint)
    }

    override fun onDetachedFromWindow() { motion.cancel(); super.onDetachedFromWindow() }
}
