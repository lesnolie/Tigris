package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class MediaFilesTest {
    @Test fun copiesOnlyDeclaredBodyAndPreservesFollowingRequestBytes() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 9))
        val output = ByteArrayOutputStream()
        MediaFiles.copy(input, output, 3, 100)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
        assertEquals(9, input.read())
    }

    @Test fun rejectsTruncatedOrOversizedTransfers() {
        assertThrows(IllegalStateException::class.java) {
            MediaFiles.copy(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), 3, 100)
        }
        for (length in listOf(-1L, 0L, 101L)) assertThrows(IllegalArgumentException::class.java) {
            MediaFiles.copy(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), length, 100)
        }
    }

    @Test fun failedTransferKeepsPreviousVideoAndSuccessfulTransferReplacesIt() {
        val directory = Files.createTempDirectory("tigris-video-test").toFile()
        val old = directory.resolve("video.mp4").apply { writeText("previous") }
        val pending = directory.resolve("pending")
        try {
            assertThrows(IllegalStateException::class.java) {
                pending.outputStream().use { MediaFiles.copy(ByteArrayInputStream(byteArrayOf(1)), it, 5, 100) }
            }
            assertEquals("previous", old.readText())
            pending.writeText("new video")
            MediaFiles.replace(pending, old)
            assertEquals("new video", old.readText())
            assertFalse(pending.exists())
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }
}
