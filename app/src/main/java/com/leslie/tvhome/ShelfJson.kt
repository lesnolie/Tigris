package com.leslie.tvhome

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI

/** Caps and checks for a user-supplied Top Shelf feed. Pure: no network and no Android APIs. */
internal object ShelfLimits {
    const val MAX_ITEMS = 12
    const val MAX_TITLE = 120
    const val MAX_META = 180
    const val MAX_JSON_BYTES = 512 * 1024
    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    const val MAX_IMAGE_EDGE = 1920
    const val MAX_URL_CHARS = 2048

    fun isHttpUrl(raw: String): Boolean {
        val s = raw.trim()
        if (s.length !in 8..MAX_URL_CHARS) return false
        if (s.any { it.isWhitespace() || it.code < 32 }) return false
        val uri = try {
            URI(s)
        } catch (e: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        return !uri.host.isNullOrBlank()
    }

    /** Power-of-two [android.graphics.BitmapFactory.Options.inSampleSize] so the long edge stays near [maxEdge]. */
    fun sampleSize(width: Int, height: Int, maxEdge: Int = MAX_IMAGE_EDGE): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        var sample = 1
        val longest = maxOf(width, height)
        while (sample < longest && longest / (sample.toLong() * 2) >= maxEdge) sample *= 2
        return sample
    }

    fun readAtMost(input: InputStream, maxBytes: Int): ByteArray {
        if (maxBytes < 0) throw IOException("response too large")
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (n == 0) continue
            if (total > maxBytes - n) throw IOException("response too large")
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}

/**
 * The custom Top Shelf document: a JSON array of `{title, meta, image}`.
 * Extra fields (a per-item page URL, for example) are ignored. The shelf is
 * display-only; confirming the dock tile still opens the linked app.
 * Returns null when the body is not a JSON array, and an empty list when the
 * array contains nothing usable.
 */
internal object ShelfJson {
    data class Item(val title: String, val meta: String, val image: String)

    fun parse(text: String): List<Item>? {
        val body = text.removePrefix("\uFEFF").trim()
        if (body.isEmpty() || !body.startsWith("[")) return null
        val array = try {
            JSONArray(body)
        } catch (e: Exception) {
            return null
        }
        val out = ArrayList<Item>(ShelfLimits.MAX_ITEMS)
        for (i in 0 until array.length()) {
            if (out.size >= ShelfLimits.MAX_ITEMS) break
            val obj = array.optJSONObject(i) ?: continue
            val title = field(obj, "title").take(ShelfLimits.MAX_TITLE)
            val image = field(obj, "image")
            if (title.isEmpty() || !ShelfLimits.isHttpUrl(image)) continue
            out.add(Item(title, field(obj, "meta").take(ShelfLimits.MAX_META), image))
        }
        return out
    }

    private fun field(obj: JSONObject, key: String): String {
        if (!obj.has(key) || obj.isNull(key)) return ""
        val value = obj.opt(key)
        if (value !is String && value !is Number) return ""
        return value.toString().trim()
    }
}
