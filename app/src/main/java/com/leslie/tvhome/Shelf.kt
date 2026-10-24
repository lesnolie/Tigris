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
 * Top Shelf content. Android TV's own channel API is empty on this TV (none
 * of the installed apps publish to it), so for a few apps the shelf is filled
 * from the service's public web API instead: B站 popular videos, 斗鱼 live rooms.
 */
object Shelf {
    data class Item(val title: String, val meta: String, val image: String)
    data class Source(val name: String, val fetch: () -> List<Item>)

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    private const val TTL = 15 * 60_000L

    private val sources = mapOf(
        "com.xiaodianshi.tv.yst" to Source("Bilibili · Trending", ::bilibili),
        "com.douyu.xl.douyutv" to Source("Douyu · Live now", ::douyu),
    )
    private val cache = HashMap<String, Pair<Long, List<Item>>>()

    fun has(pkg: String) = pkg in sources
    fun sourceName(pkg: String) = sources[pkg]?.name ?: ""

    /** Items for [pkg], cached for a while; empty if the app has no shelf or the network failed. */
    fun items(pkg: String): List<Item> {
        val src = sources[pkg] ?: return emptyList()
        synchronized(cache) {
            cache[pkg]?.let { (at, list) -> if (System.currentTimeMillis() - at < TTL) return list }
        }
        val list = try { src.fetch() } catch (e: Exception) { Log.w("Shelf", "$pkg failed", e); emptyList() }
        if (list.isNotEmpty()) synchronized(cache) { cache[pkg] = System.currentTimeMillis() to list }
        return list
    }

    /** Downloads (or reads from the disk cache) an item's picture. */
    fun image(ctx: Context, url: String): Bitmap? {
        val dir = File(ctx.cacheDir, "shelf").apply { mkdirs() }
        val f = File(dir, Integer.toHexString(url.hashCode()) + ".jpg")
        if (!f.exists()) try {
            val tmp = File(dir, f.name + ".part")
            tmp.writeBytes(get(url))
            tmp.renameTo(f)
            // Keep the cache small: the newest 60 pictures.
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(60)?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w("Shelf", "image $url failed", e)
            return null
        }
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?.let(::trimBars)
            ?.let(::darken)
            // Start the GPU upload now, off the frame that first shows it.
            ?.also { it.prepareToDraw() }
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
        val list = JSONObject(get("https://api.bilibili.com/x/web-interface/popular?ps=12&pn=1").decodeToString())
            .getJSONObject("data").getJSONArray("list")
        return (0 until list.length()).map { list.getJSONObject(it) }.map { v ->
            Item(
                v.optString("title"),
                "${v.getJSONObject("owner").optString("name")} · ${count(v.getJSONObject("stat").optLong("view"))} views",
                v.optString("pic").replace("http://", "https://") + "@1280w_720h_1c.jpg",
            )
        }
    }

    private fun douyu(): List<Item> {
        val list = JSONObject(get("https://www.douyu.com/japi/weblist/apinc/allpage/6/1").decodeToString())
            .getJSONObject("data").getJSONArray("rl")
        return (0 until list.length()).map { list.getJSONObject(it) }
            .filter { it.optString("rs16").isNotEmpty() }
            .take(12)
            .map { r ->
                Item(
                    r.optString("rn"),
                    "${r.optString("nn")} · ${r.optString("c2name")} · ${count(r.optLong("ol"))} hot",
                    // "/dy4" is the 1280×720 JPEG rendition of the AVIF cover.
                    r.optString("rs16").substringBeforeLast('/') + "/dy4",
                )
            }
    }

    private fun count(n: Long) = when {
        n >= 100_000_000 -> "%.1f×100M".format(n / 100_000_000.0)
        n >= 10_000 -> "%.1f×10K".format(n / 10_000.0)
        else -> n.toString()
    }

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", UA)
        // B站's API answers with an empty body when sent a Referer from outside its pages.
        if ("douyu" in url) c.setRequestProperty("Referer", "https://www.douyu.com/")
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}
