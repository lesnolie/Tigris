package com.leslie.tvhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.palette.graphics.Palette
import com.leslie.tvhome.tv.Input
import java.text.Collator
import java.util.Locale

data class AppEntry(
    val pkg: String,
    val label: String,
    val component: ComponentName,
    val leanback: Boolean,
    /** Set for a TV input pinned to the grid; then [pkg] is its [InputTile.key]. */
    val input: Input? = null,
    val folder: AppFolder? = null,
)

/** A TV input (HDMI) pinned to the home grid from the Control Center's input list. */
object InputTile {
    private const val PREFIX = "input:"
    private const val SEP = "\u001f"

    fun key(i: Input) = PREFIX + i.uri
    fun isInput(pkg: String) = pkg.startsWith(PREFIX)

    /** How it is kept in [Prefs.pinnedInputs]. */
    fun encode(i: Input) = listOf(i.uri, i.title, i.label).joinToString(SEP)

    fun decode(s: String): Input? {
        val p = s.split(SEP)
        return if (p.size == 3) Input(p[0], p[1], p[2], false) else null
    }
}

/** Enumerates launchable apps and renders their 5:3 tvOS-style tiles. */
class AppRepo(private val ctx: Context) {
    private val pm: PackageManager = ctx.packageManager
    private val prefs = Prefs(ctx)
    private val tiles = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun allApps(): List<AppEntry> {
        val byPkg = LinkedHashMap<String, AppEntry>()
        fun add(list: List<ResolveInfo>, leanback: Boolean) {
            for (ri in list) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == ctx.packageName && (!leanback || ai.name == HomeActivity::class.java.name)) continue
                // Tigris lists two screens of its own (settings, Wallpaper); each gets a key of its own.
                val key = if (ai.packageName == ctx.packageName && ai.name != SettingsActivity::class.java.name) ownKey(ai.name) else ai.packageName
                if (byPkg[key]?.leanback == true) continue
                byPkg[key] = AppEntry(
                    key,
                    ri.loadLabel(pm).toString().trim(),
                    ComponentName(ai.packageName, ai.name),
                    leanback,
                )
            }
        }
        add(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0), true)
        add(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0), false)
        // Firmware apps the maker's launcher shows although they declare no launcher category
        // (Xiaomi's 应用中心 by category, 高清播放器 and 米家 by name; from Aerial's device notes).
        add(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_APPSTORE_LAUNCHER), 0), false)
        for (c in UNLISTED_ENTRIES) {
            if (c.packageName in byPkg) continue
            val ai = runCatching { pm.getActivityInfo(c, 0) }.getOrNull() ?: continue
            if (ai.exported && ai.isEnabled && ai.applicationInfo.enabled)
                byPkg[c.packageName] = AppEntry(c.packageName, ai.loadLabel(pm).toString().trim(), c, false)
        }
        // Other launchers only confuse things on a launcher grid; so does the home screen itself
        // (the guard build lists it as an app).
        HIDE_ALWAYS.forEach { byPkg.remove(it) }
        if (byPkg[ctx.packageName]?.component?.className == HomeActivity::class.java.name) byPkg.remove(ctx.packageName)
        for (s in prefs.pinnedInputs) {
            val i = InputTile.decode(s) ?: continue
            byPkg[InputTile.key(i)] = AppEntry(InputTile.key(i), i.name, ComponentName(ctx, HomeActivity::class.java), false, i)
        }
        return byPkg.values.toList()
    }

    /** Visible apps in user order; new apps go to the end. */
    fun orderedVisible(): List<AppEntry> {
        val folders = Folders(ctx).read()
        val grouped = folders.flatMap { it.members }.toSet()
        val apps = allApps().filter { it.pkg !in grouped } + folders.map {
            AppEntry(it.id, it.name, ComponentName(ctx, HomeActivity::class.java), false, folder = it)
        }
        val hidden = prefs.hidden
        var order = prefs.order
        if (order.isEmpty()) order = Prefs.DEFAULT_ORDER
        val rank = order.withIndex().associate { it.value to it.index }
        val collator = Collator.getInstance(Locale.CHINA)
        return apps.filter { it.pkg !in hidden }
            .sortedWith(compareBy<AppEntry> { rank[it.pkg] ?: Int.MAX_VALUE }
                .thenBy { if (isSystem(it.pkg)) 1 else 0 }
                .thenComparator { a, b -> collator.compare(a.label, b.label) })
    }

    /** Grid key of one of Tigris's own screens besides settings. */
    private fun ownKey(cls: String) = ctx.packageName + ":" + cls.substringAfterLast('.')

    /** A screen of Tigris itself or a pinned input: it can be moved and hidden, not deleted. */
    fun isOwn(pkg: String) = pkg == ctx.packageName || pkg.startsWith(ctx.packageName + ":") || InputTile.isInput(pkg)

    fun isSystem(pkg: String): Boolean = if (InputTile.isInput(pkg)) false else try {
        (pm.getApplicationInfo(pkg, 0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (e: PackageManager.NameNotFoundException) { false }

    /**
     * Apps the TV needs to work (settings, the TV player, inputs, the built-in
     * launcher); never offered for deletion. Launchers the user installed can go.
     */
    fun isEssential(pkg: String): Boolean {
        if (pkg == ctx.packageName || pkg in ESSENTIAL) return true
        if (!isSystem(pkg)) return false
        if (listOf(".settings", "tvinput", "launcher", "systemui", "inputmethod", ".ime").any { it in pkg.lowercase() }) return true
        return isLauncher(pkg)
    }

    fun isLauncher(pkg: String): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS).any { it.activityInfo.packageName == pkg }
    }

    fun launchIntent(app: AppEntry): Intent? {
        if (app.component.packageName == ctx.packageName)
            return Intent(Intent.ACTION_MAIN).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val i = if (app.leanback) pm.getLeanbackLaunchIntentForPackage(app.pkg) else null
        return (i ?: pm.getLaunchIntentForPackage(app.pkg) ?: Intent(Intent.ACTION_MAIN).setComponent(app.component))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    fun cachedTile(pkg: String): Bitmap? = tiles.get(tileKey(pkg))

    /** The settings tile is keyed by its custom icon's date too, so a newly uploaded one shows up. */
    private fun tileKey(pkg: String): String =
        if (pkg == ctx.packageName) pkg + "@" + (SettingsActivity.customIcon(ctx)?.lastModified() ?: 0L) else pkg

    /** Builds the tile bitmap; call off the main thread. */
    fun tile(app: AppEntry): Bitmap {
        val key = tileKey(app.pkg)
        tiles.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(TILE_W, TILE_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        app.folder?.let { f ->
            c.drawColor(Color.rgb(56, 62, 82))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val members = allApps().associateBy { it.pkg }
            f.members.filter { it !in prefs.hidden }.take(4).forEachIndexed { index, pkg ->
                members[pkg]?.let { member ->
                    val left = 84f + (index % 2) * 122f
                    val top = 14f + (index / 2) * 76f
                    c.drawBitmap(tile(member), null, RectF(left, top, left + 110f, top + 62f), paint)
                }
            }
            paint.color = Color.WHITE; paint.textSize = 26f; paint.textAlign = Paint.Align.CENTER
            c.drawText(android.text.TextUtils.ellipsize(f.name, android.text.TextPaint(paint), 360f,
                android.text.TextUtils.TruncateAt.END).toString(), 200f, 207f, paint)
            tiles.put(key, bmp); return bmp
        }
        app.input?.let { drawInputTile(c, it); tiles.put(key, bmp); return bmp }
        val own = if (app.component.className == SettingsActivity::class.java.name)
            SettingsActivity.customIcon(ctx)?.let { SettingsActivity.decodeIcon(it, TILE_W) }?.let { BitmapDrawable(ctx.resources, it) }
        else null
        val banner = own ?: try {
            pm.getActivityInfo(app.component, 0).loadBanner(pm)
                ?: pm.getApplicationInfo(app.pkg, 0).loadBanner(pm)
        } catch (e: Exception) { null }
        if (banner != null) {
            drawCenterCrop(c, banner)
        } else {
            val icon = try { pm.getActivityIcon(app.component) } catch (e: Exception) { pm.defaultActivityIcon }
            drawIconTile(c, icon)
        }
        tiles.put(key, bmp)
        return bmp
    }

    /** Drops the cached tile of [pkg] (it was updated or removed), or of every app. */
    fun invalidate(pkg: String? = null) { if (pkg == null) tiles.evictAll() else tiles.remove(tileKey(pkg)) }

    private fun drawCenterCrop(c: Canvas, d: Drawable) {
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: TILE_W
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: TILE_H
        val scale = maxOf(TILE_W.toFloat() / iw, TILE_H.toFloat() / ih)
        val w = (iw * scale).toInt()
        val h = (ih * scale).toInt()
        val l = (TILE_W - w) / 2
        val t = (TILE_H - h) / 2
        c.drawColor(Color.BLACK)
        d.setBounds(l, t, l + w, t + h)
        d.draw(c)
    }

    /**
     * Icon-only apps: the icon blown up and blurred fills the tile, with the
     * crisp icon in the middle — how tvOS 26 frames non-TV apps.
     */
    private fun drawIconTile(c: Canvas, icon: Drawable) {
        val src = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 192, 192)
        icon.draw(Canvas(src))
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val base = Palette.from(src).maximumColorCount(12).generate().let {
            it.lightVibrantSwatch ?: it.vibrantSwatch ?: it.dominantSwatch
        }?.rgb ?: Color.rgb(120, 130, 150)
        c.drawColor(base)
        // Crop the centre of the icon, stretch it over the tile and blur it.
        val crop = Bitmap.createBitmap(src, 24, 40, 144, 112)
        val blurred = Wallpapers.blurred(crop, darken = 0f)
        p.alpha = 235
        c.drawBitmap(blurred, null, RectF(-40f, -40f, TILE_W + 40f, TILE_H + 40f), p)
        p.alpha = 255
        // A light veil like frosted glass.
        c.drawColor(Color.argb(22, 255, 255, 255))
        val s = (TILE_H * 0.58f).toInt()
        val dst = RectF(((TILE_W - s) / 2).toFloat(), ((TILE_H - s) / 2).toFloat(),
            ((TILE_W + s) / 2).toFloat(), ((TILE_H + s) / 2).toFloat())
        c.drawBitmap(src, Rect(0, 0, 192, 192), dst, p)
        crop.recycle(); blurred.recycle(); src.recycle()
    }

    /** An input tile: a dark glass card with the input glyph and its name, like tvOS's own inputs. */
    private fun drawInputTile(c: Canvas, i: Input) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, TILE_W.toFloat(), TILE_H.toFloat(),
            Color.rgb(58, 64, 82), Color.rgb(22, 24, 32), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, TILE_W.toFloat(), TILE_H.toFloat(), p)
        p.shader = null
        val glyph = ctx.getDrawable(R.drawable.ic_input)!!.mutate()
        glyph.setTint(Color.WHITE)
        val g = 64
        glyph.setBounds((TILE_W - g) / 2, 46, (TILE_W + g) / 2, 46 + g)
        glyph.draw(c)
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.typeface = Fonts.inter(ctx, 600)
        p.textSize = 30f
        c.drawText(i.title, TILE_W / 2f, 160f, p)
        if (i.label.isNotEmpty()) {
            p.color = Color.argb(170, 255, 255, 255)
            p.typeface = Fonts.inter(ctx, 500)
            p.textSize = 20f
            c.drawText(i.label, TILE_W / 2f, 192f, p)
        }
    }

    companion object {
        private const val CATEGORY_APPSTORE_LAUNCHER = "android.intent.category.APPSTORE_LAUNCHER"
        private val UNLISTED_ENTRIES = listOf(
            ComponentName("com.xiaomi.mitv.mediaexplorer", "com.xiaomi.mitv.mediaexplorer.NewScraperMainEntryActivity"), // 高清播放器
            ComponentName("com.xiaomi.smarthome.tv", "com.xiaomi.smarthome.tv.ui.MainActivity"), // 米家
        )
        const val TILE_W = 400
        const val TILE_H = 225
        val HIDE_ALWAYS = setOf(
            "com.oversea.aslauncher",
            "com.dangbei.TVHomeLauncher",
            "com.android.tv.settings.system.FallbackHome",
        )
        private val ESSENTIAL = setOf(
            "com.sony.dtv.tvx",                // Sony's TV / HDMI player
            "com.android.tv",                  // Live Channels
            "com.google.android.tv",
            "com.android.vending",
            "com.google.android.gms",
            "com.sony.dtv.systemupdate",
            "com.sony.dtv.update",
        )
    }
}
