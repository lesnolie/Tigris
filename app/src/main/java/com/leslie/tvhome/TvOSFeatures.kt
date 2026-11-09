package com.leslie.tvhome

import android.app.Activity
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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

    /** Remove the switcher card (the caller may also force-stop the app). */
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

/**
 * iOS-style app switcher: large app-screenshot cards stacked over the desktop.
 *
 * A plain third-party launcher cannot read system task snapshots, so the wallpapers come
 * from the user-enabled accessibility service (see [ShotCapture]). Removing a card also
 * force-stops the app through Tigris's own local adb when authorized (see [AppCloser]).
 */
class AppSwitcherActivity : Activity() {
    private var interacted = false
    private var content: View? = null
    private var closing = false
    private var instant = false

    private lateinit var container: FrameLayout
    private lateinit var stack: FrameLayout
    private lateinit var caption: TextView
    private val cards = ArrayList<FrameLayout>()
    private val labels = ArrayList<TextView>()
    private var apps: List<AppEntry> = emptyList()
    private var selected = 0
    private var cardW = 0
    private var cardH = 0
    private var step = 0
    private var iconSize = 0

    override fun onUserInteraction() {
        super.onUserInteraction()
        interacted = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Translucent window so the desktop stays visible behind the stacked cards.
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val dm = resources.displayMetrics
        cardW = (dm.widthPixels * 0.80f).toInt()
        cardH = (cardW * 9f / 16f).toInt()
        step = (cardW * 0.16f).toInt()
        iconSize = (cardH * 0.34f).toInt()

        container = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x99101824.toInt(), 0xCC000000.toInt()))
        }
        stack = FrameLayout(this)
        container.addView(stack, FrameLayout.LayoutParams(-1, -1))
        // Only used for the empty-state message; the app name is drawn inside each card.
        caption = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            alpha = 0.92f
            visibility = View.GONE
        }
        container.addView(caption, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        setContentView(container)
        content = container
        container.scaleX = 0.94f
        container.scaleY = 0.94f
        container.alpha = 0f
        container.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(220).setInterpolator(Motion.ease).start()

        val available = AppRepo(this).allApps()
        apps = RecentApps.saved(this, available)
        buildCards()
        Thread({
            val latest = RecentApps.recent(this, available)
            runOnUiThread {
                if (!isFinishing && !isDestroyed && !interacted) {
                    apps = latest
                    buildCards()
                }
            }
        }, "tigris-recent-events").start()
    }

    /** Rebuild the stacked cards from [apps]; selected index is preserved when possible. */
    private fun buildCards() {
        stack.removeAllViews()
        cards.clear()
        labels.clear()
        for (app in apps) {
            val drawable = runCatching { packageManager.getActivityIcon(app.component) }.getOrNull()
            val shot = runCatching { ShotCapture.load(this, app.pkg) }.getOrNull()
            val card = FrameLayout(this).apply {
                background = cardBackground(cardBrandColor(drawable))
                clipToOutline = true
                isFocusable = false
                isClickable = false
            }
            if (shot != null) {
                card.addView(ImageView(this).apply {
                    setImageBitmap(shot)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }, FrameLayout.LayoutParams(-1, -1))
            } else {
                card.addView(ImageView(this).apply {
                    setImageDrawable(drawable)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
            }
            val name = TextView(this).apply {
                text = app.label
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                setShadowLayer(6f, 0f, 2f, Color.BLACK)
                visibility = View.INVISIBLE
            }
            card.addView(name, FrameLayout.LayoutParams(-1, -2,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(16) })
            labels.add(name)
            stack.addView(card, FrameLayout.LayoutParams(cardW, cardH, Gravity.CENTER))
            cards.add(card)
        }
        if (apps.isEmpty()) {
            caption.text = getString(R.string.tvos_recents_empty)
            caption.visibility = View.VISIBLE
        } else {
            caption.visibility = View.GONE
            if (selected !in apps.indices) selected = 0
        }
        applyPositions(animate = false)
    }

    /** Cascade the cards: the selected one is centred and on top, neighbours fan out behind. */
    private fun applyPositions(animate: Boolean) {
        for (i in cards.indices) {
            val card = cards[i]
            val delta = i - selected
            val ad = kotlin.math.abs(delta)
            val visible = ad <= 3
            val targetX = (delta * step).toFloat()
            val targetScale = if (delta == 0) 1f else kotlin.math.max(0.66f, 1f - ad * 0.11f)
            val targetY = if (delta == 0) 0f else (ad * dp(7)).toFloat()
            val targetAlpha = when {
                !visible -> 0f
                delta == 0 -> 1f
                else -> kotlin.math.max(0.30f, 0.92f - ad * 0.18f)
            }
            card.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            card.translationZ = (if (visible) 50 - ad else 0).toFloat()
            if (animate) {
                card.animate().cancel()
                card.animate().translationX(targetX).translationY(targetY)
                    .scaleX(targetScale).scaleY(targetScale).alpha(targetAlpha)
                    .setDuration(220).setInterpolator(Motion.ease).start()
            } else {
                card.translationX = targetX
                card.translationY = targetY
                card.scaleX = targetScale
                card.scaleY = targetScale
                card.alpha = targetAlpha
            }
        }
        if (apps.isEmpty()) {
            caption.text = getString(R.string.tvos_recents_empty)
            caption.visibility = View.VISIBLE
        } else {
            caption.visibility = View.GONE
        }
        for (i in labels.indices) labels[i].visibility = if (i == selected) View.VISIBLE else View.INVISIBLE
    }

    private fun moveSelection(delta: Int) {
        if (apps.isEmpty()) return
        val next = (selected + delta).coerceIn(0, apps.size - 1)
        if (next == selected) return
        selected = next
        applyPositions(animate = true)
    }

    private fun launchSelected() {
        val app = apps.getOrNull(selected) ?: return
        AppRepo(this).launchIntent(app)?.let { intent ->
            RecentApps.record(this, app.pkg)
            val src = cards.getOrNull(selected) ?: container
            val opts = android.app.ActivityOptions
                .makeScaleUpAnimation(src, 0, 0, src.width, src.height).toBundle()
            startActivity(intent, opts)
            HomeReturnHandoff.set(app.pkg)
            finishInstant()
        }
    }

    /** Up: animate the focused card away, then really close the app in the background. */
    private fun removeSelected() {
        val idx = selected
        val app = apps.getOrNull(idx) ?: return
        val card = cards.getOrNull(idx) ?: return
        card.animate().cancel()
        card.animate().translationY(-dp(180).toFloat()).alpha(0f)
            .scaleX(0.85f).scaleY(0.85f)
            .setDuration(200).setInterpolator(Motion.ease)
            .withEndAction {
                AppCloser.close(this, app.pkg)
                RecentApps.dismiss(this, app.pkg)
                ShotCapture.clear(this, app.pkg)
                apps = apps.filter { it.pkg != app.pkg }
                selected = selected.coerceIn(0, (apps.size - 1).coerceAtLeast(0))
                buildCards()
                if (apps.isEmpty()) finishInstant()
            }.start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (apps.isNotEmpty()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) moveSelection(-1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) moveSelection(1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) removeSelected()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP) launchSelected()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (event.action == KeyEvent.ACTION_UP) finish()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
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
            cornerRadius = dp(6).toFloat()
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
        v.animate().scaleX(0.94f).scaleY(0.94f).alpha(0f)
            .setDuration(150).setInterpolator(Motion.ease)
            .withEndAction { finishNow() }.start()
    }
}

/**
 * Best-effort real close of another app: kill its background processes, then force-stop it
 * through Tigris's own local adb (the same shell path the memory cleaner uses). Force-stop
 * removal is only possible when the user authorized local debugging; otherwise the kill is
 * still performed.
 */
internal object AppCloser {
    fun close(ctx: Context, pkg: String) {
        if (pkg.isEmpty() || pkg == ctx.packageName) return
        runCatching {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.killBackgroundProcesses(pkg)
        }
        Thread({
            runCatching { LocalAdb.shell(ctx.applicationContext, "am force-stop $pkg", 3000) }
        }, "tigris-close-app").start()
    }
}
