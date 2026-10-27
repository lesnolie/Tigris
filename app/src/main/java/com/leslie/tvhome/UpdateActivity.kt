package com.leslie.tvhome

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import java.util.concurrent.Executors

/** tvOS settings composition: quiet content, with refracting glass on interactive controls. */
class UpdateActivity : Activity() {
    companion object { @Volatile var visible = false }

    private data class AppCard(
        val product: Updates.Product,
        val detail: TextView,
        val progress: ProgressBar,
        val action: TextView,
        val badge: TextView,
    )

    private lateinit var column: LinearLayout
    private lateinit var options: ScrollView
    private lateinit var checkButton: TextView
    private lateinit var autoButton: LinearLayout
    private lateinit var autoSwitch: UpdateSwitch
    private lateinit var autoState: TextView
    private lateinit var permissionButton: TextView
    private lateinit var glass: UpdateGlass
    private val cards = LinkedHashMap<String, AppCard>()
    private val artwork = Executors.newSingleThreadExecutor()
    private var busy = false

    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread {
            if (!isFinishing && !isDestroyed && ::autoButton.isInitialized) render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); show() }

    override fun onResume() {
        super.onResume()
        visible = true
        Updates.prefs(this).registerOnSharedPreferenceChangeListener(listener)
        if (::autoButton.isInitialized) render()
        if (::permissionButton.isInitialized) {
            permissionButton.visibility = if (packageManager.canRequestPackageInstalls()) View.GONE else View.VISIBLE
        }
        if (::glass.isInitialized) glass.refresh()
    }

    override fun onPause() {
        visible = false
        Updates.prefs(this).unregisterOnSharedPreferenceChangeListener(listener)
        super.onPause()
    }

    override fun onDestroy() {
        glass.close()
        artwork.shutdownNow()
        super.onDestroy()
    }

    private fun focus(view: View, on: Boolean) {
        (view.background as? UpdateGlass.Surface)?.focus(on)
        view.tweenScale(if (on) 1.055f else 1f, if (on) 260 else 200,
            if (on) Motion.spring else Motion.ease, z = if (on) dp(8).toFloat() else 0f)
    }

    private fun button(label: String, parent: LinearLayout, height: Int = 50,
                       action: () -> Unit): TextView {
        val view = text(16f, weight = 500).apply {
            text = label
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            isFocusable = true
            isClickable = true
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), Color.argb(150,0,0,0))
            setOnClickListener { action() }
        }
        glass.control(view)
        view.setOnFocusChangeListener { _, on -> focus(view, on) }
        parent.addView(view, LinearLayout.LayoutParams(-1, dp(height)))
        return view
    }

    /** Reveal the whole application, including its name/status, not just the nested button. */
    private fun reveal(holder: View) {
        options.post {
            if (isFinishing || isDestroyed) return@post
            val rect = Rect(0, 0, holder.width, holder.height)
            column.offsetDescendantRectToMyCoords(holder, rect)
            val edge = dp(12)
            val target = when {
                rect.top < options.scrollY + edge -> rect.top - edge
                rect.bottom > options.scrollY + options.height - edge -> rect.bottom - options.height + edge
                else -> options.scrollY
            }.coerceIn(0, (column.height - options.height).coerceAtLeast(0))
            options.smoothScrollTo(0, target)
        }
    }

    private fun iconFor(p: Updates.Product): View {
        if (!p.optional) return ImageView(this).apply {
            background = rounded(0x24ffffff, dp(12).toFloat())
            setImageResource(R.drawable.ic_software_update)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val installedIcon = runCatching { packageManager.getApplicationIcon(p.pkg) }.getOrNull()
        if (installedIcon != null) return ImageView(this).apply {
            setImageDrawable(installedIcon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val cast = p.pkg == OptionalAppPolicy.MIRACAST_PKG
        return ImageView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (cast) intArrayOf(0xff3f7d9d.toInt(), 0xff24485f.toInt())
                else intArrayOf(0xffab4c70.toInt(), 0xff663447.toInt())).apply {
                cornerRadius = dp(12).toFloat()
            }
            setImageResource(if (cast) R.drawable.ic_input else R.drawable.ic_apps)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
    }

    private fun section(title: Int) {
        column.addView(text(13f, Color.argb(165,255,255,255), weight = 500).apply {
            text = I18n.text(title)
        }, LinearLayout.LayoutParams(-1, -2).apply {
            marginStart = dp(4); topMargin = if (column.childCount == 0) 0 else dp(18)
            bottomMargin = dp(10)
        })
    }

    private fun card(p: Updates.Product) {
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(17))
            minimumHeight = dp(90)
            clipChildren = false
            clipToPadding = false
        }
        val material = glass.control(holder, 20, content = true)
        column.addView(holder, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        holder.addView(iconFor(p), LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(16) })

        val summary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        holder.addView(summary, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(12) })
        summary.addView(text(18f, weight = 500).apply {
            text = p.name
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        })
        val detail = text(13f, Color.argb(180,255,255,255)).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        summary.addView(detail, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x30ffffff)
            visibility = View.GONE
        }
        summary.addView(progress, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(8) })

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false; clipToPadding = false
        }
        holder.addView(right, LinearLayout.LayoutParams(dp(100), -2))
        val action = button("", right, 42) { if (!busy) perform(p) }
        val badge = text(13f, Color.argb(175,255,255,255)).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        right.addView(badge, LinearLayout.LayoutParams(-1, dp(42)))
        action.setOnFocusChangeListener { _, on ->
            focus(action, on)
            material.focus(on)
            if (on) reveal(holder)
        }
        cards[p.pkg] = AppCard(p, detail, progress, action, badge)
    }

    private fun label(action: AppUpdateCardState.Action): String = I18n.text(when (action) {
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
    })

    private fun cardAction(p: Updates.Product): AppUpdateCardState.Action {
        val sp = Updates.prefs(this)
        return AppUpdateCardState.action(AppUpdateCardState.Snapshot(
            installed = Updates.installed(this, p) != null,
            optional = p.optional,
            checked = sp.contains(p.pkg + ".version"),
            updateAvailable = sp.getBoolean(p.pkg + ".available", false),
            phase = sp.getString(p.pkg + ".phase", "idle") ?: "idle",
            hasConfirmation = !sp.getString(p.pkg + ".confirmation", null).isNullOrBlank()
        ))
    }

    private fun render() {
        val sp = Updates.prefs(this)
        for ((pkg, item) in cards) {
            val phase = sp.getString(pkg + ".phase", "idle") ?: "idle"
            val percent = sp.getInt(pkg + ".progress", -1)
            val version = Updates.installed(this, item.product)
            val state = cardAction(item.product)
            val saved = sp.getString(pkg + ".status", null)
            val release = sp.getString(pkg + ".version", "") ?: ""
            val current = version != null && saved in listOf(
                I18n.text(R.string.i18n_updates_026), I18n.text(R.string.i18n_updates_005, version, release))
            val status = when {
                phase == "download" && percent >= 0 -> I18n.text(R.string.update_download_percent, percent)
                current && !sp.getBoolean(pkg + ".available", false) ->
                    if (state == AppUpdateCardState.Action.CURRENT) null else label(AppUpdateCardState.Action.CURRENT)
                version != null && saved == "$version → $release" -> I18n.text(R.string.update_available_version, release)
                else -> saved
            }
            item.detail.text = when {
                version != null && !status.isNullOrBlank() -> I18n.text(R.string.update_version_status, version, status)
                version != null -> I18n.text(R.string.i18n_updateactivity_001, version)
                !status.isNullOrBlank() -> status
                else -> I18n.text(R.string.optional_status_not_installed)
            }
            val inProgress = phase in listOf("download", "verify", "stage")
            item.progress.visibility = if (inProgress) View.VISIBLE else View.GONE
            item.progress.isIndeterminate = percent !in 0..100 || phase != "download"
            if (phase == "download" && percent in 0..100) item.progress.progress = percent

            val enabled = AppUpdateCardState.actionable(state)
            // Hiding a focused operation must not let Android jump to an unrelated app action.
            if (!enabled && item.action.hasFocus()) checkButton.requestFocus()
            item.action.visibility = if (enabled) View.VISIBLE else View.GONE
            item.badge.visibility = if (enabled) View.GONE else View.VISIBLE
            item.action.text = label(state)
            item.action.contentDescription = I18n.text(R.string.update_version_status, item.product.name, label(state))
            item.badge.text = label(state)
        }
        val auto = sp.getBoolean("auto", false)
        autoSwitch.checked = auto
        autoState.text = I18n.text(if (auto) R.string.i18n_updateactivity_009 else R.string.i18n_updateactivity_010)
        autoButton.contentDescription = I18n.text(R.string.update_version_status, I18n.text(R.string.optional_auto_update), autoState.text)
        checkButton.text = I18n.text(if (busy) R.string.update_working else R.string.i18n_updateactivity_007)
        glass.refresh()
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
                if (!Updates.confirm(this, p)) Toast.makeText(this,
                    I18n.text(R.string.update_no_confirmation), Toast.LENGTH_LONG).show()
            }
            AppUpdateCardState.Action.GET, AppUpdateCardState.Action.UPDATE, AppUpdateCardState.Action.RETRY -> work {
                if (Updates.prefs(this).getString(p.pkg + ".phase", "") == "failed") Updates.cancelPending(applicationContext, p)
                Updates.check(applicationContext)
                Updates.install(applicationContext, p)
            }
            AppUpdateCardState.Action.CHECK -> work { Updates.check(applicationContext) }
            else -> Unit
        }
    }

    private fun show() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(20, 27, 38))
            clipChildren = false; clipToPadding = false
        }
        val background = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(background, FrameLayout.LayoutParams(-1, -1))
        glass = UpdateGlass(root)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(44), dp(38), dp(40), dp(30))
        }
        root.addView(body, FrameLayout.LayoutParams(-1, -1))

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(12), dp(16), dp(20), dp(16))
        }
        val leftScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = true
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(12))
            addView(left)
            setOnScrollChangeListener { _, _, _, _, _ -> glass.refresh() }
        }
        body.addView(leftScroll, LinearLayout.LayoutParams(0, -1, 0.37f).apply { marginEnd = dp(24) })
        left.addView(text(32f, weight = 600).apply { text = I18n.text(R.string.i18n_updateactivity_003) })
        left.addView(text(15f, Color.argb(175,255,255,255)).apply {
            text = I18n.text(R.string.update_page_description)
            setLineSpacing(dp(4).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        left.addView(text(13f, Color.argb(155,255,255,255)).apply {
            text = I18n.text(R.string.update_app_version, packageManager.getPackageInfo(packageName, 0).versionName ?: "")
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
        }
        left.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(36) })
        checkButton = button(I18n.text(R.string.i18n_updateactivity_007), controls) { work { Updates.check(applicationContext) } }

        autoButton = object : LinearLayout(this) {
            override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(info)
                info.className = "android.widget.Switch"
                info.isCheckable = true
                info.isChecked = Updates.prefs(context).getBoolean("auto", false)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(14), 0)
            isFocusable = true; isClickable = true
            clipChildren = false
            setOnClickListener {
                val sp = Updates.prefs(this@UpdateActivity)
                sp.edit().putBoolean("auto", !sp.getBoolean("auto", false)).apply()
            }
        }
        glass.control(autoButton)
        autoButton.setOnFocusChangeListener { _, on -> focus(autoButton, on) }
        controls.addView(autoButton, LinearLayout.LayoutParams(-1, dp(64)).apply { topMargin = dp(16) })
        val autoText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        autoButton.addView(autoText, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(10) })
        autoText.addView(text(14f, weight = 500).apply { text = I18n.text(R.string.i18n_updateactivity_008).trim() })
        autoState = text(11f, Color.argb(170,255,255,255))
        autoText.addView(autoState, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        autoSwitch = UpdateSwitch(this)
        autoButton.addView(autoSwitch, LinearLayout.LayoutParams(dp(42), dp(26)))
        left.addView(text(12f, Color.argb(150,255,255,255)).apply {
            text = I18n.text(R.string.update_auto_description)
            setLineSpacing(dp(3).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); marginStart = dp(4) })

        val back = LinearLayout(this).apply { clipChildren = false }
        left.addView(back, LinearLayout.LayoutParams(dp(108), -2).apply { topMargin = dp(26) })
        button(I18n.text(R.string.i18n_updateactivity_016), back, 42) { finish() }

        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(14), dp(12), dp(14), dp(16))
        }
        options = object : ScrollView(this) {
            override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean) = false
        }.apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = true
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(16))
            addView(column)
            setOnScrollChangeListener { _, _, _, _, _ -> glass.refresh() }
        }
        body.addView(options, LinearLayout.LayoutParams(0, -1, 0.63f))
        section(R.string.update_section_home)
        Updates.products.filter { !it.optional }.forEach { card(it) }
        section(R.string.update_section_optional)
        Updates.products.filter { it.optional }.forEach { card(it) }

        val permissions = LinearLayout(this).apply {
            clipChildren = false
            setPadding(dp(4), dp(8), dp(4), dp(6))
        }
        column.addView(permissions, LinearLayout.LayoutParams(-1, -2))
        permissionButton = button(I18n.text(R.string.i18n_updateactivity_011), permissions, 44) {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
                .onFailure { Toast.makeText(this, I18n.text(R.string.i18n_updateactivity_012), Toast.LENGTH_LONG).show() }
        }
        permissionButton.setOnFocusChangeListener { _, on ->
            focus(permissionButton, on)
            if (on) reveal(permissions)
        }
        permissionButton.visibility = if (packageManager.canRequestPackageInstalls()) View.GONE else View.VISIBLE
        setContentView(root)
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> glass.refresh() }
        render()
        checkButton.requestFocus()
        loadBackdrop(background)
    }

    private fun loadBackdrop(view: ImageView) {
        artwork.execute {
            val backdrop = SettingsBackdrop.load(this)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    view.setImageBitmap(backdrop)
                    glass.texture(backdrop)
                }
            }
        }
    }

    private fun work(block: () -> Unit) {
        if (busy) return
        busy = true
        render()
        Updates.executor.execute {
            val error = runCatching(block).exceptionOrNull()
            runOnUiThread {
                busy = false
                if (!isFinishing && !isDestroyed) {
                    if (error != null) Toast.makeText(this,
                        error.message ?: I18n.text(R.string.i18n_updates_024), Toast.LENGTH_LONG).show()
                    render()
                }
            }
        }
    }
}
