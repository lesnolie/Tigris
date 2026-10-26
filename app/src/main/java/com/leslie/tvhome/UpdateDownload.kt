package com.leslie.tvhome

import java.io.InputStream
import java.io.OutputStream

/** Fixed-length download with bounded writes and distinct percentage notifications. */
internal object UpdateDownload {
    fun copy(input: InputStream, output: OutputStream, size: Long, onProgress: (Int) -> Unit) {
        require(size in 1..250_000_000L)
        val buffer = ByteArray(65536)
        var total = 0L
        var previous = 0
        onProgress(0)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            check(total + count <= size) { "Download is larger than release metadata" }
            output.write(buffer, 0, count)
            total += count
            val percent = (total * 100 / size).toInt().coerceAtMost(99)
            if (percent != previous) { previous = percent; onProgress(percent) }
        }
        check(total == size) { "Download incomplete" }
        // Do not report 100% until EOF confirms that no excess bytes follow.
        onProgress(100)
    }
}
