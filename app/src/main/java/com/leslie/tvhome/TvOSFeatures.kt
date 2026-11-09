package com.leslie.tvhome

import android.app.Activity
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

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

/** Search only installed Android applications. Voice uses a system recognizer if available;
 * it does not claim universal media search, Apple Siri, or Apple TV search indexing. */
class TvOSSearchActivity : Activity() {
    private lateinit var results: LinearLayout
    private lateinit var query: EditText
    private val apps by lazy { AppRepo(this).allApps().filter { it.folder == null && it.input == null } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(60), dp(38), dp(60), dp(32))
            setBackgroundColor(0xFF12151F.toInt())
        }
        page.addView(TextView(this).apply {
            text = getString(R.string.tvos_search_title)
            textSize = 30f
            setTextColor(Color.WHITE)
        })
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        query = EditText(this).apply {
            isSingleLine = true
            textSize = 22f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFFBBC6D5.toInt())
            hint = getString(R.string.tvos_search_hint)
            background = GradientDrawable().apply {
                setColor(0xFF303849.toInt())
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(20), dp(15), dp(20), dp(15))
        }
        controls.addView(query, LinearLayout.LayoutParams(0, -2, 1f))
        val voice = TextView(this).apply {
            text = getString(R.string.tvos_search_voice)
            textSize = 17f
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setTextColor(Color.WHITE)
            setPadding(dp(22), dp(16), dp(22), dp(16))
            setOnClickListener { openVoice() }
        }
        controls.addView(voice)
        page.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(ScrollView(this).apply {
            fillViewport = true
            addView(results)
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(18) })
        setContentView(page)
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render(s.toString())
            override fun afterTextChanged(s: Editable?) {}
        })
        render("")
        query.requestFocus()
    }

    private fun render(text: String) {
        results.removeAllViews()
        val normalized = text.trim()
        val choices = apps.filter { normalized.isBlank() ||
            it.label.contains(normalized, true) || it.pkg.contains(normalized, true)
        }.sortedBy { it.label.lowercase(Locale.getDefault()) }.take(35)
        if (choices.isEmpty()) {
            results.addView(TextView(this).apply {
                this.text = getString(R.string.tvos_search_empty)
                textSize = 19f
                setTextColor(Color.WHITE)
            })
        }
        for (app in choices) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                setPadding(dp(18), dp(11), dp(18), dp(11))
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat() }
            }
            val icon = ImageView(this).apply {
                setImageDrawable(runCatching { packageManager.getActivityIcon(app.component) }.getOrNull())
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(45), dp(45)))
            row.addView(TextView(this).apply {
                this.text = app.label
                setTextColor(Color.WHITE)
                textSize = 19f
            }, LinearLayout.LayoutParams(-1, -2).apply { marginStart = dp(20) })
            row.setOnFocusChangeListener { v, on ->
                v.background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(if (on) 0xFF475673.toInt() else Color.TRANSPARENT)
                }
            }
            row.setOnClickListener {
                AppRepo(this).launchIntent(app)?.let {
                    RecentApps.record(this, app.pkg)
                    startActivity(it)
                    finish()
                }
            }
            results.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        }
    }

    private fun openVoice() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            startActivityForResult(intent, 103)
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, R.string.tvos_search_voice_unavailable, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("System recognizer API for Android 9+ TV compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 103 && resultCode == RESULT_OK) {
            query.setText(data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty())
        }
    }
}
