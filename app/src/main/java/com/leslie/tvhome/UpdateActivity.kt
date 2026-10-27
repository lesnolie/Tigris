package com.leslie.tvhome

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.View

class UpdateActivity : Activity() {
    companion object {
        @Volatile var visible = false
        private const val BUTTON_HEIGHT_DP = 49
        private const val BUTTON_GAP_DP = 10
        private const val FOCUS_SCALE = 1.035f
    }
    private lateinit var status: TextView
    private lateinit var column: LinearLayout
    private lateinit var options: ScrollView
    private var busy = false
    private val progressBars = mutableMapOf<String, ProgressBar>()
    private val launchButtons = mutableMapOf<String, TextView>()
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        // Installer progress is written from a worker; never update TV focus/UI off the main thread.
        runOnUiThread {
            if (!isFinishing && !isDestroyed && ::status.isInitialized) renderStatus()
        }
    }
    private val surfaces = mutableListOf<FrostGlass>()
    override fun onCreate(state: Bundle?) { super.onCreate(state); show() }
    override fun onResume() {
        super.onResume(); visible = true
        Updates.prefs(this).registerOnSharedPreferenceChangeListener(listener)
        if (::status.isInitialized) renderStatus()
    }
    override fun onPause() {
        visible = false
        Updates.prefs(this).unregisterOnSharedPreferenceChangeListener(listener)
        super.onPause()
    }
    private fun button(label: String, action: () -> Unit): TextView {
        val view = text(17f, weight = 600).apply {
            text = label; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), 0, dp(22), 0)
            isFocusable = true; isClickable = true
            setOnClickListener { action() }
        }
        val glass = FrostGlass(view, dp(23).toFloat()); surfaces += glass; view.background = glass
        view.setOnFocusChangeListener { _, focused ->
            glass.focused = focused
            view.tweenScale(if (focused) FOCUS_SCALE else 1f, 180)
            if (focused) reveal(view)
        }
        column.addView(view, LinearLayout.LayoutParams(-1, dp(BUTTON_HEIGHT_DP)).apply { bottomMargin = dp(BUTTON_GAP_DP) })
        return view
    }

    /**
     * Keep the visible rows aligned to their buttons. Android's default ScrollView focus handling
     * scrolls only the few pixels needed to expose a focused row, leaving a clipped sliver of the
     * previous row at the opposite edge. Move by whole row increments instead.
     */
    private fun reveal(view: View) {
        if (!::options.isInitialized) return
        options.post {
            if (isFinishing || isDestroyed) return@post
            val topEdge = column.paddingTop
            val bottomEdge = options.height - column.paddingBottom
            val top = view.top - options.scrollY
            val bottom = view.bottom - options.scrollY
            val target = when {
                top < topEdge -> view.top - topEdge
                bottom > bottomEdge -> view.bottom - bottomEdge
                else -> options.scrollY
            }.coerceIn(0, (column.height - options.height).coerceAtLeast(0))
            if (target != options.scrollY) options.smoothScrollTo(0, target)
        }
    }

    /** Leave enough room for focus zoom, then make the remaining viewport an exact number of rows. */
    private fun sizeVisibleRows() {
        if (!::options.isInitialized || options.height <= 0 || column.childCount == 0) return
        val buttonHeight = dp(BUTTON_HEIGHT_DP)
        val gap = dp(BUTTON_GAP_DP)
        val stride = buttonHeight + gap
        val focusInset = dp(1).coerceAtLeast(1)
        val fitByHeight = ((options.height - 2 * focusInset + gap) / stride).coerceAtLeast(1)
        val visibleRows = minOf(column.childCount, fitByHeight)
        val rowsHeight = visibleRows * buttonHeight + (visibleRows - 1) * gap
        val remainder = (options.height - rowsHeight).coerceAtLeast(0)
        val top = remainder / 2
        val bottom = remainder - top
        if (column.paddingTop != top || column.paddingBottom != bottom) {
            column.setPadding(column.paddingLeft, top, column.paddingRight, bottom)
        }
    }
    private fun renderStatus() {
        launchButtons.forEach { (pkg, action) ->
            val available = Updates.installed(this, Updates.products.first { it.pkg == pkg }) != null
            action.isEnabled = available
            action.alpha = if (available) 1f else 0.48f
        }
        val sp = Updates.prefs(this)
        progressBars.forEach { (pkg, bar) ->
            val phase = sp.getString(pkg + ".phase", "idle")
            val percent = sp.getInt(pkg + ".progress", -1)
            val working = phase in listOf("download", "verify", "stage", "pending")
            bar.visibility = if (working) View.VISIBLE else View.GONE
            bar.isIndeterminate = percent < 0
            if (percent >= 0) bar.progress = percent
        }
        status.text = Updates.products.joinToString("\n\n") { p ->
            p.name + "\n" + Updates.prefs(this).getString(p.pkg + ".status", Updates.installed(this, p)?.let { I18n.text(R.string.i18n_updateactivity_001, it) } ?: I18n.text(R.string.i18n_updateactivity_002))
        }
    }
    private fun show() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(20, 27, 43)) }
        val background = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(background, FrameLayout.LayoutParams(-1, -1))
        val heading = text(29f, weight = 600).apply { text = I18n.text(R.string.i18n_updateactivity_003); gravity = Gravity.CENTER }
        root.addView(heading, FrameLayout.LayoutParams(-1, dp(70)).apply { topMargin = dp(18) })
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false
            setPadding(dp(56), dp(98), dp(56), dp(22))
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(26), dp(28), dp(26), dp(28))
        }
        val infoGlass = FrostGlass(info, dp(30).toFloat()); info.background = infoGlass; surfaces += infoGlass
        info.addView(text(36f, weight = 600).apply { text = I18n.text(R.string.i18n_updateactivity_004) })
        info.addView(text(17f, Color.argb(200,255,255,255)).apply {
            text = I18n.text(R.string.i18n_updateactivity_005); setPadding(0, dp(12), 0, dp(20))
        })
        status = text(16f).apply { setLineSpacing(dp(4).toFloat(), 1f) }
        info.addView(status)
        for (p in Updates.products) {
            info.addView(text(13f).apply { text = p.name }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
            progressBars[p.pkg] = bar
            info.addView(bar, LinearLayout.LayoutParams(-1, dp(12)))
        }
        renderStatus()
        info.addView(text(13f, Color.argb(175,255,255,255)).apply {
            text = I18n.text(R.string.i18n_updateactivity_006)
            setPadding(0, dp(24), 0, 0)
        })
        info.addView(text(15f, Color.argb(215,255,255,255)).apply {
            text = I18n.text(R.string.optional_apps_help)
            setPadding(0, dp(20), 0, 0)
        })
        content.addView(info, LinearLayout.LayoutParams(0, -2, 0.46f).apply { marginEnd = dp(36) })
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(dp(12), 0, dp(12), 0)
        }
        options = object : ScrollView(this) {
            // Scrolling is driven by reveal() so DPAD focus always lands on a whole row.
            override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean) = false
        }.apply {
            clipChildren = true; clipToPadding = true; isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            addView(column)
            setOnScrollChangeListener { _, _, _, _, _ -> surfaces.forEach { it.invalidateSelf() } }
        }
        val optionsViewport = FrameLayout(this).apply {
            // The row zoom and the scrolling child must stay inside the right-hand viewport.
            clipChildren = true; clipToPadding = true
            addView(options, FrameLayout.LayoutParams(-1, -1))
        }
        content.addView(optionsViewport, LinearLayout.LayoutParams(0, -1, 0.54f))
        setContentView(root)
        options.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> sizeVisibleRows() }
        options.post { sizeVisibleRows() }
        Thread {
            val prefs = Prefs(this)
            val source = if (Wallpapers.isPhoto(prefs.background)) {
                val walls = Wallpapers(this, Wallpapers.sourceFor(prefs.background))
                walls.current()?.let { walls.forHome(it) }
            } else null
            val image = source ?: Backgrounds.render(if (Wallpapers.isPhoto(prefs.background)) "green" else prefs.background)
            val blurred = Wallpapers.blurred(image, darken = 0.35f)
            runOnUiThread {
                if (!isDestroyed) { background.setImageBitmap(blurred); surfaces.forEach { it.texture(blurred) } }
            }
        }.start()
        val check = button(I18n.text(R.string.i18n_updateactivity_007)) { work { Updates.check(applicationContext) } }
        lateinit var auto: TextView
        fun autoLabel() = I18n.text(R.string.i18n_updateactivity_008) + if (Updates.prefs(this).getBoolean("auto", false)) I18n.text(R.string.i18n_updateactivity_009) else I18n.text(R.string.i18n_updateactivity_010)
        auto = button(autoLabel()) {
            val prefs = Updates.prefs(this)
            prefs.edit().putBoolean("auto", !prefs.getBoolean("auto", false)).apply()
            auto.text = autoLabel()
        }
        check.requestFocus()
        button(I18n.text(R.string.i18n_updateactivity_011)) {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
                .onFailure { Toast.makeText(this, I18n.text(R.string.i18n_updateactivity_012), Toast.LENGTH_LONG).show() }
        }
        for (p in Updates.products) {
            button(I18n.text(R.string.i18n_updateactivity_013, p.name)) { work {
                if (Updates.installed(applicationContext, p) == null) {
                    // The third-party APK is never fetched until the user presses this button.
                    Updates.check(applicationContext)
                }
                Updates.install(applicationContext, p)
            } }
            button(I18n.text(R.string.update_continue_product, p.name)) {
                if (!Updates.confirm(this, p)) Toast.makeText(this, I18n.text(R.string.update_no_confirmation), Toast.LENGTH_LONG).show()
            }
            button(I18n.text(R.string.update_retry_product, p.name)) {
                work {
                    Updates.cancelPending(applicationContext, p)
                    Updates.check(applicationContext)
                    if (Updates.prefs(this).getBoolean(p.pkg + ".available", false)) Updates.install(applicationContext, p)
                }
            }
            if (p.optional) {
                val open = button(I18n.text(R.string.optional_app_open, p.name)) {
                    val intent = packageManager.getLeanbackLaunchIntentForPackage(p.pkg)
                        ?: packageManager.getLaunchIntentForPackage(p.pkg)
                    if (intent == null) {
                        Toast.makeText(this, I18n.text(R.string.optional_app_not_installed), Toast.LENGTH_LONG).show()
                    } else startActivity(intent)
                }
                launchButtons[p.pkg] = open
                open.isEnabled = Updates.installed(this, p) != null
                open.alpha = if (open.isEnabled) 1f else 0.48f
            }
        }
        button(I18n.text(R.string.i18n_updateactivity_016)) { finish() }
        // A final bottom margin would add a non-row scroll offset and break the edge alignment.
        (column.getChildAt(column.childCount - 1).layoutParams as LinearLayout.LayoutParams).apply {
            bottomMargin = 0
            column.getChildAt(column.childCount - 1).layoutParams = this
        }
    }
    private fun work(block: () -> Unit) {
        if (busy) return
        busy = true; status.text = I18n.text(R.string.i18n_updateactivity_017)
        Updates.executor.execute {
            val error = runCatching(block).exceptionOrNull()
            runOnUiThread {
                busy = false
                if (!isDestroyed) { renderStatus(); if (error != null) status.append("\n" + error.message) }
            }
        }
    }
}
