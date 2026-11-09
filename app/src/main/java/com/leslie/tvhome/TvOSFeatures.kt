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

    override fun onUserInteraction() {
        super.onUserInteraction()
        interacted = true
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(54), dp(44), dp(54), dp(48))
            setBackgroundColor(0xFF10131D.toInt())
        }
        page.addView(title(getString(R.string.tvos_recents_title), 31f))
        page.addView(title(getString(R.string.tvos_recents_help), 15f).apply {
            setTextColor(0xFFAEB9D1.toInt())
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val cards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
        }
        val scroll = HorizontalScrollView(this).apply {
            clipToPadding = false
            clipChildren = false
            isHorizontalScrollBarEnabled = false
            addView(cards)
        }
        val empty = title(getString(R.string.tvos_recents_empty), 20f)
        page.addView(scroll, LinearLayout.LayoutParams(-1, dp(365)).apply { topMargin = dp(32) })
        page.addView(empty, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(42) })
        setContentView(page)

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
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isFocusable = true
                    isClickable = true
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(18).toFloat()
                        setColor(0xFF303B50.toInt())
                    }
                }
                val icon = ImageView(this).apply {
                    val drawable = runCatching { packageManager.getActivityIcon(app.component) }.getOrNull()
                    setImageDrawable(drawable)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                tile.addView(icon, LinearLayout.LayoutParams(dp(142), dp(142)))
                tile.addView(title(app.label, 17f).apply {
                    maxLines = 2
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })
                tile.setOnFocusChangeListener { v, on ->
                    v.animate().cancel()
                    v.animate().scaleX(if (on) 1.08f else 1f)
                        .scaleY(if (on) 1.08f else 1f)
                        .translationY(if (on) -dp(9).toFloat() else 0f)
                        .setDuration(180).start()
                    v.elevation = dp(if (on) 16 else 2).toFloat()
                    (v.background as? GradientDrawable)?.setStroke(dp(if (on) 2 else 0), if (on) Color.WHITE else Color.TRANSPARENT)
                    if (on) {
                        val center = v.left + v.width / 2 - scroll.width / 2
                        scroll.smoothScrollTo(center.coerceAtLeast(0), 0)
                    }
                }
                // Up dismisses the visible card, not the Android process or task.
                // Consume both DOWN and UP so focus does not jump to the page heading.
                tile.setOnKeyListener { _, keyCode, event ->
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
                tile.setOnClickListener {
                    AppRepo(this).launchIntent(app)?.let { intent ->
                        RecentApps.record(this, app.pkg)
                        startActivity(intent)
                        // HomeActivity did not perform this launch; hand it the app so a
                        // later return can keep the Dock position instead of snapping home.
                        HomeReturnHandoff.set(app.pkg)
                        finish()
                    }
                }
                cards.addView(tile, LinearLayout.LayoutParams(dp(290), dp(300)).apply { marginEnd = dp(32) })
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

    private fun title(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
}
