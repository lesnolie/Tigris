package com.leslie.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.os.Build
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Inet4Address
import java.security.SecureRandom

/**
 * The page a phone opens after scanning the QR code in the Wallpaper app: choose the background and the
 * screensaver, send photos, paste picture links, and upload the boot animation. Typing a link or picking photos with a remote
 * is slow; with a phone it is not. After Aerial's phone page (github.com/pengchujin/aerial-tv, MIT).
 *
 * It listens on the local network only while [WallpaperActivity] (the Wallpaper app) is open (and a few minutes after, so
 * an upload can finish), and every address carries a random token shown only in the QR code, so
 * another device on the network cannot use it by guessing.
 */
class PhoneServer private constructor(private val ctx: Context, private val token: String) : NanoHTTPD(PORT) {
    private val prefs = Prefs(ctx)

    override fun serve(session: IHTTPSession): Response {
        val prefix = "/$token"
        val uri = session.uri
        if (!uri.startsWith(prefix)) return text(Response.Status.NOT_FOUND, "Not found")
        val path = uri.removePrefix(prefix).ifEmpty { "/" }
        val post = session.method == Method.POST
        return runCatching {
            when {
                path == "/" -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page())
                path == "/api/state" -> json(state())
                path.startsWith("/thumb/") -> thumb(path.removePrefix("/thumb/"))
                post && path == "/api/background" -> {
                    val v = form(session)["id"].orEmpty()
                    if (v in Wallpapers.SOURCES || v in Backgrounds.THEMES) {
                        prefs.background = v
                        Wallpapers.trimExcept(ctx, v)
                    }
                    json(state())
                }
                post && path == "/api/screensaver" -> {
                    val v = form(session)["mode"].orEmpty()
                    if (v == Prefs.SAVER_PHOTOS || v == Prefs.SAVER_AERIAL) prefs.saverMode = v
                    json(state())
                }
                post && path == "/api/links" -> {
                    val links = form(session)["links"].orEmpty().lines().map { it.trim() }
                        .filter { it.startsWith("http://") || it.startsWith("https://") }.distinct().take(20)
                    prefs.customWallUrls = links.joinToString("\n")
                    if (links.isNotEmpty()) {
                        prefs.background = Wallpapers.CUSTOM
                        // Fetching can take a while; the page polls the state for the count.
                        Thread { runCatching { Wallpapers(ctx, Wallpapers.CUSTOM).refresh(force = true) } }.start()
                    }
                    json(state())
                }
                post && path == "/api/delete" -> {
                    val f = form(session)
                    Wallpapers(ctx, if (f["source"] == Wallpapers.CUSTOM) Wallpapers.CUSTOM else Wallpapers.MINE).delete(f["id"].orEmpty())
                    json(state())
                }
                post && path == "/api/upload" -> upload(session)
                post && path == "/api/intro/source" -> {
                    val v = form(session)["source"].orEmpty()
                    if (v == Prefs.INTRO_VIDEO || v == Prefs.INTRO_WORDMARK) prefs.introSource = v
                    json(state())
                }
                post && path == "/api/intro" -> {
                    val v = form(session)["mode"].orEmpty()
                    if (v in listOf(Prefs.INTRO_OFF, Prefs.INTRO_BOOT, Prefs.INTRO_WAKE)) {
                        prefs.intro = v
                        // This boot counts as played: turning it on doesn't start it on the next return home.
                        prefs.introBoot = Intro.bootStamp(ctx)
                    }
                    json(state())
                }
                post && path == "/api/intro/upload" -> uploadIntro(session)
                post && path == "/api/avatar/upload" -> uploadAvatar(session)
                post && path == "/api/avatar/delete" -> {
                    session.parseBody(HashMap())
                    synchronized(this) { SettingsActivity.avatarFile(ctx)?.delete() }
                    json(state())
                }
                post && path == "/api/icon/upload" -> uploadIcon(session)
                post && path == "/api/backdrop/upload" -> uploadBackdrop(session)
                post && path == "/api/backdrop/delete" -> {
                    session.parseBody(HashMap())
                    backdropFiles().forEach { it.delete() }
                    json(state())
                }
                post && path == "/api/icon/delete" -> {
                    session.parseBody(HashMap())
                    iconFiles().forEach { it.delete() }
                    json(state())
                }
                post && path == "/api/intro/delete" -> {
                    session.parseBody(HashMap())
                    introFiles().forEach { it.delete() }
                    json(state())
                }
                else -> text(Response.Status.NOT_FOUND, "Not found")
            }
        }.getOrElse { text(Response.Status.INTERNAL_ERROR, it.message ?: "error") }
    }

    private fun page(): String = ctx.assets.open("web/index.html").bufferedReader().use { it.readText() }
        .replace("{{DEVICE}}", Build.MODEL.replace("_", " "))

    private fun names(source: String) = JSONArray().apply { Wallpapers(ctx, source).files().forEach { put(it.name) } }

    private fun state(): JSONObject = JSONObject()
        .put("background", prefs.background)
        .put("screensaver", prefs.saverMode)
        .put("links", prefs.customWallUrls)
        .put("sources", JSONArray().apply {
            Wallpapers.SOURCES.forEach { put(JSONObject().put("id", it).put("label", Wallpapers.label(it))) }
        })
        .put("themes", JSONArray().apply {
            Backgrounds.THEMES.forEach { (k, v) -> put(JSONObject().put("id", k).put("label", v.first)) }
        })
        .put("intro", prefs.intro)
        .put("introVideo", Intro.videoFile(ctx)?.length() ?: 0L)
        .put("introSource", prefs.introSource)
        .put("avatar", SettingsActivity.avatarFile(ctx) != null)
        .put("icon", SettingsActivity.iconFile(ctx) != null)
        .put("backdrop", SettingsActivity.backdropFile(ctx) != null)
        .put("mine", names(Wallpapers.MINE))
        .put("custom", names(Wallpapers.CUSTOM))

    private fun thumb(rest: String): Response {
        val (source, name) = rest.split('/', limit = 2).takeIf { it.size == 2 } ?: return text(Response.Status.NOT_FOUND, "")
        if (source !in listOf(Wallpapers.MINE, Wallpapers.CUSTOM) || name.contains('/') || !name.endsWith(".jpg"))
            return text(Response.Status.NOT_FOUND, "")
        val f = Wallpapers(ctx, source).files().firstOrNull { it.name == name } ?: return text(Response.Status.NOT_FOUND, "")
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 })
            ?: return text(Response.Status.NOT_FOUND, "")
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bmp.recycle()
        val bytes = out.toByteArray()
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "max-age=3600")
        }
    }

    private fun upload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val mine = Wallpapers(ctx, Wallpapers.MINE)
        // NanoHTTPD stores each part in a temp file; several photos arrive as photo, photo1, photo2...
        var added = 0
        for (path in files.values) {
            val f = File(path)
            if (f.exists() && f.length() in 1..MAX_UPLOAD && mine.add(f.readBytes()) != null) added++
        }
        if (added > 0) prefs.background = Wallpapers.MINE
        return json(state().put("added", added))
    }

    /** The boot animation: one video, kept in app storage (where [Intro.videoFile] looks first). */
    private fun uploadIntro(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val f = files.values.map { File(it) }.firstOrNull { it.exists() && it.length() in 1..MAX_INTRO }
            ?: return json(state().put("ok", false))
        // Only something Android can play as video; anything else would leave the intro black.
        val video = runCatching {
            MediaMetadataRetriever().run {
                try { setDataSource(f.path); extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" } finally { release() }
            }
        }.getOrDefault(false)
        if (!video) return json(state().put("ok", false))
        val dest = File(ctx.filesDir, Intro.FILE)
        val tmp = File(ctx.filesDir, Intro.FILE + ".part")
        f.copyTo(tmp, overwrite = true)
        // One pushed over adb would otherwise come back once this one is deleted.
        introFiles().forEach { it.delete() }
        tmp.renameTo(dest)
        return json(state().put("ok", true))
    }

    /**
     * Decodes at the smallest power-of-two reduction still at least [width] wide: a 48 MP phone photo
     * decoded whole needs about 190 MB, more than this app's heap.
     */
    private fun decodeAtMost(f: File, width: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (o.outWidth / (sample * 2) >= width) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** The settings icon: any picture, kept as PNG, at most 960 px wide (a tile is drawn at 400 or so). */
    @Synchronized
    private fun uploadAvatar(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val file = files.values.map { File(it) }.firstOrNull { it.isFile && it.length() in 1..MAX_UPLOAD }
            ?: return json(state().put("ok", false))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return json(state().put("ok", false))
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val src = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return json(state().put("ok", false))
        val size = minOf(src.width, src.height)
        val crop = Bitmap.createBitmap(src, (src.width - size) / 2, (src.height - size) / 2, size, size)
        val scaled = Bitmap.createScaledBitmap(crop, 256, 256, true)
        val target = android.util.AtomicFile(File(ctx.filesDir, SettingsActivity.AVATAR_FILE))
        val output = target.startWrite()
        try {
            check(scaled.compress(Bitmap.CompressFormat.PNG, 100, output))
            target.finishWrite(output)
        } catch (e: Exception) { target.failWrite(output); throw e }
        finally { listOf(src, crop, scaled).distinct().forEach { it.recycle() } }
        return json(state().put("ok", true))
    }

    private fun uploadIcon(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val src = files.values.map { File(it) }.firstOrNull { it.exists() && it.length() in 1..MAX_UPLOAD }
            ?.let { decodeAtMost(it, 960) } ?: return json(state().put("ok", false))
        val bmp = if (src.width > 960) Bitmap.createScaledBitmap(src, 960, src.height * 960 / src.width, true) else src
        val tmp = File(ctx.filesDir, SettingsActivity.ICON_FILE + ".part")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        iconFiles().forEach { it.delete() }
        tmp.renameTo(File(ctx.filesDir, SettingsActivity.ICON_FILE))
        prefs.ownIcon = true
        return json(state().put("ok", true))
    }

    /** The settings page background: any picture, kept as JPEG at most 1920 px wide. */
    private fun uploadBackdrop(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val src = files.values.map { File(it) }.firstOrNull { it.exists() && it.length() in 1..MAX_UPLOAD }
            ?.let { decodeAtMost(it, 1920) } ?: return json(state().put("ok", false))
        val bmp = if (src.width > 1920) Bitmap.createScaledBitmap(src, 1920, src.height * 1920 / src.width, true) else src
        val tmp = File(ctx.filesDir, SettingsActivity.BACKDROP_FILE + ".part")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        backdropFiles().forEach { it.delete() }
        tmp.renameTo(File(ctx.filesDir, SettingsActivity.BACKDROP_FILE))
        prefs.settingsBg = true
        return json(state().put("ok", true))
    }

    private fun backdropFiles() = listOfNotNull(File(ctx.filesDir, SettingsActivity.BACKDROP_FILE),
        ctx.getExternalFilesDir(null)?.let { File(it, SettingsActivity.BACKDROP_FILE) })

    private fun iconFiles() = listOfNotNull(File(ctx.filesDir, SettingsActivity.ICON_FILE),
        ctx.getExternalFilesDir(null)?.let { File(it, SettingsActivity.ICON_FILE) })

    private fun introFiles() = listOfNotNull(File(ctx.filesDir, Intro.FILE), ctx.getExternalFilesDir(null)?.let { File(it, Intro.FILE) })

    private fun form(session: IHTTPSession): Map<String, String> {
        session.parseBody(HashMap())
        return session.parameters.mapValues { it.value.firstOrNull().orEmpty() }
    }

    private fun json(o: JSONObject) = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", o.toString())

    private fun text(status: Response.Status, msg: String) = newFixedLengthResponse(status, "text/plain; charset=utf-8", msg)

    companion object {
        const val PORT = 8790
        private const val MAX_UPLOAD = 40L * 1024 * 1024
        private const val MAX_INTRO = 100L * 1024 * 1024

        @Volatile
        private var running: PhoneServer? = null

        /** Starts the server if needed and returns the address to put in the QR code, or null offline. */
        @Synchronized
        fun start(ctx: Context): String? {
            val ip = lanAddress(ctx) ?: return null
            val server = running ?: PhoneServer(ctx.applicationContext, newToken()).also {
                runCatching { it.start(SOCKET_READ_TIMEOUT, true) }.onFailure { return null }
                running = it
            }
            return "http://$ip:$PORT/${server.token}/"
        }

        @Synchronized
        fun stop() {
            running?.stop()
            running = null
        }

        private fun newToken(): String {
            val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
            val rnd = SecureRandom()
            return (1..8).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        }

        private fun lanAddress(ctx: Context): String? {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
            val props = cm.getLinkProperties(cm.activeNetwork ?: return null) ?: return null
            return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }

        /** A QR code, dark modules on white, each module [scale] pixels square. */
        fun qr(text: String, scale: Int = 10): Bitmap {
            val m = QRCodeWriter().encode(
                text, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
            val n = m.width
            val px = IntArray(n * scale * n * scale)
            for (y in 0 until n * scale) for (x in 0 until n * scale)
                px[y * n * scale + x] = if (m[x / scale, y / scale]) Color.BLACK else Color.WHITE
            return Bitmap.createBitmap(px, n * scale, n * scale, Bitmap.Config.ARGB_8888)
        }
    }
}
