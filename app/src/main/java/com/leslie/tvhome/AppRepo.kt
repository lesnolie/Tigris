package com.leslie.tvhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.AdaptiveIconDrawable
import android.util.LruCache
import com.leslie.tvhome.tv.Input
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.min
import kotlin.math.max

/** Immutable decoded artwork. Background stays cached and optional adaptive foreground floats independently. */
data class AppTileArt(val background: Bitmap, val foreground: Bitmap? = null)

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

/** Enumerates launchable apps and renders their 16:9 tvOS-style tiles. */
class AppRepo(private val ctx: Context) {
    private val pm: PackageManager = ctx.packageManager
    private val prefs = Prefs(ctx)
    private val tiles = object : LruCache<String, AppTileArt>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppTileArt) =
            value.background.byteCount + (value.foreground?.byteCount ?: 0)
    }
    private val heightPx: Int get() = BrandTilePolicy.renderedHeight(TILE_W, prefs.appTileAspect)

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
        prefs.appTileAspect,
        prefs.appTileSource(pkg),
        if (pkg == ctx.packageName) SettingsActivity.customIcon(ctx)?.lastModified() ?: 0L else 0L
    ).joinToString("@")

    /** Builds once on a worker. Never decode or inspect drawable pixels during focus animation. */
    fun tile(app: AppEntry): Bitmap = artwork(app).background

    fun artwork(app: AppEntry): AppTileArt {
        val key = tileKey(app.pkg)
        tiles.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(TILE_W, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        var layer: Bitmap? = null
        if (app.folder != null) {
            val members = allApps().associateBy { it.pkg }
            c.drawColor(Color.rgb(56, 62, 82))
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            app.folder.members.filter { it !in prefs.hidden }.take(4).forEachIndexed { index, pkg ->
                members[pkg]?.let { member ->
                    val pic = artwork(member)
                    val left = 84f + (index % 2) * 122f
                    val top = 14f + (index / 2) * 76f
                    val dst = RectF(left, top, left + 110f, top + 62f)
                    c.drawBitmap(pic.background, null, dst, p)
                    pic.foreground?.let { c.drawBitmap(it, null, dst, p) }
                }
            }
            p.color = Color.WHITE
            p.textSize = 26f
            p.textAlign = Paint.Align.CENTER
            c.drawText(android.text.TextUtils.ellipsize(app.folder.name, android.text.TextPaint(p), 360f,
                android.text.TextUtils.TruncateAt.END).toString(), 200f, heightPx - 18f, p)
        } else if (app.input != null) {
            drawInputTile(c, app.input)
        } else {
            val ownIcon = if (app.component.className == SettingsActivity::class.java.name)
                SettingsActivity.customIcon(ctx)?.let { SettingsActivity.decodeIcon(it, TILE_W) }
            else null
            if (app.component.className == WallpaperActivity::class.java.name && ownIcon == null) {
                drawWallpaperIconTile(c)
            } else if (ownIcon != null) {
                if (prefs.appTileStyle == Prefs.TILE_CLASSIC) drawIconBitmapTile(c, ownIcon)
                else drawBrandIconBitmapTile(c, ownIcon)
            } else {
                val drawable = if (app.component.className == SettingsActivity::class.java.name) {
                    ctx.getDrawable(R.drawable.ic_gear)?.mutate()?.apply { setTint(TileSkin.SETTINGS_GEAR) }
                } else runCatching { pm.getActivityIcon(app.component) }.getOrNull()
                val mode = prefs.appTileSource(app.pkg)
                val classic = prefs.appTileStyle == Prefs.TILE_CLASSIC || mode == Prefs.TILE_CLASSIC
                val banner = if (classic || mode == Prefs.TILE_ICON) null else runCatching {
                    pm.getActivityInfo(app.component, 0).loadBanner(pm)
                        ?: pm.getApplicationInfo(app.component.packageName, 0).loadBanner(pm)
                }.getOrNull()
                val goodBanner = banner != null &&
                    BrandTilePolicy.goodBanner(banner.intrinsicWidth, banner.intrinsicHeight)
                val adaptive = drawable as? AdaptiveIconDrawable
                val candidate = if (classic || goodBanner) null else adaptive?.let { validatedForeground(it.foreground) }
                val choice = BrandTilePolicy.select(classic, mode, goodBanner, candidate != null, drawable != null)
                when (choice) {
                    BrandTilePolicy.Source.CLASSIC -> drawIconTile(c, drawable ?: pm.defaultActivityIcon)
                    BrandTilePolicy.Source.BANNER -> drawBrandBanner(c, banner!!)
                    BrandTilePolicy.Source.ADAPTIVE -> {
                        val bg = adaptive?.background?.let { raster(it, 192) }
                        drawBrandSurface(c, brandColor(bg ?: candidate!!))
                        bg?.recycle()
                        layer = Bitmap.createBitmap(TILE_W, heightPx, Bitmap.Config.ARGB_8888)
                        drawCenteredIcon(Canvas(layer!!), candidate!!, 0.90f)
                        candidate?.recycle()
                    }
                    BrandTilePolicy.Source.ICON -> {
                        val icon = drawable?.let { raster(it, 256) }
                        if (icon != null) {
                            drawBrandIconBitmapTile(c, icon)
                            icon.recycle()
                        } else drawPlaceholder(c, app.label)
                        candidate?.recycle()
                    }
                    BrandTilePolicy.Source.PLACEHOLDER -> {
                        drawPlaceholder(c, app.label)
                        candidate?.recycle()
                    }
                }
            }
        }
        val value = AppTileArt(bmp, layer)
        tiles.put(key, value)
        return value
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

    /** Reject an adaptive drawable which fills its entire nominal layer with opaque pixels. */
    private fun validatedForeground(d: Drawable): Bitmap? {
        val bitmap = raster(d, 192)
        val stride = 4
        var visible = 0
        var total = 0
        var top = false
        var bottom = false
        var left = false
        var right = false
        for (y in 0 until bitmap.height step stride) {
            for (x in 0 until bitmap.width step stride) {
                total++
                if (Color.alpha(bitmap.getPixel(x, y)) < 56) continue
                visible++
                if (y < 10) top = true
                if (y > bitmap.height - 10) bottom = true
                if (x < 10) left = true
                if (x > bitmap.width - 10) right = true
            }
        }
        if (!BrandTilePolicy.validForeground(visible, total, top && bottom && left && right)) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    /** Saturated regions beat near-white padding when recovering a brand identity. */
    private fun brandColor(bitmap: Bitmap): Int {
        var best = -1f
        var picked = 0xFF627C9C.toInt()
        for (y in 4 until bitmap.height step 9) for (x in 4 until bitmap.width step 9) {
            val color = bitmap.getPixel(x, y)
            if (Color.alpha(color) < 48) continue
            val red = Color.red(color); val green = Color.green(color); val blue = Color.blue(color)
            val mx = max(red, max(green, blue)); val mn = min(red, min(green, blue))
            val range = mx - mn
            // Reject almost-white/black pixels and prefer saturated colors.
            val score = range.toFloat() - if (mx > 239 || mx < 40) 70f else 0f
            if (score > best) { best = score; picked = color }
        }
        return BrandTilePolicy.brandColor(picked)
    }

    private fun drawBrandSurface(c: Canvas, color: Int) {
        val light = blend(color, Color.WHITE, 0.12f)
        val dark = blend(color, Color.BLACK, 0.40f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, TILE_W.toFloat(), heightPx.toFloat(),
                light, dark, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, TILE_W.toFloat(), heightPx.toFloat(), paint)
    }

    private fun blend(a: Int, b: Int, factor: Float) = Color.rgb(
        (Color.red(a) * (1f-factor) + Color.red(b)*factor).roundToInt(),
        (Color.green(a) * (1f-factor) + Color.green(b)*factor).roundToInt(),
        (Color.blue(a) * (1f-factor) + Color.blue(b)*factor).roundToInt())

    private fun drawCenteredIcon(c: Canvas, icon: Bitmap, fraction: Float = 0.80f) {
        val size = heightPx * fraction
        val fit = min(size / icon.width, size / icon.height)
        val w = icon.width * fit
        val h = icon.height * fit
        c.drawBitmap(icon, null, RectF((TILE_W-w)/2f, (heightPx-h)/2f,
            (TILE_W+w)/2f, (heightPx+h)/2f),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawBrandIconBitmapTile(c: Canvas, icon: Bitmap) {
        drawBrandSurface(c, brandColor(icon))
        drawCenteredIcon(c, icon)
    }

    /** Do not crop any important text or stretch a TV Banner; contain odd aspect ratios. */
    private fun drawBrandBanner(c: Canvas, banner: Drawable) {
        drawBrandSurface(c, BrandTilePolicy.brandColor(0xFF69778A.toInt()))
        val pos = AppTileArtwork.banner(banner.intrinsicWidth, banner.intrinsicHeight, TILE_W, heightPx)
        banner.setBounds(pos.left, pos.top, pos.right, pos.bottom)
        banner.draw(c)
    }

    private fun drawPlaceholder(c: Canvas, label: String) {
        drawBrandSurface(c, BrandTilePolicy.brandColor(0xFF546E88.toInt()))
        val letter = label.trim().take(1).uppercase(Locale.getDefault()).ifEmpty { "?" }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = heightPx * 0.42f
            isFakeBoldText = true
        }
        c.drawText(letter, TILE_W / 2f, (heightPx - (p.ascent() + p.descent())) / 2f, p)
    }

    /** Drops the cached tile of [pkg] (it was updated or removed), or of every app. */
    fun invalidate(pkg: String? = null) {
        if (pkg == null) tiles.evictAll()
        else {
            // Package changes invalidate all artwork variants, including manual overrides.
            tiles.snapshot().keys.filter { it.startsWith(pkg + "@") }.forEach { tiles.remove(it) }
        }
    }

    /** The wallpaper screen has no square launcher icon; draw the same blue-violet tile it uses. */
    private fun drawWallpaperIconTile(c: Canvas) {
        val size = 256
        val icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val ic = Canvas(icon)
        val radius = size * 0.225f
        ic.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
                    0xFF2F6FE0.toInt(), 0xFF7A3FD0.toInt(), Shader.TileMode.CLAMP)
            })
        val glyph = ctx.getDrawable(R.drawable.ic_photo)!!.mutate()
        glyph.setTint(Color.WHITE)
        val g = (size * 0.58f).roundToInt()
        glyph.setBounds((size - g) / 2, (size - g) / 2, (size + g) / 2, (size + g) / 2)
        glyph.draw(ic)
        drawIconBitmapTile(c, icon)
        icon.recycle()
    }

    /**
     * Every app tile shares one frost-grey surface; the icon itself is untouched. Rounded corners
     * and the hairline border live in TileCell, over this bitmap, so the edge stays crisp at any
     * resolution and against any wallpaper.
     */
    private fun drawTileSurface(c: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, heightPx.toFloat(),
                TileSkin.TOP, TileSkin.BOTTOM, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, TILE_W.toFloat(), heightPx.toFloat(), paint)
    }

    /** Draw the icon at its original colours and proportions, centred with a calm margin. */
    private fun drawIconTile(c: Canvas, icon: Drawable) {
        val sourceSize = 256
        val iw = icon.intrinsicWidth.takeIf { it > 0 } ?: sourceSize
        val ih = icon.intrinsicHeight.takeIf { it > 0 } ?: sourceSize
        val fit = minOf(sourceSize.toFloat() / iw, sourceSize.toFloat() / ih)
        val w = (iw * fit).roundToInt().coerceAtLeast(1)
        val h = (ih * fit).roundToInt().coerceAtLeast(1)
        val source = Bitmap.createBitmap(sourceSize, sourceSize, Bitmap.Config.ARGB_8888)
        val left = (sourceSize - w) / 2
        val top = (sourceSize - h) / 2
        icon.setBounds(left, top, left + w, top + h)
        icon.draw(Canvas(source))
        drawIconBitmapTile(c, source)
        source.recycle()
    }

    private fun drawIconBitmapTile(c: Canvas, source: Bitmap) {
        drawTileSurface(c)
        val side = AppTileArtwork.iconSize(heightPx)
        val fit = minOf(side.toFloat() / source.width, side.toFloat() / source.height)
        val w = (source.width * fit).coerceAtLeast(1f)
        val h = (source.height * fit).coerceAtLeast(1f)
        c.drawBitmap(source, Rect(0, 0, source.width, source.height),
            RectF((TILE_W - w) / 2f, (heightPx - h) / 2f, (TILE_W + w) / 2f, (heightPx + h) / 2f),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    /** An input tile: a dark glass card with the input glyph and its name, like tvOS's own inputs. */
    private fun drawInputTile(c: Canvas, i: Input) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, TILE_W.toFloat(), heightPx.toFloat(),
            Color.rgb(58, 64, 82), Color.rgb(22, 24, 32), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, TILE_W.toFloat(), heightPx.toFloat(), p)
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
