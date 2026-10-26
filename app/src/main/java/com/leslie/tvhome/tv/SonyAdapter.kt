package com.leslie.tvhome.tv

import com.leslie.tvhome.I18n
import com.leslie.tvhome.R

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sony BRAVIA: backlight, volume and HDMI inputs through the TV's own BRAVIA IP-control API.
 * Each falls back to standard Android when the API doesn't answer (IP control switched off).
 */
class SonyAdapter(ctx: Context) : AndroidAdapter(ctx) {
    private var apiVolume = false

    override fun matches() = Build.MANUFACTURER.equals("Sony", true)

    override fun brightness() = Bravia.brightness()

    override fun setBrightness(v: Int) = Bravia.setBrightness(v)

    override fun volume(): Level? {
        Bravia.volume()?.let { apiVolume = true; return it }
        apiVolume = false
        return super.volume()
    }

    override fun setVolume(target: String, v: Int) =
        if (apiVolume) Bravia.setVolume(target, v) else super.setVolume(target, v)

    override fun inputs() = Bravia.inputs().ifEmpty { super.inputs() }

    override fun currentInput() = Bravia.currentInput()

    override fun setInput(i: Input) {
        if (i.uri.startsWith(URI_PREFIX)) Bravia.setInput(i.uri) else super.setInput(i)
    }

    /** Sony reports the input being shown, so with Sony's inputs listed and none current, the TV is on the home screen. */
    override fun inputSummary(list: List<Input>) =
        if (list.firstOrNull()?.uri?.startsWith(URI_PREFIX) == true) I18n.text(R.string.i18n_sonyadapter_001) else super.inputSummary(list)

    private companion object {
        const val URI_PREFIX = "extInput:"
    }
}

/**
 * The TV's own BRAVIA IP-control API on 127.0.0.1:80. Reads work without
 * auth; writes need the "auth" cookie, which a registered client gets back
 * from actRegister without a PIN, but only once "tvhome:1" has been paired
 * with the PIN the TV shows.
 *
 * On a TV that was never paired the PIN-less actRegister can't succeed, so
 * only writes ask for the cookie and a failed attempt backs off. Otherwise
 * every read would raise Sony's registration dialog over the home screen,
 * again as soon as it was dismissed.
 */
private object Bravia {
    private const val BASE = "http://127.0.0.1/sony/"
    private const val CLIENT = "tvhome:1"
    /** How long an unpaired TV is left alone before actRegister is tried again. */
    private const val RETRY_AFTER = 30 * 60 * 1000L
    private var cookie: String? = null
    private var cookieAt = 0L
    private var failedAt = 0L

    fun volume(): Level? = call("audio", "getVolumeInformation")?.optJSONArray(0)?.let { list ->
        val o = (0 until list.length()).map { list.getJSONObject(it) }
            .firstOrNull { it.optString("target") == "speaker" } ?: list.optJSONObject(0) ?: return null
        Level(o.optInt("volume"), o.optInt("minVolume", 0), o.optInt("maxVolume", 100), o.optString("target"))
    }

    fun setVolume(target: String, v: Int) =
        call("audio", "setAudioVolume", JSONObject().put("target", target).put("volume", v.toString()).put("ui", "off"), "1.2") != null

    fun brightness(): Level? = call("video", "getPictureQualitySettings", JSONObject().put("target", "brightness"))
        ?.optJSONArray(0)?.optJSONObject(0)?.let { o ->
            val c = o.optJSONArray("candidate")?.optJSONObject(0)
            Level(o.optString("currentValue").toIntOrNull() ?: 0, c?.optInt("min", 0) ?: 0, c?.optInt("max", 50) ?: 50)
        }

    fun setBrightness(v: Int) = call("video", "setPictureQualitySettings",
        JSONObject().put("settings", JSONArray().put(JSONObject().put("target", "brightness").put("value", v.toString())))) != null

    fun inputs(): List<Input> = call("avContent", "getCurrentExternalInputsStatus", version = "1.1")
        ?.optJSONArray(0)?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }
                .filter { it.optString("uri").startsWith("extInput:hdmi") }
                .map { Input(it.optString("uri"), it.optString("title"), it.optString("label"), it.optBoolean("connection")) }
        } ?: emptyList()

    /** Uri of the HDMI input being shown, or null on the home screen / apps. */
    fun currentInput(): String? =
        call("avContent", "getPlayingContentInfo")?.optJSONObject(0)?.optString("uri")?.takeIf { it.isNotEmpty() }

    fun setInput(uri: String) = call("avContent", "setPlayContent", JSONObject().put("uri", uri)) != null

    // ---- transport ----------------------------------------------------------------

    /**
     * Which methods the TV refuses without the cookie, measured on a BRAVIA 4K VH2: the setters,
     * plus getPlayingContentInfo. getVolumeInformation, getPictureQualitySettings and
     * getCurrentExternalInputsStatus all answer unauthenticated, so they must never ask for it.
     */
    private fun needsAuth(method: String) =
        method.startsWith("set") || method == "getPlayingContentInfo"

    private fun call(
        service: String,
        method: String,
        param: JSONObject? = null,
        version: String = "1.0",
        retry: Boolean = true,
        needsAuth: Boolean = needsAuth(method),
    ): JSONArray? {
        val body = JSONObject().put("method", method).put("id", 1).put("version", version)
            .put("params", JSONArray().apply { if (param != null) put(param) })
        return try {
            val (code, text, _) = post(service, body.toString(), if (needsAuth) authCookie() else null)
            val o = JSONObject(text)
            val err = o.optJSONArray("error")
            if (err != null) {
                if (retry && needsAuth && (err.optInt(0) == 403 || err.optInt(0) == 401 || code == 403)) {
                    cookie = null
                    return call(service, method, param, version, retry = false, needsAuth = true)
                }
                Log.w("Bravia", "$method: $err")
                null
            } else o.optJSONArray("result")
        } catch (e: Exception) {
            Log.w("Bravia", "$method failed", e)
            null
        }
    }

    @Synchronized
    private fun authCookie(): String? {
        val now = System.currentTimeMillis()
        if (cookie != null && now - cookieAt < 7 * 24 * 3_600_000L) return cookie
        // An unpaired TV answers every attempt with its registration dialog; asking again right
        // away would put that dialog back over the home screen the moment the user dismissed it.
        if (failedAt != 0L && now - failedAt < RETRY_AFTER) return null
        val body = JSONObject().put("method", "actRegister").put("id", 1).put("version", "1.0")
            .put("params", JSONArray()
                .put(JSONObject().put("clientid", CLIENT).put("nickname", "TVHome").put("level", "private"))
                .put(JSONArray().put(JSONObject().put("value", "yes").put("function", "WOL"))))
        return try {
            val (_, _, setCookie) = post("accessControl", body.toString(), null)
            cookie = setCookie?.substringAfter("auth=", "")?.substringBefore(';')?.takeIf { it.isNotEmpty() }
            cookieAt = System.currentTimeMillis()
            failedAt = if (cookie == null) cookieAt else 0L
            cookie
        } catch (e: Exception) {
            failedAt = System.currentTimeMillis()
            null
        }
    }

    private fun post(service: String, body: String, auth: String?): Triple<Int, String, String?> {
        val c = URL(BASE + service).openConnection() as HttpURLConnection
        c.connectTimeout = 2000
        c.readTimeout = 4000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        if (auth != null) c.setRequestProperty("Cookie", "auth=$auth")
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() } ?: "{}"
        return Triple(code, text, c.getHeaderField("Set-Cookie"))
    }
}
