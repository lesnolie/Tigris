package com.leslie.tvhome

import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Exercise NanoHTTPD's raw request stream, not a mock HTTP implementation. */
class VideoUploadHttpTest {
    @Test fun streamsVideoBodyAndServesTheFollowingRequest() {
        val body = ByteArray(256 * 1024 + 17) { (it % 251).toByte() }
        var received = ByteArray(0)
        val server = object : NanoHTTPD("127.0.0.1", 0) {
            override fun serve(session: IHTTPSession): Response {
                if (session.method == Method.POST) {
                    val output = ByteArrayOutputStream()
                    MediaFiles.copy(session.inputStream, output,
                        session.headers.getValue("content-length").toLong(), MediaFiles.MAX_VIDEO)
                    received = output.toByteArray()
                }
                return newFixedLengthResponse("ok")
            }
        }
        server.start(5000, true)
        try {
            val url = URL("http://127.0.0.1:${server.listeningPort}/video")
            val upload = url.openConnection() as HttpURLConnection
            upload.connectTimeout = 5000
            upload.readTimeout = 5000
            upload.requestMethod = "POST"
            upload.doOutput = true
            upload.setFixedLengthStreamingMode(body.size)
            upload.setRequestProperty("Content-Type", "video/mp4")
            upload.outputStream.use { it.write(body) }
            assertEquals("ok", upload.inputStream.bufferedReader().use { it.readText() })
            assertArrayEquals(body, received)
            upload.disconnect()
            val next = url.openConnection() as HttpURLConnection
            next.readTimeout = 5000
            assertEquals("ok", next.inputStream.bufferedReader().use { it.readText() })
            next.disconnect()
        } finally { server.stop() }
    }
}
