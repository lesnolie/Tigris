package com.leslie.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Top Shelf content. Android TV's own channel API is empty on the TVs this
 * was written for, so a few apps are filled from public web APIs: Bilibili
 * popular videos (云视听小电视 and the third-party client com.tutu.myblbl)
 * and Douyu live rooms.
 *
 * Settings can also point one dock app at a JSON URL. That feed is cached
 * with the same 15-minute policy. A bad address, unusable JSON, or a timeout
 * leaves the built-in feed for that package in place when one exists.
 */
object Shelf {
    data class Item(val title: String, val meta: String, val image: String)
    data class Source(val name: String, val fetch: () -> List<Item>)

    /** One user-entered feed, shown when [pkg] is focused in the dock. */
    data class Custom(val pkg: String, val url: String)

    internal enum class ShelfPick { CUSTOM, BUILTIN, NONE }

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    private const val TTL = 15 * 60_000L
    /** Built-in responses are small; the cap only stops a runaway body. */
    private const val BUILTIN_JSON_BYTES = 2 * 1024 * 1024

    /** Package → which public feed it shares. Both Bilibili clients use `bilibili`. */
    internal val builtInPackages = mapOf(
        "com.xiaodianshi.tv.yst" to "bilibili",
        "com.tutu.myblbl" to "bilibili",
        "com.douyu.xl.douyutv" to "douyu",
    )

    private data class Cached(val at: Long, val name: String, val items: List<Item>)

    private val sources get() = builtInPackages.mapValues { (_, kind) ->
        if (kind == "bilibili") Source(I18n.text(R.string.i18n_shelf_001), ::bilibili)
        else Source(I18n.text(R.string.i18n_shelf_002), ::douyu)
    }
    private val cache = HashMap<String, Cached>()
    private val shownName = HashMap<String, String>()

    /**
     * Custom wins only when the linked package matches and the body was usable.
     * Otherwise a known package keeps its built-in feed.
     */
    internal fun pickShelf(pkg: String, custom: Custom?, customOk: Boolean): ShelfPick {
        val linked = custom?.pkg?.trim().orEmpty()
        val url = custom?.url?.trim().orEmpty()
        if (linked == pkg && ShelfLimits.isHttpUrl(url) && customOk) return ShelfPick.CUSTOM
        if (pkg in builtInPackages) return ShelfPick.BUILTIN
        return ShelfPick.NONE
    }

    fun has(pkg: String, custom: Custom? = null) = pickShelf(pkg, custom, customOk = true) != ShelfPick.NONE

    fun sourceName(pkg: String) = synchronized(cache) {
        shownName[pkg] ?: sources[pkg]?.name ?: ""
    }

    /** Items for [pkg], cached for a while; empty if the app has no shelf or the network failed. */
    fun items(pkg: String, custom: Custom? = null): List<Item> {
        if (pickShelf(pkg, custom, customOk = true) == ShelfPick.CUSTOM) {
            val url = custom!!.url.trim()
            val key = "custom\u0000$pkg\u0000$url"
            readCache(key, pkg)?.let { return it }
            val fetched = try {
                fetchCustom(url)
            } catch (e: Exception) {
                Log.w("Shelf", "custom $pkg failed", e)
                null
            }
            if (!fetched.isNullOrEmpty()) {
                val name = I18n.text(R.string.shelf_custom_source)
                writeCache(key, pkg, name, fetched)
                return fetched
            }
        }
        val src = sources[pkg] ?: return emptyList()
        readCache(pkg, pkg)?.let { return it }
        val list = try {
            src.fetch()
        } catch (e: Exception) {
            Log.w("Shelf", "$pkg failed", e)
            emptyList()
        }
        if (list.isNotEmpty()) writeCache(pkg, pkg, src.name, list)
        return list
    }

    /** Downloads (or reads from the disk cache) an item's picture. */
    fun image(ctx: Context, url: String): Bitmap? {
        if (!ShelfLimits.isHttpUrl(url)) return null
        val dir = File(ctx.cacheDir, "shelf").apply { mkdirs() }
        val f = File(dir, Integer.toHexString(url.hashCode()) + ".jpg")
        if (!f.exists()) try {
            val tmp = File(dir, f.name + ".part")
            tmp.writeBytes(get(url, ShelfLimits.MAX_IMAGE_BYTES))
            tmp.renameTo(f)
            // Keep the cache small: the newest 60 pictures.
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(60)?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w("Shelf", "image $url failed", e)
            return null
        }
        return decodeCapped(f.path)
            ?.let(::trimBars)
            ?.let(::darken)
            // Start the GPU upload now, off the frame that first shows it.
            ?.also { it.prepareToDraw() }
    }

    private fun fetchCustom(url: String): List<Item>? {
        val text = get(url, ShelfLimits.MAX_JSON_BYTES).toString(Charsets.UTF_8)
        val parsed = ShelfJson.parse(text) ?: return null
        if (parsed.isEmpty()) return null
        return parsed.map { Item(it.title, it.meta, it.image) }
    }

    private fun readCache(key: String, pkg: String): List<Item>? = synchronized(cache) {
        val hit = cache[key] ?: return null
        if (System.currentTimeMillis() - hit.at >= TTL) return null
        shownName[pkg] = hit.name
        hit.items
    }

    private fun writeCache(key: String, pkg: String, name: String, items: List<Item>) {
        synchronized(cache) {
            cache[key] = Cached(System.currentTimeMillis(), name, items)
            shownName[pkg] = name
        }
    }

    private fun decodeCapped(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = ShelfLimits.sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    /**
     * Bakes the scrim that keeps the title readable into the picture itself:
     * a full-screen gradient layer on top would cost the TV's GPU three more
     * full-screen passes on every frame of a cross-fade.
     */
    private fun darken(b: Bitmap): Bitmap {
        val out = if (b.isMutable) b else b.copy(Bitmap.Config.ARGB_8888, true).also { b.recycle() }
        ShelfScrim().apply { setBounds(0, 0, out.width, out.height) }.draw(android.graphics.Canvas(out))
        return out
    }

    /** Live-stream covers are often letterboxed; cut the black bars so the picture fills the screen. */
    private fun trimBars(b: Bitmap): Bitmap {
        fun dark(y: Int): Boolean {
            var max = 0
            for (k in 0 until 32) {
                val c = b.getPixel(k * (b.width - 1) / 31, y)
                max = maxOf(max, (c shr 16) and 255, (c shr 8) and 255, c and 255)
            }
            return max < 28
        }
        var top = 0
        while (top < b.height / 4 && dark(top)) top++
        var bottom = b.height
        while (bottom > b.height * 3 / 4 && dark(bottom - 1)) bottom--
        if (top < 4 && b.height - bottom < 4) return b
        return Bitmap.createBitmap(b, 0, top, b.width, bottom - top)
    }

    private fun bilibili(): List<Item> {
        val list = JSONObject(get("https://api.bilibili.com/x/web-interface/popular?ps=12&pn=1", BUILTIN_JSON_BYTES).decodeToString())
            .getJSONObject("data").getJSONArray("list")
        return (0 until list.length()).map { list.getJSONObject(it) }.map { v ->
            Item(
                v.optString("title"),
                I18n.text(R.string.i18n_shelf_003, v.getJSONObject("owner").optString("name"), count(v.getJSONObject("stat").optLong("view"))),
                v.optString("pic").replace("http://", "https://") + "@1280w_720h_1c.jpg",
            )
        }
    }

    private fun douyu(): List<Item> {
        val list = JSONObject(get("https://www.douyu.com/japi/weblist/apinc/allpage/6/1", BUILTIN_JSON_BYTES).decodeToString())
            .getJSONObject("data").getJSONArray("rl")
        return (0 until list.length()).map { list.getJSONObject(it) }
            .filter { it.optString("rs16").isNotEmpty() }
            .take(12)
            .map { r ->
                Item(
                    r.optString("rn"),
                    I18n.text(R.string.i18n_shelf_004, r.optString("nn"), r.optString("c2name"), count(r.optLong("ol"))),
                    // "/dy4" is the 1280×720 JPEG rendition of the AVIF cover.
                    r.optString("rs16").substringBeforeLast('/') + "/dy4",
                )
            }
    }

    private fun count(n: Long): String = if (I18n.isChinese()) {
        when {
            n >= 100_000_000 -> I18n.text(R.string.i18n_shelf_005, n / 100_000_000.0)
            n >= 10_000 -> I18n.text(R.string.i18n_shelf_006, n / 10_000.0)
            else -> n.toString()
        }
    } else {
        when {
            n >= 1_000_000 -> I18n.text(R.string.i18n_shelf_005, n / 1_000_000.0)
            n >= 1_000 -> I18n.text(R.string.i18n_shelf_006, n / 1_000.0)
            else -> n.toString()
        }
    }

    private fun get(url: String, maxBytes: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 8000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        // B站's API answers with an empty body when sent a Referer from outside its pages.
        if ("douyu" in url) c.setRequestProperty("Referer", "https://www.douyu.com/")
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            val advertised = c.contentLengthLong
            if (advertised > maxBytes) throw java.io.IOException("response too large")
            return c.inputStream.use { ShelfLimits.readAtMost(it, maxBytes) }
        } finally {
            c.disconnect()
        }
    }
}
