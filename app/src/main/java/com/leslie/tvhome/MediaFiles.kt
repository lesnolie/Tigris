package com.leslie.tvhome

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Bounded, disk-streamed transfers. A failed upload never replaces the last usable file. */
internal object MediaFiles {
    const val MAX_VIDEO = 1024L * 1024 * 1024
    fun copy(input: InputStream, output: java.io.OutputStream, length: Long, max: Long) {
        require(length in 1..max) { "Invalid file size" }
        val buffer = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(n > 0) { "Incomplete transfer" }
            output.write(buffer, 0, n)
            remaining -= n
        }
    }

    fun isVideo(file: File): Boolean = runCatching {
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(file.path)
            reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        } finally { reader.release() }
    }.getOrDefault(false)

    fun replace(temp: File, target: File) {
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    const val WALL_FILE = "wallpaper-video.mp4"
    fun wallpaper(ctx: Context): File? = File(ctx.filesDir, WALL_FILE).takeIf { it.isFile && it.length() > 0 }

    @Synchronized
    fun uploadWallpaper(ctx: Context, input: InputStream, length: Long) {
        require(length in 1..MAX_VIDEO) { "Video must be at most 1 GB" }
        check(ctx.filesDir.usableSpace > length + 32L * 1024 * 1024) { "Not enough TV storage" }
        val temp = File.createTempFile("wallpaper-", ".part", ctx.filesDir)
        try {
            temp.outputStream().use { copy(input, it, length, MAX_VIDEO) }
            check(isVideo(temp)) { "Not a readable video" }
            replace(temp, File(ctx.filesDir, WALL_FILE))
            Prefs(ctx).background = VideoWallpaper.LOCAL
            Prefs(ctx).wallpaperVideoChanged()
        } finally { temp.delete() }
    }
}
