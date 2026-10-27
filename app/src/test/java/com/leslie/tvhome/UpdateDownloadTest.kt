package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class UpdateDownloadTest {
    @Test fun reportsProgressAndPreservesTheEntireApk() {
        val bytes = ByteArray(2163917) { (it % 251).toByte() }
        val result = ByteArrayOutputStream()
        val progress = mutableListOf<Int>()
        UpdateDownload.copy(ByteArrayInputStream(bytes), result, bytes.size.toLong(), progress::add)
        assertArrayEquals(bytes, result.toByteArray())
        assertEquals(0, progress.first())
        assertEquals(100, progress.last())
        assertTrue(progress.size > 2)
        assertTrue(progress.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun interruptedDownloadCannotAppearComplete() {
        val progress = mutableListOf<Int>()
        assertThrows(IllegalStateException::class.java) {
            UpdateDownload.copy(ByteArrayInputStream(ByteArray(123)), ByteArrayOutputStream(), 1000, progress::add)
        }
        assertFalse(100 in progress)
    }

    @Test fun excessBytesAreNotWrittenAndNeverReportCompletion() {
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Int>()
        assertThrows(IllegalStateException::class.java) {
            UpdateDownload.copy(ByteArrayInputStream(ByteArray(65537)), output, 65536, progress::add)
        }
        assertEquals(65536, output.size())
        assertFalse(100 in progress)
    }
}
