package com.leslie.tvhome

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** App-owned dialogs share the page's glass, while the TV keeps its native keyboard. */
internal class SettingsDialog(
    private val activity: Activity,
    scene: Bitmap,
    title: String,
    message: String? = null,
    body: View? = null,
    compact: Boolean = false,
    primary: String,
    private val confirm: () -> Boolean = { true },
    secondary: String = activity.getString(android.R.string.cancel),
    private val secondaryAction: (() -> Unit)? = null,
) : Dialog(activity) {
    private val root = FrameLayout(activity)
    private val glass = UpdateGlass(root)
    private val opener = activity.currentFocus
    private val firstButton: TextView

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        root.setBackgroundColor(Color.argb(130, 3, 7, 15))
        root.clipChildren = false
        val width = minOf(activity.dp(650), activity.resources.displayMetrics.widthPixels - activity.dp(80))
        val height = minOf(activity.dp(if (compact) 242 else 410),
            activity.resources.displayMetrics.heightPixels - activity.dp(76))
        val frame = FrameLayout(activity).apply {
            background = rounded(Color.argb(230, 12, 22, 36), activity.dp(28).toFloat())
            clipChildren = false
        }
        root.addView(frame, FrameLayout.LayoutParams(width, height, Gravity.CENTER))
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(22))
            clipChildren = false; clipToPadding = false
        }
        frame.addView(panel, FrameLayout.LayoutParams(-1, -1))
        glass.control(panel, 28, content = true)
        panel.addView(activity.text(24f, weight = 600).apply { text = title },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = activity.dp(18) })
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
        }
        if (!message.isNullOrBlank()) content.addView(activity.text(15f, Color.argb(220,255,255,255)).apply {
            text = message
            setLineSpacing(activity.dp(4).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = if (body != null) activity.dp(16) else 0 })
        if (body != null) content.addView(body, LinearLayout.LayoutParams(-1, -2))
        if (body is LinearLayout) for (i in 0 until body.childCount) {
            val choice = body.getChildAt(i)
            if (choice.isFocusable) {
                val material = glass.control(choice, 14)
                choice.setOnFocusChangeListener { _, on ->
                    material.focus(on)
                    choice.tweenScale(if (on) 1.025f else 1f, 200)
                }
            }
        }
        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(10))
            setPadding(dp(6), dp(4), dp(6), dp(8))
            clipToPadding = true
            addView(content)
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(6), dp(14), dp(6), dp(3))
        }
        panel.addView(actions, LinearLayout.LayoutParams(-1, -2))
        fun action(label: String, click: () -> Unit): TextView {
            val button = activity.text(16f, weight = 500).apply {
                text = label; gravity = Gravity.CENTER
                isFocusable = true; isClickable = true
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener { click() }
            }
            val material = glass.control(button, 22)
            button.setOnFocusChangeListener { _, on ->
                material.focus(on)
                button.tweenScale(if (on) 1.045f else 1f, 220)
            }
            actions.addView(button, LinearLayout.LayoutParams(0, activity.dp(46), 1f).apply {
                if (actions.childCount > 0) marginStart = activity.dp(16)
            })
            return button
        }
        action(secondary) { dismiss(); secondaryAction?.invoke() }
        firstButton = action(primary) { if (confirm()) dismiss() }
        setContentView(root)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        glass.texture(scene)
        setOnDismissListener {
            glass.close()
            if (!activity.isFinishing && !activity.isDestroyed && opener?.isAttachedToWindow == true) opener.requestFocus()
        }
    }

    override fun show() {
        super.show()
        window?.setLayout(-1, -1)
        firstButton.requestFocus()
    }
}
