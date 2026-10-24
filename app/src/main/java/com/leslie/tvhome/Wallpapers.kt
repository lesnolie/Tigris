package com.leslie.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Background pictures, downloaded when they are about to be shown and dropped once old. A refresh
 * (about daily) only reads a source's list of pictures, a few kB of JSON, into a queue; one picture
 * is downloaded each time the home screen or the photo frame moves on ([advance], [next]). The
 * day's dozen used up, they go round the ones already here. Pictures older than [KEEP_DAYS] days
 * and any past [MAX_FILES] are deleted, keeping the newest [MIN_FILES]. Each source has its folder:
 * - [BING]: Bing's daily photos (cn.bing.com works without a VPN), the last ~15 days listed.
 * - [ANIME]: the month's most popular anime wallpapers on Wallhaven, SFW only; 360's and alcy's
 *   anime when Wallhaven can't be reached.
 * - [SCENERY], [FRESH], [ART]: picture feeds (360's wallpaper categories, Wallhaven, the
 *   Cleveland Museum's open collection), read like custom links, from a page that changes daily.
 * - [MINE]: photos sent from a phone (PhoneServer); never deleted.
 * - [CUSTOM]: links the user added ([Prefs.customWallUrls]). A link may be a
 *   picture, an API that answers with a different picture each time, or a
 *   page / JSON that lists picture addresses.
 */
class Wallpapers(private val ctx: Context, private val source: String = BING) {
    private val dir = File(ctx.filesDir, if (source == BING) "wall" else "wall-$source").apply { mkdirs() }
    private val metaFile = File(dir, "meta.json")
    /** {made, total, taken, items: [{url, caption, name?, id?, page?}], ids, looks}: the day's list. */
    private val queueFile = File(dir, "queue.json")
    private val showFile = File(dir, "showing")
    private val slotFile = File(dir, "slot")
    private val lock get() = LOCKS.getOrPut(source) { Any() }

    /** Cached files, newest first (Bing files are named by date; others by download time). */
    fun files(): List<File> {
        val all = dir.listFiles { f -> f.name.endsWith(".jpg") } ?: return emptyList()
        return if (source == BING) all.sortedByDescending { it.name } else all.sortedByDescending { it.lastModified() }
    }

    /** The home screen's picture: the one [advance] last moved to, or the newest. */
    fun current(): File? {
        val all = files()
        val name = runCatching { showFile.readText() }.getOrNull()
        return all.firstOrNull { it.name == name } ?: all.firstOrNull()
    }

    /** Blocking. Moves the home screen on once every [minutes] (or now, with [force]); true if its picture changed. */
    fun advance(minutes: Int, force: Boolean = false): Boolean {
        val slot = System.currentTimeMillis() / (minutes.coerceAtLeast(1) * 60_000L)
        if (!force && runCatching { slotFile.readText().toLong() }.getOrNull() == slot) return false
        val before = current()
        val f = next(before) ?: return false
        slotFile.writeText(slot.toString())
        showFile.writeText(f.name)
        return f != before
    }

    /** Blocking. The picture to show after [after]: a new one while the day's list lasts, else the next one here. */
    fun next(after: File?): File? {
        download()?.let { return it }
        val all = files()
        if (all.isEmpty()) return null
        return all[(all.indexOf(after) + 1) % all.size]
    }

    /** "Title — place (© photographer)" for the photo frame caption. */
    fun caption(f: File): String = try {
        JSONObject(metaFile.readText()).optString(f.name)
    } catch (e: Exception) { "" }

    /**
     * Blocking; reads the source's list again if it is older than ~a day, and downloads one picture
     * if there is none yet, so a newly picked source shows something straight away.
     */
    fun refresh(force: Boolean = false) {
        if (source == MINE) return
        // One at a time per source: the home screen, the frame and the phone page may all ask.
        synchronized(lock) {
            val q = readQueue()
            if (force || q == null || System.currentTimeMillis() - q.optLong("made") > 20 * 3_600_000L) {
                runCatching { queueFile.writeText(list().toString()) }.onFailure { Log.w("Wallpapers", "list failed", it) }
            }
        }
        if (files().isEmpty()) download()
    }

    private fun readQueue(): JSONObject? = runCatching { JSONObject(queueFile.readText()) }.getOrNull()

    private fun list(): JSONObject {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        val items = JSONArray()
        when (source) {
            BING -> listBing(items)
            ANIME -> if (runCatching { listAnime(items, day) }.onFailure { Log.w("Wallpapers", "wallhaven", it) }.isFailure ||
                items.length() == 0) listLinks(feeds(ANIME_BACKUP, day), items)
            CUSTOM -> listLinks(Prefs(ctx).customWallUrls.lines(), items)
            else -> listLinks(feeds(source, day), items)
        }
        return JSONObject().put("made", System.currentTimeMillis()).put("taken", 0).put("items", items)
            .put("total", if (source == CUSTOM) Int.MAX_VALUE else PER_DAY)
    }

    private fun item(url: String, caption: String) = JSONObject().put("url", url).put("caption", caption)

    private fun listBing(items: JSONArray) {
        val uhd = MediaQuality.uhdWallpapers(ctx)
        for (idx in listOf(0, 7)) {
            val json = get("https://cn.bing.com/HPImageArchive.aspx?format=js&idx=$idx&n=8&mkt=zh-CN").decodeToString()
            val imgs = JSONObject(json).getJSONArray("images")
            for (i in 0 until imgs.length()) {
                val o = imgs.getJSONObject(i)
                val title = o.optString("title")
                val copy = o.optString("copyright")
                val base = "https://cn.bing.com" + o.getString("urlbase")
                val caption = if (title.isNotEmpty() && !copy.startsWith(title)) "$title — $copy" else copy
                val standard = base + "_1920x1080.jpg"
                items.put(item(if (uhd) base + "_UHD.jpg" else standard, caption)
                    .apply { if (uhd) put("fallback", standard) }
                    .put("name", o.getString("startdate") + (if (uhd) "-uhd.jpg" else ".jpg")))
            }
        }
    }

    private fun listAnime(items: JSONArray, day: Int) {
        // A different page of the month's ranking each day keeps the set changing.
        val list = JSONObject(get("https://wallhaven.cc/api/v1/search?categories=010&purity=100&sorting=toplist" +
            "&topRange=1M&atleast=1920x1080&ratios=16x9&page=${1 + day % 5}").decodeToString()).getJSONArray("data")
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            // Some originals are 10000 px wide; not worth the download or the memory.
            if (o.optLong("file_size") > 8_000_000) continue
            items.put(item(o.getString("path"), "Wallhaven · wallhaven.cc/w/" + o.getString("id")).put("name", "wh-" + o.getString("id") + ".jpg"))
        }
    }

    /**
     * A link that answers with a picture is listed [PER_LINK] times (random-picture APIs give a new
     * one each time). Anything else is searched for picture addresses, JSON included. Feeds list each
     * picture at several sizes (Wallhaven's thumbnails, 360's seven resolutions); those share an id,
     * and small ones and near-copies of one already taken are skipped when downloading ([take]).
     */
    private fun listLinks(lines: List<String>, items: JSONArray) {
        val links = lines.map { it.trim() }.filter { it.startsWith("http://") || it.startsWith("https://") }
        for (link in links) {
            val host = runCatching { URL(link).host }.getOrDefault(link)
            val caption = CAPTIONS.entries.firstOrNull { host.endsWith(it.key) }?.value ?: host
            val first = runCatching { fetch(link) }.getOrNull() ?: continue
            if (first.second?.startsWith("image/") == true || looksLikeImage(first.first)) {
                repeat(PER_LINK) { items.put(item(link, caption)) }
            } else {
                val text = first.first.decodeToString().replace("\\/", "/")
                IMAGE_URL.findAll(text).map { it.value }.distinct().take(PER_DAY * 4).forEach { u ->
                    items.put(item(u, caption).put("page", true).apply { pictureId(u)?.let { put("id", it) } })
                }
            }
        }
    }

    /** Blocking. Downloads the next picture of the day's list; null once it is used up or none works. */
    private fun download(): File? = synchronized(lock) {
        val q = readQueue() ?: return null
        val items = q.optJSONArray("items") ?: return null
        val meta = runCatching { JSONObject(metaFile.readText()) }.getOrElse { JSONObject() }
        var got: File? = null
        var tries = 0
        while (got == null && items.length() > 0 && q.optInt("taken") < q.optInt("total") && tries++ < 16) {
            val o = items.remove(0) as JSONObject
            got = runCatching { take(o, q, meta) }.onFailure { Log.w("Wallpapers", "download failed", it) }.getOrNull()
        }
        if (got != null) {
            q.put("taken", q.optInt("taken") + 1)
            prune()
            val keep = files().map { it.name }.toSet()
            meta.keys().asSequence().toList().forEach { if (it !in keep) meta.remove(it) }
            metaFile.writeText(meta.toString())
        }
        queueFile.writeText(q.toString())
        got
    }

    private fun take(o: JSONObject, q: JSONObject, meta: JSONObject): File? {
        val ids = q.optJSONArray("ids") ?: JSONArray().also { q.put("ids", it) }
        val id = o.optString("id").ifEmpty { null }
        if (id != null && (0 until ids.length()).any { ids.getString(it) == id }) return null
        val given = o.optString("name").ifEmpty { null }
        if (given != null && File(dir, given).exists()) return null
        // Bing does not provide UHD for every date. A missing UHD falls back to the existing
        // 1080p file; no stale failed download is cached.
        val bytes = runCatching { fetch(o.getString("url")).first }.getOrElse { error ->
            val fallback = o.optString("fallback")
            if (fallback.isEmpty()) throw error
            fetch(fallback).first
        }
        if (o.optBoolean("page")) {
            val look = look(bytes, minWidth = 1280) ?: return null
            val looks = q.optJSONArray("looks") ?: JSONArray().also { q.put("looks", it) }
            if ((0 until looks.length()).any { java.lang.Long.bitCount(looks.getLong(it) xor look) <= 6 }) return null
            looks.put(look)
        }
        id?.let { ids.put(it) }
        val name = given ?: ("u-" + hash(bytes) + ".jpg")
        if (File(dir, name).exists() || !write(bytes, name)) return null
        meta.put(name, o.optString("caption"))
        return File(dir, name)
    }

    /** Old pictures go: past [KEEP_DAYS] days (keeping the newest [MIN_FILES]) or past [MAX_FILES]; never the one on screen. */
    private fun prune() {
        val showing = runCatching { showFile.readText() }.getOrNull()
        val old = System.currentTimeMillis() - KEEP_DAYS * 86_400_000L
        files().forEachIndexed { i, f ->
            if (f.name != showing && (i >= MAX_FILES || (i >= MIN_FILES && f.lastModified() < old))) f.delete()
        }
    }

    /** A photo sent from a phone, for [MINE]. Returns its file name, or null if it isn't a picture. */
    fun add(bytes: ByteArray): String? {
        val name = "my-" + hash(bytes) + ".jpg"
        return if (File(dir, name).exists() || write(bytes, name)) name else null
    }

    private fun write(bytes: ByteArray, name: String): Boolean {
        val bmp = fitScreen(bytes) ?: return false
        val tmp = File(dir, "$name.part")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return tmp.renameTo(File(dir, name))
    }

    fun delete(name: String) {
        if (name.contains('/') || !name.endsWith(".jpg")) return
        File(dir, name).delete()
    }

    private fun hash(b: ByteArray) =
        java.security.MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }.take(16)

    private fun looksLikeImage(b: ByteArray) = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        .also { BitmapFactory.decodeByteArray(b, 0, b.size, it) }.outWidth > 0

    /** GET with the content type, following redirects across http and https (HttpURLConnection won't). */
    private fun fetch(url: String): Pair<ByteArray, String?> {
        var u = url
        repeat(5) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 15000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) Tigris")
            val code = c.responseCode
            if (code in 300..399) {
                u = URL(URL(u), c.getHeaderField("Location") ?: error("redirect without Location")).toString()
                c.disconnect()
                return@repeat
            }
            if (code !in 200..299) error("HTTP $code")
            if (c.contentLengthLong > MAX_DOWNLOAD) error("too large")
            val bytes = c.inputStream.use { it.readBytes() }
            return bytes to c.contentType
        }
        error("too many redirects")
    }

    /**
     * The id in a picture's file name ("wallhaven-yqkl97.jpg" and "yqkl97.jpg" are both "yqkl97"), or
     * null for a name that is no id, such as IIIF's "default.jpg", which every picture there shares.
     */
    private fun pictureId(url: String): String? {
        // Cleveland's are "1916.1044_web" and "1916.1044_print".
        val stem = url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').substringAfterLast('-').substringBefore('_')
        return stem.takeIf { it.length >= 6 && it.any(Char::isDigit) }
    }

    /**
     * A 64-bit fingerprint of a picture (each cell of an 8x8 grey thumbnail brighter or darker than
     * average), the same for one picture at any size; null if it is no picture, narrower than [minWidth]
     * or upright (a portrait cropped to the screen loses most of it).
     */
    private fun look(bytes: ByteArray, minWidth: Int): Long? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth < minWidth || bounds.outWidth * 5 < bounds.outHeight * 6) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 64) sample *= 2
        val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        // Of the middle 16:9, the part that is kept: feeds also offer the picture cropped to 16:10 or 4:3.
        val w = minOf(b.width, b.height * 16 / 9)
        val h = minOf(b.height, b.width * 9 / 16)
        val t = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        Canvas(t).drawBitmap(b, Rect((b.width - w) / 2, (b.height - h) / 2, (b.width + w) / 2, (b.height + h) / 2),
            Rect(0, 0, 8, 8), Paint(Paint.FILTER_BITMAP_FLAG))
        val grey = IntArray(64) { i -> t.getPixel(i % 8, i / 8).let { Color.red(it) * 3 + Color.green(it) * 6 + Color.blue(it) } }
        t.recycle()
        b.recycle()
        val avg = grey.average()
        var bits = 0L
        for (i in 0 until 64) if (grey[i] > avg) bits = bits or (1L shl i)
        return bits
    }

    /** Do not silently reduce an actual UHD photo to 1080p. Avoid upscaling smaller sources. */
    private fun fitScreen(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val hasUhdSource = bounds.outWidth >= 3840 && bounds.outHeight >= 2160
        val prefer4k = hasUhdSource && MediaQuality.uhdWallpapers(ctx)
        // A 4K RGB_565 bitmap takes ~16 MiB instead of ~32 MiB in ARGB_8888.
        if (prefer4k) runCatching { decodeToSize(bytes, 3840, 2160) }
            .onFailure { Log.w("Wallpapers", "UHD decode failed; using 1080p", it) }
            .getOrNull()?.let { return it }
        return runCatching { decodeToSize(bytes, 1920, 1080) }
            .onFailure { Log.w("Wallpapers", "image decode failed", it) }.getOrNull()
    }

    private fun decodeToSize(bytes: ByteArray, targetW: Int, targetH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) sample *= 2
        val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }) ?: return null
        if (b.width == targetW && b.height == targetH) return b
        val cropW = minOf(b.width, b.height * 16 / 9)
        val cropH = minOf(b.height, b.width * 9 / 16)
        val outW = minOf(targetW, cropW)
        val outH = minOf(targetH, cropH)
        if (outW <= 0 || outH <= 0) { b.recycle(); return null }
        val src = Rect((b.width - cropW) / 2, (b.height - cropH) / 2,
            (b.width + cropW) / 2, (b.height + cropH) / 2)
        return try {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.RGB_565).also {
                Canvas(it).drawBitmap(b, src, Rect(0, 0, outW, outH), Paint(Paint.FILTER_BITMAP_FLAG))
            }
        } finally { b.recycle() }
    }

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) Tigris")
        return c.inputStream.use { it.readBytes() }
    }

    fun decode(f: File, full: Boolean = false): Bitmap? = try {
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
            inPreferredConfig = if (full) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            inMutable = true
        })
    } catch (e: OutOfMemoryError) { null }

    /** A photo dressed for the home screen: darker at the bottom and top-left so tiles and clock stay legible. */
    fun forHome(f: File): Bitmap? = decode(f)?.also { b ->
        val c = Canvas(b)
        val w = b.width.toFloat()
        val h = b.height.toFloat()
        val p = Paint()
        p.shader = LinearGradient(0f, h * 0.35f, 0f, h, Color.TRANSPARENT, Color.argb(215, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = android.graphics.RadialGradient(w * 0.12f, h * 0.38f, w * 0.45f,
            Color.argb(90, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
    }

    companion object {
        const val MAX_FILES = 16
        private const val MIN_FILES = 4
        private const val KEEP_DAYS = 3
        /** New pictures a day from a source with a list; random-picture links give [PER_LINK] each. */
        private const val PER_DAY = 12
        const val BING = "daily"
        const val ANIME = "anime"
        const val MINE = "mine"
        const val CUSTOM = "custom"
        const val SCENERY = "scenery"
        const val FRESH = "fresh"
        const val ART = "art"
        private const val ANIME_BACKUP = "anime-backup"
        val SOURCES = listOf(BING, SCENERY, FRESH, ANIME, ART, MINE, CUSTOM)

        /** Hosts to the captions the photo frame shows under their pictures. */
        private val CAPTIONS = mapOf("360.cn" to "360 壁纸", "wallhaven.cc" to "Wallhaven", "alcy.cc" to "栗次元",
            "clevelandart.org" to "克利夫兰艺术博物馆 · 公有领域", "picsum.photos" to "Lorem Picsum · Unsplash")

        /**
         * The links of a feed source, with the page for [day] (so a new set each day), in a
         * daily-rotating order; the day's dozen come from the first that works, the rest are backups.
         */
        private fun feeds(source: String, day: Int): List<String> {
            fun q360(cid: Int) = "http://wallpaper.apc.360.cn/index.php?c=WallPaper&a=getAppsByCategory&cid=$cid" +
                "&start=${day % 20 * 12}&count=12"
            val links = when (source) {
                SCENERY -> listOf(q360(9), q360(36)).rotate(day) + "https://picsum.photos/1920/1080"
                FRESH -> listOf(q360(15), q360(14)).rotate(day)
                ART -> listOf("https://wallhaven.cc/api/v1/search?q=painting&categories=100&purity=100&atleast=1920x1080" +
                    "&ratios=16x9&sorting=toplist&page=${1 + day % 5}",
                    "https://openaccess-api.clevelandart.org/api/artworks/?q=landscape&type=Painting&cc0=1&has_image=1" +
                        "&limit=30&skip=${day % 10 * 30}").rotate(day)
                ANIME_BACKUP -> listOf(q360(26), "https://t.alcy.cc/pc").rotate(day)
                else -> emptyList()
            }
            return links
        }

        private fun List<String>.rotate(day: Int) = if (isEmpty()) this else drop(day % size) + take(day % size)

        /**
         * Keeps one picture (the gallery's preview) of each downloaded source that is not [keep], so
         * trying sources out doesn't pile up files. Photos and custom links are left alone.
         */
        fun trimExcept(ctx: Context, keep: String) {
            for (s in SOURCES) if (s != keep && s != MINE && s != CUSTOM) Wallpapers(ctx, s).files().drop(1).forEach { it.delete() }
        }
        private val LOCKS = java.util.concurrent.ConcurrentHashMap<String, Any>()
        private const val PER_LINK = 6
        private const val MAX_DOWNLOAD = 15L * 1024 * 1024
        private val IMAGE_URL = Regex("""https?://[^\s"'<>()\\]+?\.(?:jpe?g|png|webp)(?:\?[^\s"'<>()\\]*)?""", RegexOption.IGNORE_CASE)

        fun label(source: String) = when (source) {
            ANIME -> "二次元壁纸"
            SCENERY -> "风景大片"
            FRESH -> "清新萌宠"
            ART -> "名画"
            MINE -> "我的照片"
            CUSTOM -> "自定义链接"
            else -> "风景照片"
        }

        /** The picture source behind a [Prefs.background] value; gradient themes fall back to Bing. */
        fun sourceFor(background: String) = if (background in SOURCES) background else BING
        fun isPhoto(background: String) = background in SOURCES

        /** Cheap, smooth blur: shrink, box-blur three times, let the view scale it back up. */
        fun blurred(src: Bitmap, darken: Float = 0.45f): Bitmap {
            val w = 96
            val h = (w * src.height / src.width.toFloat()).toInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            repeat(3) { boxBlur(px, w, h, 4) }
            for (i in px.indices) {
                val c = px[i]
                px[i] = Color.rgb(
                    (Color.red(c) * (1 - darken)).toInt(),
                    (Color.green(c) * (1 - darken)).toInt(),
                    (Color.blue(c) * (1 - darken)).toInt(),
                )
            }
            small.setPixels(px, 0, w, 0, 0, w, h)
            return small
        }

        /** Dark vertical gradient used before any wallpaper is cached. */
        fun fallback(): Bitmap {
            val b = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888)
            Paint().apply {
                shader = LinearGradient(0f, 0f, 32f, 18f, Color.rgb(38, 44, 66), Color.rgb(10, 10, 14), Shader.TileMode.CLAMP)
                Canvas(b).drawRect(0f, 0f, 32f, 18f, this)
            }
            return b
        }

        private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
            val tmp = IntArray(px.size)
            pass(px, tmp, w, h, r, true)
            pass(tmp, px, w, h, r, false)
        }

        private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
            val len = if (horizontal) w else h
            val lines = if (horizontal) h else w
            for (line in 0 until lines) {
                for (i in 0 until len) {
                    var rs = 0; var gs = 0; var bs = 0; var n = 0
                    for (k in -r..r) {
                        val j = (i + k).coerceIn(0, len - 1)
                        val c = if (horizontal) src[line * w + j] else src[j * w + line]
                        rs += (c shr 16) and 0xff; gs += (c shr 8) and 0xff; bs += c and 0xff; n++
                    }
                    val out = (0xff shl 24) or ((rs / n) shl 16) or ((gs / n) shl 8) or (bs / n)
                    if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
                }
            }
        }
    }
}

/** The soft multi-colour gradients tvOS 26 uses behind the home grid. */
object Backgrounds {
    /** key -> (label, colour blobs as x, y, radius (fractions of width), colour). */
    val THEMES: LinkedHashMap<String, Pair<String, List<Blob>>> = linkedMapOf(
        "green" to ("绿色" to listOf(
            Blob(0.10f, 0.35f, 0.75f, 0xFF0B7A2E.toInt()),
            Blob(0.48f, -0.05f, 0.70f, 0xFF02B43C.toInt()),
            Blob(1.00f, 0.20f, 0.55f, 0xFF0F7F8A.toInt()),
            Blob(1.05f, 0.80f, 0.45f, 0xFF3C2B8F.toInt()),
        )),
        "blue" to ("蓝色" to listOf(
            Blob(0.10f, 0.30f, 0.75f, 0xFF0B3C8C.toInt()),
            Blob(0.50f, -0.05f, 0.70f, 0xFF1E6FE0.toInt()),
            Blob(1.00f, 0.20f, 0.55f, 0xFF16A3C4.toInt()),
            Blob(1.05f, 0.85f, 0.45f, 0xFF5B2EA6.toInt()),
        )),
        "purple" to ("紫色" to listOf(
            Blob(0.10f, 0.30f, 0.75f, 0xFF4A1C8C.toInt()),
            Blob(0.50f, -0.05f, 0.70f, 0xFF8E3BE0.toInt()),
            Blob(1.00f, 0.20f, 0.55f, 0xFFD24C9A.toInt()),
            Blob(1.05f, 0.85f, 0.45f, 0xFF2D3FA8.toInt()),
        )),
        "orange" to ("橙色" to listOf(
            Blob(0.10f, 0.30f, 0.75f, 0xFF9A3412.toInt()),
            Blob(0.50f, -0.05f, 0.70f, 0xFFF0802A.toInt()),
            Blob(1.00f, 0.20f, 0.55f, 0xFFE0466A.toInt()),
            Blob(1.05f, 0.85f, 0.45f, 0xFF6B2FA0.toInt()),
        )),
        "graphite" to ("深灰" to listOf(
            Blob(0.10f, 0.30f, 0.75f, 0xFF2A2D34.toInt()),
            Blob(0.50f, -0.05f, 0.70f, 0xFF4A505C.toInt()),
            Blob(1.00f, 0.20f, 0.55f, 0xFF36404F.toInt()),
            Blob(1.05f, 0.85f, 0.45f, 0xFF2B2A40.toInt()),
        )),
    )

    data class Blob(val x: Float, val y: Float, val r: Float, val color: Int)

    /** Rendered small; the ImageView's bilinear upscale keeps it perfectly smooth. */
    fun render(key: String): Bitmap {
        val blobs = (THEMES[key] ?: THEMES.getValue("green")).second
        val w = 480
        val h = 270
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(6, 10, 10))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (bl in blobs) {
            p.shader = android.graphics.RadialGradient(bl.x * w, bl.y * w, bl.r * w,
                intArrayOf(bl.color, Color.argb(140, Color.red(bl.color), Color.green(bl.color), Color.blue(bl.color)), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        // Darken the bottom so the lower rows sit on near-black, as in tvOS.
        p.shader = LinearGradient(0f, h * 0.45f, 0f, h.toFloat(),
            Color.TRANSPARENT, Color.argb(235, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return b
    }
}
