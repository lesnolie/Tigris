package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InstallConfirmationTest {
    private val path = "/storage/emulated/0/Android/data/com.leslie.tvhome/files/Download/Tigris/com.leslie.tvhome-v2.0.18.apk"

    @Test fun probesCoverTheInstallConfirmationIntents() {
        assertTrue(InstallConfirmation.probes.any {
            it.action == "android.intent.action.INSTALL_PACKAGE" && it.mime == InstallConfirmation.ARCHIVE_MIME
        })
        assertTrue(InstallConfirmation.probes.any {
            it.action == "android.intent.action.VIEW" && it.mime == InstallConfirmation.ARCHIVE_MIME
        })
        assertTrue(InstallConfirmation.probes.any { it.action == InstallConfirmation.ACTION_CONFIRM_INSTALL })
        assertEquals("application/vnd.android.package-archive", InstallConfirmation.ARCHIVE_MIME)
    }

    @Test fun sessionStaysOnTheCurrentPathWhenAnyConfirmationUiResolves() {
        assertFalse(InstallConfirmation.sessionAllowed(false))
        assertTrue(InstallConfirmation.sessionAllowed(true))
        assertFalse(InstallConfirmation.sessionAllowed(listOf(false, false, false).any { it }))
        assertTrue(InstallConfirmation.sessionAllowed(listOf(false, true, false).any { it }))
    }

    @Test fun pendingUserActionWithoutAStartableIntentIsTheSameMissingPage() {
        assertTrue(InstallConfirmation.pendingActionUnusable(intentPresent = false, intentStartable = false))
        assertTrue(InstallConfirmation.pendingActionUnusable(intentPresent = false, intentStartable = true))
        assertTrue(InstallConfirmation.pendingActionUnusable(intentPresent = true, intentStartable = false))
        assertFalse(InstallConfirmation.pendingActionUnusable(intentPresent = true, intentStartable = true))
    }

    @Test fun savedApkNameStaysInsideTheTigrisDownloadDirectory() {
        assertEquals("com.leslie.tvhome-v2.0.18.apk", InstallConfirmation.fileName("com.leslie.tvhome", "v2.0.18"))
        assertEquals("Tigris/com.leslie.tvhome-v2.0.18.apk", InstallConfirmation.relativePath("com.leslie.tvhome", "v2.0.18"))
        val hostile = InstallConfirmation.fileName("../evil", "../../2.0")
        assertFalse(hostile.contains('/'))
        assertFalse(hostile.contains('\\'))
        assertTrue(hostile.endsWith(".apk"))
        assertEquals("update", InstallConfirmation.safeToken(".."))
        assertEquals("update", InstallConfirmation.safeToken("   "))
    }

    @Test fun olderCopiesOfTheSamePackageAreReplaced() {
        val keep = InstallConfirmation.fileName("com.leslie.tvhome", "v2.0.18")
        assertTrue(InstallConfirmation.previousApk("com.leslie.tvhome-v2.0.17.apk", "com.leslie.tvhome", keep))
        assertFalse(InstallConfirmation.previousApk(keep, "com.leslie.tvhome", keep))
        assertFalse(InstallConfirmation.previousApk("com.hgtv.star-1.2.3.apk", "com.leslie.tvhome", keep))
        assertFalse(InstallConfirmation.previousApk("com.leslie.tvhome-v2.0.17.txt", "com.leslie.tvhome", keep))
    }

    @Test fun adbCommandUsesTheExactSavedPath() {
        assertEquals("adb install -r $path", InstallConfirmation.adbCommand(path))
        assertEquals("adb install -r \"/sdcard/My TV/app.apk\"", InstallConfirmation.adbCommand("/sdcard/My TV/app.apk"))
    }

    @Test fun bothLocalesTellTheUserToSideloadTheExactPath() {
        val command = InstallConfirmation.adbCommand(path)
        for (folder in listOf("values", "values-zh")) {
            val xml = read("src/main/res/$folder/updater_progress.xml")
            val body = stringBody(xml, "update_no_install_ui")
            val shown = body.format(path, command)
            assertTrue(shown.contains("adb install -r $path"))
            assertTrue(shown.contains(path))
            assertTrue(stringBody(xml, "update_no_install_ui_missing_copy").contains("adb install -r"))
            assertTrue(stringBody(xml, "update_no_install_ui_saving").isNotBlank())
        }
    }

    @Test fun sideloadFailureKeepsItsInstruction() {
        val error = SideloadRequired("adb install -r $path")
        assertEquals("adb install -r $path", error.message)
    }

    private fun stringBody(xml: String, name: String): String {
        val match = Regex("""<string name="$name"[^>]*>(.*?)</string>""").find(xml)
        return match?.groupValues?.get(1) ?: error("Missing string $name")
    }

    private fun read(path: String): String {
        val file = listOf(File(path), File("app/$path")).firstOrNull { it.isFile } ?: error("Missing $path from ${File(".").absolutePath}")
        return file.readText()
    }
}
