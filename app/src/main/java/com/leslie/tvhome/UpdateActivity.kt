package com.leslie.tvhome

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * One primary action per product, like tvOS app listings.
 * Installing two optional APKs is always initiated by the user.
 * PackageInstaller confirmation, errors and progress replace the same action
 * rather than creating permanent Continue/Retry/Open buttons.
 */
class UpdateActivity : Activity() {
    companion object { @Volatile var visible = false }

    private data class AppCard(
        val product: Updates.Product,
        val glass: FrostGlass,
        val detail: TextView,
        val progress: ProgressBar,
        val action: TextView,
        val badge: TextView,
    )

    private lateinit var column: LinearLayout
    private lateinit var options: ScrollView
    private lateinit var checkButton: TextView
    private lateinit var autoButton: TextView
    private val cards = LinkedHashMap<String, AppCard>()
    private val surfaces = mutableListOf<FrostGlass>()
    private var busy = false
    private var updateError: String? = null

    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread {
            if (!isFinishing && !isDestroyed && ::column.isInitialized) render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        show()
    }

    override fun onResume() {
        super.onResume()
        visible = true
        Updates.prefs(this).registerOnSharedPreferenceChangeListener(listener)
        if (::column.isInitialized) render()
    }

    override fun onPause() {
        visible = false
        Updates.prefs(this).unregisterOnSharedPreferenceChangeListener(listener)
        super.onPause()
    }

    private fun glass(view: View, radius: Int): FrostGlass {
        return FrostGlass(view, dp(radius).toFloat()).also {
            view.background = it
            surfaces += it
        }
    }

    private fun focusable(label: String, parent: LinearLayout, width: Int = -1,
                          action: () -> Unit): TextView {
        val view = text(17f, weight = 600).apply {
            this.text = label
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            isFocusable = true
            isClickable = true
            setOnClickListener { if (!busy) action() }
        }
        val effect = glass(view, 21)
        view.setOnFocusChangeListener { _, focused ->
            effect.focused = focused
            view.tweenScale(if (focused) 1.035f else 1f, 180)
            if (focused) reveal(view)
        }
        parent.addView(view, LinearLayout.LayoutParams(
            if (width > 0) dp(width) else -1, dp(50)
        ))
        return view
    }

    /** Nested focus targets are positioned in the scroll content, not their own card. */
    private fun reveal(view: View) {
        if (!::options.isInitialized) return
        options.post {
            if (isFinishing || isDestroyed) return@post
            // Header / footer actions are outside the scrolling card list.
            var ancestor: View? = view
            while (ancestor != null && ancestor !== column) {
                ancestor = ancestor.parent as? View
            }
            if (ancestor !== column) return@post
            val rect = Rect(0, 0, view.width, view.height)
            column.offsetDescendantRectToMyCoords(view, rect)
            val edge = dp(14)
            val top = rect.top - options.scrollY
            val bottom = rect.bottom - options.scrollY
            val target = when {
                top < edge -> rect.top - edge
                bottom > options.height - edge -> rect.bottom - (options.height - edge)
                else -> options.scrollY
            }.coerceIn(0, (column.height - options.height).coerceAtLeast(0))
            if (target != options.scrollY) options.smoothScrollTo(0, target)
        }
    }

    private fun iconFor(p: Updates.Product): FrameLayout {
        val isHome = !p.optional
        val isCast = p.pkg == OptionalAppPolicy.MIRACAST_PKG
        val start = if (isHome) 0xff335579.toInt() else if (isCast) 0xff365a83.toInt() else 0xffba4877.toInt()
        val end = if (isHome) 0xff111e34.toInt() else if (isCast) 0xff141d37.toInt() else 0xff3b1932.toInt()
        val frame = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
                cornerRadius = dp(16).toFloat()
            }
        }
        val glyph = TextView(this).apply {
            text = if (isHome) "T" else if (isCast) "◉" else "H"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        frame.addView(glyph, FrameLayout.LayoutParams(-1, -1))
        if (Updates.installed(this, p) != null) {
            runCatching { packageManager.getApplicationIcon(p.pkg) }.getOrNull()?.let { drawable ->
                frame.addView(ImageView(this).apply {
                    setImageDrawable(drawable)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                }, FrameLayout.LayoutParams(-1, -1))
            }
        }
        return frame
    }

    private fun category(p: Updates.Product) = when {
        !p.optional -> I18n.text(R.string.optional_category_home)
        p.pkg == OptionalAppPolicy.MIRACAST_PKG -> I18n.text(R.string.optional_category_cast)
        else -> I18n.text(R.string.optional_category_drama)
    }

    private fun card(p: Updates.Product) {
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(16), dp(22), dp(16))
            minimumHeight = dp(110)
        }
        val surface = glass(holder, 26)
        column.addView(holder, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(14)
        })

        holder.addView(iconFor(p), LinearLayout.LayoutParams(dp(70), dp(70)).apply {
            marginEnd = dp(20)
        })
        val summary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        holder.addView(summary, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = dp(16)
        })
        summary.addView(text(22f, weight = 600).apply {
            this.text = p.name
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        summary.addView(text(14f, Color.argb(185,255,255,255)).apply {
            this.text = category(p)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        val detail = text(14f, Color.argb(215,255,255,255))
        summary.addView(detail, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        summary.addView(progress, LinearLayout.LayoutParams(-1, dp(5)).apply {
            topMargin = dp(8)
        })

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        holder.addView(right, LinearLayout.LayoutParams(dp(134), -2))

        val action = focusable("", right, 134) { perform(p) }
        val badge = text(16f, weight = 600).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.argb(218,255,255,255))
            visibility = View.GONE
        }
        right.addView(badge, LinearLayout.LayoutParams(-1, dp(50)))
        action.setOnFocusChangeListener { _, focused ->
            action.background.let { (it as? FrostGlass)?.focused = focused }
            surface.focused = focused
            action.tweenScale(if (focused) 1.035f else 1f, 180)
            if (focused) reveal(action)
        }
        cards[p.pkg] = AppCard(p, surface, detail, progress, action, badge)
    }

    private fun label(action: AppUpdateCardState.Action): String {
        val id = when (action) {
            AppUpdateCardState.Action.GET -> R.string.optional_action_get
            AppUpdateCardState.Action.OPEN -> R.string.optional_action_open
            AppUpdateCardState.Action.UPDATE -> R.string.optional_action_update
            AppUpdateCardState.Action.CHECK -> R.string.i18n_updateactivity_007
            AppUpdateCardState.Action.CURRENT -> R.string.optional_action_current
            AppUpdateCardState.Action.DOWNLOADING -> R.string.optional_action_downloading
            AppUpdateCardState.Action.VERIFYING -> R.string.optional_action_verifying
            AppUpdateCardState.Action.WAITING -> R.string.optional_action_waiting
            AppUpdateCardState.Action.CONFIRM -> R.string.optional_action_confirm
            AppUpdateCardState.Action.RETRY -> R.string.optional_action_retry
        }
        return I18n.text(id)
    }

    private fun cardAction(p: Updates.Product): AppUpdateCardState.Action {
        val sp = Updates.prefs(this)
        val pkg = p.pkg
        return AppUpdateCardState.action(AppUpdateCardState.Snapshot(
            installed = Updates.installed(this, p) != null,
            optional = p.optional,
            checked = sp.contains(pkg + ".version"),
            updateAvailable = sp.getBoolean(pkg + ".available", false),
            phase = sp.getString(pkg + ".phase", "idle") ?: "idle",
            hasConfirmation = !sp.getString(pkg + ".confirmation", null).isNullOrBlank()
        ))
    }

    private fun render() {
        val sp = Updates.prefs(this)
        for ((pkg, item) in cards) {
            val phase = sp.getString(pkg + ".phase", "idle") ?: "idle"
            val percent = sp.getInt(pkg + ".progress", -1)
            val installedVersion = Updates.installed(this, item.product)
            val state = cardAction(item.product)
            val savedStatus = sp.getString(pkg + ".status", null)
            item.detail.text = when {
                phase == "download" && percent >= 0 -> I18n.text(R.string.update_download_percent, percent)
                !savedStatus.isNullOrBlank() -> savedStatus
                installedVersion != null -> I18n.text(R.string.i18n_updateactivity_001, installedVersion)
                else -> I18n.text(R.string.optional_status_not_installed)
            }
            val inProgress = phase in listOf("download", "verify", "stage")
            item.progress.visibility = if (inProgress) View.VISIBLE else View.GONE
            item.progress.isIndeterminate = percent !in 0..100 || phase != "download"
            if (phase == "download" && percent in 0..100) item.progress.progress = percent

            val enabled = AppUpdateCardState.actionable(state)
            item.action.visibility = if (enabled) View.VISIBLE else View.GONE
            item.badge.visibility = if (enabled) View.GONE else View.VISIBLE
            if (enabled) item.action.text = label(state) else item.badge.text = label(state)
        }
        autoButton.text = I18n.text(R.string.optional_auto_update) + "  ·  " +
            (if (sp.getBoolean("auto", false)) I18n.text(R.string.i18n_updateactivity_009)
             else I18n.text(R.string.i18n_updateactivity_010))
        if (!::checkButton.isInitialized) return
        checkButton.alpha = if (busy) 0.6f else 1f
    }

    private fun perform(p: Updates.Product) {
        when (cardAction(p)) {
            AppUpdateCardState.Action.OPEN -> {
                val launch = packageManager.getLeanbackLaunchIntentForPackage(p.pkg)
                    ?: packageManager.getLaunchIntentForPackage(p.pkg)
                if (launch != null) startActivity(launch)
                else Toast.makeText(this, I18n.text(R.string.optional_app_not_installed), Toast.LENGTH_LONG).show()
            }
            AppUpdateCardState.Action.CONFIRM -> {
                if (!Updates.confirm(this, p)) Toast.makeText(
                    this, I18n.text(R.string.update_no_confirmation), Toast.LENGTH_LONG
                ).show()
            }
            AppUpdateCardState.Action.GET, AppUpdateCardState.Action.UPDATE,
            AppUpdateCardState.Action.RETRY -> work {
                if (Updates.prefs(this).getString(p.pkg + ".phase", "") == "failed") {
                    Updates.cancelPending(applicationContext, p)
                }
                Updates.check(applicationContext)
                Updates.install(applicationContext, p)
            }
            AppUpdateCardState.Action.CHECK -> work { Updates.check(applicationContext) }
            else -> Unit
        }
    }

    private fun show() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(20, 27, 43)) }
        val background = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(background, FrameLayout.LayoutParams(-1, -1))

        val screenW = resources.displayMetrics.widthPixels
        val panelW = minOf((screenW - dp(48)).coerceAtLeast(dp(320)), dp(1110))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(30), dp(22), dp(30), dp(22))
        }
        glass(panel, 32)
        root.addView(panel, FrameLayout.LayoutParams(panelW, -1, Gravity.CENTER).apply {
            topMargin = dp(28); bottomMargin = dp(28)
        })
        setContentView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        panel.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        header.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = dp(20)
        })
        titleBlock.addView(text(31f, weight = 600).apply {
            this.text = I18n.text(R.string.i18n_updateactivity_003)
        })
        titleBlock.addView(text(15f, Color.argb(200,255,255,255)).apply {
            this.text = I18n.text(R.string.optional_apps_subtitle)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        // A small secondary global command; the main focus is the three app cards.
        val checkActions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(checkActions, LinearLayout.LayoutParams(-2, -2))
        checkButton = focusable(I18n.text(R.string.i18n_updateactivity_007), checkActions, 156) {
            work { Updates.check(applicationContext) }
        }

        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(22))
        }
        options = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = true; clipToPadding = false
            isFillViewport = false
            addView(column)
            setOnScrollChangeListener { _, _, _, _, _ -> surfaces.forEach { it.invalidateSelf() } }
        }
        panel.addView(options, LinearLayout.LayoutParams(-1, 0, 1f))
        for (p in Updates.products) card(p)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(16), dp(6), dp(3))
        }
        panel.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        autoButton = focusable("", bottom, 284) {
            val sp = Updates.prefs(this)
            sp.edit().putBoolean("auto", !sp.getBoolean("auto", false)).apply()
            render()
        }
        val spacer = View(this)
        bottom.addView(spacer, LinearLayout.LayoutParams(0, dp(1), 1f))
        focusable(I18n.text(R.string.i18n_updateactivity_016), bottom, 128) { finish() }

        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            val permissions = LinearLayout(this)
            panel.addView(permissions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            focusable(I18n.text(R.string.i18n_updateactivity_011), permissions) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")))
                }.onFailure {
                    Toast.makeText(this, I18n.text(R.string.i18n_updateactivity_012),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
        render()
        checkButton.requestFocus()

        // Compute glass texture away from D-pad input thread.
        Thread {
            val prefs = Prefs(this)
            val photo = if (Wallpapers.isPhoto(prefs.background)) {
                val walls = Wallpapers(this, Wallpapers.sourceFor(prefs.background))
                walls.current()?.let { walls.forHome(it) }
            } else null
            val source = photo ?: Backgrounds.render(
                if (Wallpapers.isPhoto(prefs.background)) "green" else prefs.background
            )
            val blurred = Wallpapers.blurred(source, darken = 0.35f)
            runOnUiThread {
                if (!isDestroyed) {
                    background.setImageBitmap(blurred)
                    surfaces.forEach { it.texture(blurred) }
                }
            }
        }.start()
    }

    private fun work(block: () -> Unit) {
        if (busy) return
        busy = true
        checkButton.alpha = 0.6f
        Updates.executor.execute {
            val error = runCatching(block).exceptionOrNull()
            runOnUiThread {
                busy = false
                if (!isDestroyed) {
                    if (error != null) {
                        Toast.makeText(this, error.message ?: I18n.text(R.string.i18n_updates_024),
                            Toast.LENGTH_LONG).show()
                    }
                    render()
                }
            }
        }
    }
}
