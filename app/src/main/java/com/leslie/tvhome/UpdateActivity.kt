package com.leslie.tvhome

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import android.graphics.Color
import android.view.Gravity
import android.view.View

class UpdateActivity : Activity() {
    companion object { @Volatile var visible = false }
    private lateinit var status: TextView
    private lateinit var column: LinearLayout
    private var busy = false
    private val progressBars = mutableMapOf<String, ProgressBar>()
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (!isDestroyed && ::status.isInitialized) renderStatus()
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
            view.tweenScale(if (focused) 1.035f else 1f, 180)
        }
        column.addView(view, LinearLayout.LayoutParams(-1, dp(49)).apply { bottomMargin = dp(10) })
        return view
    }
    private fun renderStatus() {
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
        content.addView(info, LinearLayout.LayoutParams(0, -2, 0.46f).apply { marginEnd = dp(36) })
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        val scroll = ScrollView(this).apply {
            clipChildren = false; clipToPadding = false; isVerticalScrollBarEnabled = false
            addView(column)
            setOnScrollChangeListener { _, _, _, _, _ -> surfaces.forEach { it.invalidateSelf() } }
        }
        content.addView(scroll, LinearLayout.LayoutParams(0, -1, 0.54f))
        setContentView(root)
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
            button(I18n.text(R.string.i18n_updateactivity_013, p.name)) { work { Updates.install(applicationContext, p) } }
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
        }
        button(I18n.text(R.string.i18n_updateactivity_014)) {
            val pm = packageManager; val pkg = Updates.products[1].pkg
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            if (intent == null) Toast.makeText(this, I18n.text(R.string.i18n_updateactivity_015), Toast.LENGTH_LONG).show() else startActivity(intent)
        }
        button(I18n.text(R.string.i18n_updateactivity_016)) { finish() }
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
