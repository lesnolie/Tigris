package com.leslie.tvhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.leslie.tvhome.tv.Input
import java.text.Collator
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The fully rendered horizontal tile of one app. */
data class AppTileArt(val background: Bitmap)

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

/** Enumerates launchable apps and renders one 16:9 banner tile per app. */
class AppRepo(private val ctx: Context) {
    private val pm: PackageManager = ctx.packageManager
    private val prefs = Prefs(ctx)
    private val tiles = object : LruCache<String, AppTileArt>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppTileArt) = value.background.byteCount
    }
    private val heightPx: Int get() = BrandTilePolicy.renderedHeight(TILE_W)

    fun allApps(): List<AppEntry> {
        val byPkg = LinkedHashMap<String, AppEntry>()
        fun add(list: List<ResolveInfo>, leanback: Boolean) {
            for (ri in list) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == ctx.packageName && (!leanback || ai.name == HomeActivity::class.java.name)) continue
                // Tigris lists two screens of its own (settings, 壁纸); each gets a key of its own.
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

    fun cachedTile(pkg: String): Bitmap? = tiles.get(tileKey(pkg))?.background
    fun cachedArtwork(pkg: String): AppTileArt? = tiles.get(tileKey(pkg))

    /** The settings tile is keyed by its custom icon's date too, so a newly uploaded one shows up. */
    private fun tileKey(pkg: String): String = listOf(
        pkg,
        prefs.appTileStyle,
        if (pkg == ctx.packageName) SettingsActivity.customIcon(ctx)?.lastModified() ?: 0L else 0L,
        prefs.tileIconStamp(pkg)
    ).joinToString("@")

    /** Builds once on a worker. Never decode or inspect drawable pixels during focus animation. */
    fun tile(app: AppEntry): Bitmap = artwork(app).background

    fun artwork(app: AppEntry): AppTileArt {
        val key = tileKey(app.pkg)
        tiles.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(TILE_W, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        when {
            app.folder != null -> drawFolderTile(c, app.folder)
            app.input != null -> drawInputTile(c, app.input)
            else -> drawAppTile(c, app)
        }
        val value = AppTileArt(bmp)
        tiles.put(key, value)
        return value
    }

    private fun drawAppTile(c: Canvas, app: AppEntry) {
        val mode = prefs.appTileSource(app.pkg)
        val classic = prefs.appTileStyle == Prefs.TILE_CLASSIC || mode == Prefs.TILE_CLASSIC
        val forceIcon = mode == Prefs.TILE_ICON
        if (app.component.className == WallpaperActivity::class.java.name) {
            drawWallpaperIconTile(c)
            return
        }
        // An icon the user picked (long press → 更换图标) wins over the app's own artwork.
        TileIcons.custom(ctx, app.pkg)?.let(TileIcons::decode)?.let { picked ->
            TileIcons.draw(c, picked, TILE_W, TILE_H)
            picked.recycle()
            return
        }
        val ownIcon = if (app.component.className == SettingsActivity::class.java.name)
            SettingsActivity.customIcon(ctx)?.let { SettingsActivity.decodeIcon(it, TILE_W) }
        else null
        if (ownIcon != null) {
            drawFallbackIcon(c, ownIcon, classic)
            return
        }
        val drawable = if (app.component.className == SettingsActivity::class.java.name) {
            ctx.getDrawable(R.drawable.ic_gear)?.mutate()?.apply { setTint(TileSkin.SETTINGS_GEAR) }
        } else runCatching { pm.getActivityIcon(app.component) }.getOrNull()
        val banner = if (classic || forceIcon) null else runCatching {
            pm.getActivityInfo(app.component, 0).loadBanner(pm)
                ?: pm.getApplicationInfo(app.component.packageName, 0).loadBanner(pm)
        }.getOrNull()
        if (banner != null && BrandTilePolicy.goodBanner(banner.intrinsicWidth, banner.intrinsicHeight)) {
            drawBannerTile(c, banner)
            return
        }
        val icon = raster(drawable ?: pm.defaultActivityIcon, TILE_H)
        drawFallbackIcon(c, icon, classic)
        icon.recycle()
    }

    /** A real TV banner fills the whole card; no white plate is put behind it. */
    private fun drawBannerTile(c: Canvas, banner: Drawable) {
        val width = banner.intrinsicWidth.takeIf { it > 0 } ?: TILE_W
        val height = banner.intrinsicHeight.takeIf { it > 0 } ?: TILE_H
        val placement = AppTileArtwork.banner(width, height, TILE_W, TILE_H)
        if (placement.mode == AppTileArtwork.Mode.CONTAIN) c.drawColor(Color.rgb(16, 19, 25))
        banner.setBounds(placement.left, placement.top, placement.right, placement.bottom)
        banner.draw(c)
    }

    /**
     * No usable banner: one uniform white card with the app's own launcher icon, centred at a
     * fixed size. Classic mode keeps the icon transparent and larger instead.
     */
    private fun drawFallbackIcon(c: Canvas, icon: Bitmap, classic: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (classic) {
            drawContained(c, icon, 0.82f, paint)
            return
        }
        c.drawColor(TileSkin.WHITE)
        val b = glyphBounds(icon) ?: run { drawPlaceholder(c, ""); return }
        val gw = (b[2] - b[0]).coerceAtLeast(1)
        val gh = (b[3] - b[1]).coerceAtLeast(1)
        val maxSide = TILE_H * 0.64f
        val scale = min(maxSide / gw, maxSide / gh)
        val dispW = gw * scale
        val dispH = gh * scale
        val cx = TILE_W / 2f
        val cy = TILE_H / 2f
        c.drawBitmap(icon, Rect(b[0], b[1], b[2], b[3]),
            RectF(cx - dispW / 2f, cy - dispH / 2f, cx + dispW / 2f, cy + dispH / 2f), paint)
    }

    private fun drawContained(c: Canvas, icon: Bitmap, fraction: Float, paint: Paint) {
        val box = TILE_H * fraction
        val fit = min(box / icon.width, box / icon.height)
        val w = icon.width * fit
        val h = icon.height * fit
        c.drawBitmap(icon, null, RectF((TILE_W - w) / 2f, (TILE_H - h) / 2f,
            (TILE_W + w) / 2f, (TILE_H + h) / 2f), paint)
    }

    /** Render native Drawable proportions intact into a square safe area, never stretching. */
    private fun raster(d: Drawable, size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: size
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: size
        val scale = min(size.toFloat() / iw, size.toFloat() / ih)
        val w = max(1, (iw * scale).roundToInt())
        val h = max(1, (ih * scale).roundToInt())
        d.setBounds((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2)
        d.draw(Canvas(output))
        return output
    }

    /** Bounding box of opaque pixels [l,t,r,b], or null when fully transparent. */
    private fun glyphBounds(bmp: Bitmap): IntArray? {
        var minX = bmp.width; var minY = bmp.height; var maxX = -1; var maxY = -1
        for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width step 2) {
            if (Color.alpha(bmp.getPixel(x, y)) >= 180) {
                if (x < minX) minX = x; if (y < minY) minY = y
                if (x > maxX) maxX = x; if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return null
        return intArrayOf(minX, minY, maxX + 1, maxY + 1)
    }

    private fun drawFolderTile(c: Canvas, folder: AppFolder) {
        c.drawColor(Color.rgb(56, 62, 82))
        val members = allApps().associateBy { it.pkg }
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        folder.members.filter { it !in prefs.hidden }.take(4).forEachIndexed { index, pkg ->
            members[pkg]?.let { member ->
                val pic = artwork(member)
                val left = 84f + (index % 2) * 122f
                val top = 14f + (index / 2) * 76f
                c.drawBitmap(pic.background, null, RectF(left, top, left + 110f, top + 62f), p)
            }
        }
        p.color = Color.WHITE
        p.textSize = 26f
        p.textAlign = Paint.Align.CENTER
        c.drawText(android.text.TextUtils.ellipsize(folder.name, android.text.TextPaint(p), 360f,
            android.text.TextUtils.TruncateAt.END).toString(), TILE_W / 2f, TILE_H - 18f, p)
    }

    /** An input tile: a dark glass card with the input glyph and its name, like tvOS's own inputs. */
    private fun drawInputTile(c: Canvas, i: Input) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, TILE_W.toFloat(), TILE_H.toFloat(),
            Color.rgb(58, 64, 82), Color.rgb(22, 24, 32), Shader.TileMode.CLAMP)
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

    /** Drops the cached tile of [pkg] (it was updated or removed), or of every app. */
    fun invalidate(pkg: String? = null) {
        if (pkg == null) tiles.evictAll()
        else {
            // Package changes invalidate all artwork variants, including manual overrides.
            tiles.snapshot().keys.filter { it.startsWith(pkg + "@") }.forEach { tiles.remove(it) }
        }
    }

    /** The wallpaper screen has no square launcher icon; draw the same blue-violet card it uses. */
    private fun drawWallpaperIconTile(c: Canvas) {
        val radius = TILE_H * 0.225f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, TILE_W.toFloat(), TILE_H.toFloat(),
                0xFF2F6FE0.toInt(), 0xFF7A3FD0.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(0f, 0f, TILE_W.toFloat(), TILE_H.toFloat(), radius, radius, p)
        val glyph = ctx.getDrawable(R.drawable.ic_photo)!!.mutate()
        glyph.setTint(Color.WHITE)
        val g = (TILE_H * 0.58f).roundToInt()
        glyph.setBounds((TILE_W - g) / 2, (TILE_H - g) / 2, (TILE_W + g) / 2, (TILE_H + g) / 2)
        glyph.draw(c)
    }

    private fun drawPlaceholder(c: Canvas, label: String) {
        c.drawColor(TileSkin.WHITE)
        val letter = label.trim().take(1).uppercase(Locale.getDefault()).ifEmpty { "?" }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x33, 0x33, 0x33)
            textAlign = Paint.Align.CENTER
            textSize = TILE_H * 0.42f
            isFakeBoldText = true
        }
        c.drawText(letter, TILE_W / 2f, TILE_H / 2f - (p.ascent() + p.descent()) / 2f, p)
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
