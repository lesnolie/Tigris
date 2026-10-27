package com.leslie.tvhome

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Explicit downloads only. Immutable source revision plus SHA-256 keep defaults reproducible. */
internal object OptionalAssets {
    data class Asset(val id: String, val path: String, val bytes: Long, val sha256: String)
    val intro = Asset("intro", "raw/default_intro.mp4", 686981,
        "b4765ca42e11a4270740b83d2317a502e535fc4b29658688de05df2a4ecbb8b3")
    val backdrop = Asset("backdrop", "drawable-nodpi/default_settings_bg.png", 1461292,
        "6c3045d02cc428f2823f43c9b6463d33a62489bc2597729b5a479b91a167832b")
    private const val BASE = "https://raw.githubusercontent.com/lesnolie/Tigris/2d124d9b6cc5f5efd79165f93ff5282c7a6f1bce/app/src/main/res/"
    private fun target(ctx: Context, asset: Asset) = File(ctx.filesDir, "default-${asset.id}")
    fun file(ctx: Context, asset: Asset) = target(ctx, asset).takeIf { it.isFile && it.length() == asset.bytes }

    @Synchronized
    fun download(ctx: Context, asset: Asset) {
        if (file(ctx, asset) != null) return
        val temp = File.createTempFile("default-", ".part", ctx.filesDir)
        val connection = URL(BASE + asset.path).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 20000
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            connection.inputStream.use { input -> temp.outputStream().use {
                MediaFiles.copy(input, it, asset.bytes, asset.bytes)
                check(input.read() == -1) { "Unexpected asset size" }
            } }
            val digest = MessageDigest.getInstance("SHA-256")
            temp.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == asset.sha256) { "Asset checksum mismatch" }
            MediaFiles.replace(temp, target(ctx, asset))
        } finally {
            connection.disconnect()
            temp.delete()
        }
    }

    @Synchronized
    fun delete(ctx: Context, asset: Asset) { check(!target(ctx, asset).exists() || target(ctx, asset).delete()) }
}
