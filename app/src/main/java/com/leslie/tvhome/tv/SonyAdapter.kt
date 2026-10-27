package com.leslie.tvhome.tv

import com.leslie.tvhome.I18n
import com.leslie.tvhome.R

import android.content.Context
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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

    init { Bravia.initialize(ctx.applicationContext) }

    override fun matches() = Build.MANUFACTURER.equals("Sony", true)

    override fun brightness() = Bravia.brightness()

    override fun setBrightness(v: Int) = Bravia.setBrightness(v)

    override fun volume(): Level? {
        Bravia.volume()?.let { apiVolume = true; return it }
        apiVolume = false
        return super.volume()
    }

    override fun setVolume(target: String, v: Int) =
        if (apiVolume && Bravia.setVolume(target, v)) true else super.setVolume(target, v)

    override fun inputs() = Bravia.inputs().ifEmpty { super.inputs() }

    override fun currentInput() = Bravia.currentInput()

    override fun setInput(i: Input) {
        if (!i.uri.startsWith(URI_PREFIX)) return super.setInput(i)
        if (Bravia.setInput(i.uri)) return
        // extInput: is a BRAVIA URI, not an Android passthrough channel. Translate before fallback.
        val target = SonyInputFallback.find(i.uri, super.inputs())
        if (target != null && start(Intent(Intent.ACTION_VIEW, Uri.parse(target.uri)))) return
        val opened = start(settingsIntent())
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, I18n.text(if (opened) R.string.sony_input_choose_system
                else R.string.sony_input_unavailable), Toast.LENGTH_LONG).show()
        }
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
 * from actRegister without a PIN after the client has been paired. Background
 * reads must never initiate registration, including getPlayingContentInfo.
 * Inspired by PR #46; failure backoff also survives process restarts.
 */
private object Bravia {
    private const val BASE = "http://127.0.0.1/sony/"
    private const val CLIENT = "tvhome:1"
    private lateinit var session: BraviaSession

    @Synchronized
    fun initialize(ctx: Context) {
        if (::session.isInitialized) return
        val prefs = ctx.getSharedPreferences("bravia_auth", Context.MODE_PRIVATE)
        session = BraviaSession(System::currentTimeMillis,
            { prefs.getLong("failed_at", 0) },
            { prefs.edit().putLong("failed_at", it).commit(); Unit },
            ::registerCookie)
    }

    fun volume(): Level? = call("audio", "getVolumeInformation")?.optJSONArray(0)?.let { list ->
        val o = (0 until list.length()).map { list.getJSONObject(it) }
            .firstOrNull { it.optString("target") == "speaker" } ?: list.optJSONObject(0) ?: return null
        Level(o.optInt("volume"), o.optInt("minVolume", 0), o.optInt("maxVolume", 100), o.optString("target"))
    }

    fun setVolume(target: String, v: Int) =
        call("audio", "setAudioVolume", JSONObject().put("target", target).put("volume", v.toString()).put("ui", "off"), "1.2", write = true) != null

    fun brightness(): Level? = call("video", "getPictureQualitySettings", JSONObject().put("target", "brightness"))
        ?.optJSONArray(0)?.optJSONObject(0)?.let { o ->
            val c = o.optJSONArray("candidate")?.optJSONObject(0)
            Level(o.optString("currentValue").toIntOrNull() ?: 0, c?.optInt("min", 0) ?: 0, c?.optInt("max", 50) ?: 50)
        }

    fun setBrightness(v: Int) = call("video", "setPictureQualitySettings",
        JSONObject().put("settings", JSONArray().put(JSONObject().put("target", "brightness").put("value", v.toString()))), write = true) != null

    fun inputs(): List<Input> = call("avContent", "getCurrentExternalInputsStatus", version = "1.1")
        ?.optJSONArray(0)?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }
                .filter { it.optString("uri").startsWith("extInput:hdmi") }
                .map { Input(it.optString("uri"), it.optString("title"), it.optString("label"), it.optBoolean("connection")) }
        } ?: emptyList()

    /** Uri of the HDMI input being shown, or null on the home screen / apps. */
    fun currentInput(): String? =
        call("avContent", "getPlayingContentInfo")?.optJSONObject(0)?.optString("uri")?.takeIf { it.isNotEmpty() }

    fun setInput(uri: String) = call("avContent", "setPlayContent", JSONObject().put("uri", uri), write = true) != null

    // ---- transport ----------------------------------------------------------------

    private fun call(service: String, method: String, param: JSONObject? = null, version: String = "1.0", retry: Boolean = true,
                     write: Boolean = false): JSONArray? {
        val body = JSONObject().put("method", method).put("id", 1).put("version", version)
            .put("params", JSONArray().apply { if (param != null) put(param) })
        return try {
            val auth = session.cookie(allowRegistration = write)
            if (write && auth == null) return null
            val (code, text, _) = post(service, body.toString(), auth)
            val o = runCatching { JSONObject(text) }.getOrNull()
            val err = o?.optJSONArray("error")
            if (err != null || code >= 400) {
                if (err?.optInt(0) in listOf(401, 403) || code in listOf(401, 403)) {
                    session.invalidate(backoff = write && !retry)
                    if (retry && write && auth != null) return call(service, method, param, version, retry = false, write = true)
                }
                Log.w("Bravia", "$method: $err")
                null
            } else o?.optJSONArray("result")
        } catch (e: Exception) {
            Log.w("Bravia", "$method failed", e)
            null
        }
    }

    private fun registerCookie(): String? {
        val body = JSONObject().put("method", "actRegister").put("id", 1).put("version", "1.0")
            .put("params", JSONArray()
                .put(JSONObject().put("clientid", CLIENT).put("nickname", "TVHome").put("level", "private"))
                .put(JSONArray().put(JSONObject().put("value", "yes").put("function", "WOL"))))
        val (code, text, setCookie) = post("accessControl", body.toString(), null)
        if (code >= 400 || JSONObject(text).optJSONArray("error") != null) return null
        return setCookie?.substringAfter("auth=", "")?.substringBefore(';')?.takeIf { it.isNotEmpty() }
    }

    private fun post(service: String, body: String, auth: String?): Triple<Int, String, String?> {
        val c = URL(BASE + service).openConnection() as HttpURLConnection
        try {
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
        } finally { c.disconnect() }
    }
}
