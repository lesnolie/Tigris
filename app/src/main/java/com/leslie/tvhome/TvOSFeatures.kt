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

    fun record(ctx: Context, pkg: String) {
        if (pkg == ctx.packageName || pkg.startsWith("input:") || pkg.contains(':')) return
        val sp = ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val merged = (listOf(pkg) + sp.getString(KEY, "").orEmpty().split('|'))
            .filter { it.isNotBlank() }.distinct().take(20)
        sp.edit().putString(KEY, merged.joinToString("|")).apply()
    }

    fun recent(ctx: Context, available: List<AppEntry>): List<AppEntry> {
        val byPkg = available.filter { it.folder == null && it.input == null }
            .associateBy { it.pkg }
        val saved = ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty().split('|')
        val observed = try {
            val manager = ctx.getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val events = manager.queryEvents(now - 2L * 24 * 60 * 60 * 1000, now)
            val event = UsageEvents.Event()
            val list = ArrayList<Pair<Long, String>>()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    list.add(event.timeStamp to event.packageName)
                }
            }
            list.sortedByDescending { it.first }.map { it.second }
        } catch (_: Exception) { emptyList() }
        return (observed + saved).distinct().mapNotNull { byPkg[it] }.take(12)
    }
}

/** Android-safe recent-app launcher: visually resembles a tvOS card switcher but
 * does not pretend to access privileged task snapshots or force-stop other apps. */
class AppSwitcherActivity : Activity() {
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
        val list = RecentApps.recent(this, AppRepo(this).allApps())
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
            tile.addView(icon, LinearLayout.LayoutParams(dp(110), dp(110)))
            tile.addView(title(app.label, 17f).apply {
                maxLines = 2
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })
            tile.setOnFocusChangeListener { v, on ->
                v.animate().cancel()
                v.animate().scaleX(if (on) 1.07f else 1f)
                    .scaleY(if (on) 1.07f else 1f).setDuration(180).start()
                v.elevation = dp(if (on) 14 else 2).toFloat()
                (v.background as? GradientDrawable)?.setStroke(dp(if (on) 2 else 0), if (on) Color.WHITE else Color.TRANSPARENT)
            }
            tile.setOnClickListener {
                AppRepo(this).launchIntent(app)?.let { intent ->
                    RecentApps.record(this, app.pkg)
                    startActivity(intent)
                    finish()
                }
            }
            cards.addView(tile, LinearLayout.LayoutParams(dp(205), dp(222)).apply { marginEnd = dp(24) })
        }
        val scroll = HorizontalScrollView(this).apply {
            clipToPadding = false
            clipChildren = false
            isHorizontalScrollBarEnabled = false
            addView(cards)
        }
        if (list.isEmpty()) {
            page.addView(title(getString(R.string.tvos_recents_empty), 20f),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(42) })
        } else {
            page.addView(scroll, LinearLayout.LayoutParams(-1, dp(292)).apply { topMargin = dp(32) })
            cards.getChildAt(0)?.requestFocus()
        }
        setContentView(page)
    }

    private fun title(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
}
