package com.leslie.tvhome

import android.app.Activity
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Small, bounded local history: does not use hidden task APIs or request screenshots of
 * another app. UsageStats augments it only when the user already granted usage access.
 */
internal object RecentApps {
    private const val STORE = "tigris_recent_apps"
    private const val KEY = "packages"
    private const val KEY_DISMISSED = "dismissed_cards"

    fun record(ctx: Context, pkg: String) {
        if (pkg == ctx.packageName || pkg.startsWith("input:") || pkg.contains(':')) return
        val sp = ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val merged = (listOf(pkg) + sp.getString(KEY, "").orEmpty().split('|'))
            .filter { it.isNotBlank() }.distinct().take(20)
        // An explicit relaunch makes a previously hidden switcher card visible again.
        val dismissed = sp.getStringSet(KEY_DISMISSED, emptySet()).orEmpty().toMutableSet()
        dismissed.remove(pkg)
        sp.edit().putString(KEY, merged.joinToString("|"))
            .putStringSet(KEY_DISMISSED, dismissed).apply()
    }

    /** Remove only the switcher card. Ordinary launchers cannot end other apps' tasks. */
    fun dismiss(ctx: Context, pkg: String) {
        val sp = ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val saved = sp.getString(KEY, "").orEmpty().split('|').filter { it.isNotBlank() && it != pkg }
        val dismissed = sp.getStringSet(KEY_DISMISSED, emptySet()).orEmpty().toMutableSet()
        dismissed.add(pkg)
        sp.edit().putString(KEY, saved.joinToString("|"))
            .putStringSet(KEY_DISMISSED, dismissed).apply()
    }

    private fun dismissed(ctx: Context): Set<String> =
        ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getStringSet(KEY_DISMISSED, emptySet()).orEmpty().toSet()

    private fun stored(ctx: Context): List<String> =
        ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty().split('|')

    private fun resolve(available: List<AppEntry>, observed: List<String>, stored: List<String>,
                        dismissed: Set<String>): List<AppEntry> {
        val byPkg = available.filter { it.folder == null && it.input == null }
            .associateBy { it.pkg }
        return RecentAppOrder.merge(observed, stored, byPkg.keys, dismissed).mapNotNull { byPkg[it] }
    }

    /** Fast, permission-free first paint. Only apps previously opened from Tigris are shown. */
    fun saved(ctx: Context, available: List<AppEntry>): List<AppEntry> =
        resolve(available, emptyList(), stored(ctx), dismissed(ctx))

    /** Potentially slow system event scan. This must only be called from a worker thread. */
    fun recent(ctx: Context, available: List<AppEntry>): List<AppEntry> {
        val availablePackages = available.filter { it.folder == null && it.input == null }
            .map { it.pkg }.toSet()
        val lastSeen = HashMap<String, Long>()
        try {
            val manager = ctx.getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val events = manager.queryEvents(now - 2L * 24 * 60 * 60 * 1000, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                if (pkg != null && pkg in availablePackages &&
                    (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)) {
                    // Store one timestamp per installed package instead of every event.
                    lastSeen[pkg] = maxOf(lastSeen[pkg] ?: Long.MIN_VALUE, event.timeStamp)
                }
            }
        } catch (_: Exception) {
            // No usage access, firmware differences and revoked permission are all safe.
        }
        val observed = lastSeen.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .map { it.key }
        return resolve(available, observed, stored(ctx), dismissed(ctx))
    }
}

/** Android-safe recent-app launcher: visually resembles a tvOS card switcher but
 * does not pretend to access privileged task snapshots or force-stop other apps. */
class AppSwitcherActivity : Activity() {
    private var interacted = false
    private var content: View? = null
    private var closing = false
    private var instant = false

    override fun onUserInteraction() {
        super.onUserInteraction()
        interacted = true
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm = resources.displayMetrics
        val cardW = (dm.widthPixels * 0.30f).toInt()
        val cardH = (cardW * 9f / 16f).toInt()
        val iconSize = (cardH * 0.46f).toInt()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(54), dp(40), dp(54), dp(44))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF141A26.toInt(), 0xFF0B0E15.toInt()))
        }
        page.addView(title(getString(R.string.tvos_recents_title), 30f))
        page.addView(title(getString(R.string.tvos_recents_help), 15f).apply {
            setTextColor(0xFFAEB9D1.toInt())
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val cards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            setPadding(dp(24), 0, dp(24), 0)
        }
        val scroll = HorizontalScrollView(this).apply {
            clipToPadding = false
            clipChildren = false
            isHorizontalScrollBarEnabled = false
            addView(cards)
        }
        val empty = title(getString(R.string.tvos_recents_empty), 20f)
        page.addView(scroll, LinearLayout.LayoutParams(-1, cardH + dp(84)).apply { topMargin = dp(30) })
        page.addView(empty, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(30) })
        setContentView(page)
        content = page
        // Enter from the centre like tvOS; no external XML window transition.
        page.scaleX = 0.92f
        page.scaleY = 0.92f
        page.alpha = 0f
        page.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(220).setInterpolator(Motion.ease).start()

        // The saved list appears immediately. Optional system usage history is read
        // off the UI thread, and never reorders a list after the remote is used.
        val available = AppRepo(this).allApps()
        var presented: List<String>? = null
        var shown: List<AppEntry> = emptyList()
        fun render(list: List<AppEntry>, focusIndex: Int? = null) {
            shown = list
            val packages = list.map { it.pkg }
            if (packages == presented) return
            presented = packages
            cards.removeAllViews()
            for (app in list) {
                val drawable = runCatching { packageManager.getActivityIcon(app.component) }.getOrNull()
                val screen = FrameLayout(this).apply {
                    background = cardBackground(cardBrandColor(drawable))
                    clipToOutline = true
                }
                screen.addView(ImageView(this).apply {
                    setImageDrawable(drawable)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    isFocusable = true
                    isClickable = true
                    clipChildren = false
                    clipToPadding = false
                    scaleX = 0.9f
                    scaleY = 0.9f
                    alpha = 0.62f
                    addView(screen, LinearLayout.LayoutParams(cardW, cardH))
                    addView(title(app.label, 18f).apply {
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }, LinearLayout.LayoutParams(cardW, -2).apply { topMargin = dp(14) })
                }
                card.setOnFocusChangeListener { v, on ->
                    v.animate().cancel()
                    v.animate().scaleX(if (on) 1f else 0.9f)
                        .scaleY(if (on) 1f else 0.9f)
                        .alpha(if (on) 1f else 0.62f)
                        .setDuration(200).setInterpolator(Motion.ease).start()
                    screen.elevation = dp(if (on) 22 else 5).toFloat()
                    (screen.background as? GradientDrawable)?.setStroke(
                        dp(if (on) 3 else 0), if (on) Color.WHITE else Color.TRANSPARENT)
                    if (on) {
                        val center = v.left + v.width / 2 - scroll.width / 2
                        scroll.smoothScrollTo(center.coerceAtLeast(0), 0)
                    }
                }
                // Up dismisses the visible card, not the Android process or task.
                card.setOnKeyListener { _, keyCode, event ->
                    if (keyCode != KeyEvent.KEYCODE_DPAD_UP) false
                    else {
                        if (event.action == KeyEvent.ACTION_UP) {
                            val formerIndex = shown.indexOfFirst { it.pkg == app.pkg }
                            if (formerIndex >= 0) {
                                RecentApps.dismiss(this, app.pkg)
                                render(shown.filter { it.pkg != app.pkg },
                                    formerIndex.coerceAtMost(shown.size - 2).coerceAtLeast(0))
                            }
                        }
                        true
                    }
                }
                card.setOnClickListener {
                    AppRepo(this).launchIntent(app)?.let { intent ->
                        RecentApps.record(this, app.pkg)
                        // Grow out of this card, matching the launcher tile behaviour.
                        val opts = android.app.ActivityOptions
                            .makeScaleUpAnimation(card, 0, 0, card.width, card.height).toBundle()
                        startActivity(intent, opts)
                        HomeReturnHandoff.set(app.pkg)
                        finishInstant()
                    }
                }
                cards.addView(card, LinearLayout.LayoutParams(cardW, -2).apply { marginEnd = dp(30) })
            }
            scroll.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            if (focusIndex != null) cards.getChildAt(focusIndex)?.requestFocus()
            else if (!interacted) cards.getChildAt(0)?.requestFocus()
        }
        render(RecentApps.saved(this, available))
        Thread({
            val latest = RecentApps.recent(this, available)
            runOnUiThread {
                if (!isFinishing && !isDestroyed && !interacted) render(latest)
            }
        }, "tigris-recent-events").start()
    }

    /** Average a (mostly opaque) icon into a dark, brand-tinted card surface. */
    private fun cardBrandColor(d: android.graphics.drawable.Drawable?): Int {
        if (d == null) return BrandTilePolicy.brandColor(0xFF3A4A66.toInt())
        return try {
            val s = 24
            val bmp = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, s, s)
            d.draw(android.graphics.Canvas(bmp))
            var r = 0
            var g = 0
            var b = 0
            var n = 0
            for (y in 0 until s) for (x in 0 until s) {
                val c = bmp.getPixel(x, y)
                if (Color.alpha(c) < 40) continue
                r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++
            }
            bmp.recycle()
            if (n == 0) BrandTilePolicy.brandColor(0xFF3A4A66.toInt())
            else BrandTilePolicy.brandColor(Color.rgb(r / n, g / n, b / n))
        } catch (_: Exception) {
            BrandTilePolicy.brandColor(0xFF3A4A66.toInt())
        }
    }

    private fun cardBackground(base: Int): GradientDrawable {
        val light = blend(base, Color.WHITE, 0.18f)
        val dark = blend(base, Color.BLACK, 0.42f)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(light, dark)).apply {
            cornerRadius = dp(18).toFloat()
        }
    }

    private fun blend(a: Int, b: Int, f: Float) = Color.rgb(
        (Color.red(a) * (1f - f) + Color.red(b) * f).toInt(),
        (Color.green(a) * (1f - f) + Color.green(b) * f).toInt(),
        (Color.blue(a) * (1f - f) + Color.blue(b) * f).toInt())

    private fun finishNow() { super.finish() }

    private fun finishInstant() {
        instant = true
        finish()
    }

    override fun finish() {
        if (instant || closing) { finishNow(); return }
        closing = true
        val v = content
        if (v == null) { finishNow(); return }
        v.animate().scaleX(0.92f).scaleY(0.92f).alpha(0f)
            .setDuration(160).setInterpolator(Motion.ease)
            .withEndAction { finishNow() }.start()
    }

    private fun title(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
}
