package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class IdleTimerTransactionTest {
    private class Fake : IdleTimerTransaction.Journal {
        val values = mutableMapOf<String, String>()
        val saved = mutableMapOf<String, IdleTimerTransaction.Saved>()
        var denied: String? = null
        var ignore: String? = null
        var saveAllowed = true
        val transaction = IdleTimerTransaction({ values[it] }, { key, value ->
            if (key == denied) false else {
                if (key != ignore) { if (value == null) values.remove(key) else values[key] = value }
                true
            }
        }, this)
        override fun entries() = saved.toMap()
        override fun save(key: String, value: IdleTimerTransaction.Saved): Boolean {
            if (!saveAllowed) return false
            saved[key] = value
            return true
        }
        override fun forget(key: String): Boolean { saved.remove(key); return true }
    }
    @Test fun changingFrameTimeKeepsTheFirstSnapshotIncludingMissingKeys() {
        val f = Fake()
        f.values["screen"] = "600000"
        assertTrue(f.transaction.apply(linkedMapOf("screen" to "60000", "sleep" to "-1")))
        assertTrue(f.transaction.apply(linkedMapOf("screen" to "180000", "sleep" to "86400000")))
        assertTrue(f.transaction.restore())
        assertEquals(mapOf("screen" to "600000"), f.values)
        assertTrue(f.saved.isEmpty())
    }
    @Test fun deniedOrIgnoredVendorWriteRollsBackEarlierWrites() {
        for (ignored in listOf(false, true)) {
            val f = Fake()
            f.values.putAll(mapOf("screen" to "600000", "sleep" to "86400000"))
            if (ignored) f.ignore = "sleep" else f.denied = "sleep"
            assertFalse(f.transaction.apply(linkedMapOf("screen" to "60000", "sleep" to "-1")))
            assertEquals("600000", f.values["screen"])
            assertEquals("86400000", f.values["sleep"])
            assertTrue(f.saved.isEmpty())
        }
    }
    @Test fun restorationPreservesLaterSystemSettingsEdits() {
        val f = Fake()
        f.values["screen"] = "600000"
        f.transaction.apply(mapOf("screen" to "60000"))
        f.values["screen"] = "120000"
        assertTrue(f.transaction.restore())
        assertEquals("120000", f.values["screen"])
    }
    @Test fun failedSnapshotNeverChangesTheSystemAndFailedRestoreKeepsRecoveryData() {
        val f = Fake()
        f.values["screen"] = "600000"
        f.saveAllowed = false
        assertFalse(f.transaction.apply(mapOf("screen" to "60000")))
        assertEquals("600000", f.values["screen"])
        f.saveAllowed = true
        f.transaction.apply(mapOf("screen" to "60000"))
        f.denied = "screen"
        assertFalse(f.transaction.restore())
        assertFalse(f.saved.isEmpty())
        f.denied = null
        assertTrue(f.transaction.restore())
        assertEquals("600000", f.values["screen"])
    }
}
