package com.leslie.tvhome

/**
 * PackageInstaller on SDK < 31 always needs a system confirmation Activity.
 * Some Sony BRAVIA Android 9 firmwares ship `com.android.packageinstaller`
 * with no activities, so the session can never finish. Callers then keep the
 * verified APK and show `adb install -r` instead of committing that session.
 */
internal object InstallConfirmation {
    const val ARCHIVE_MIME = "application/vnd.android.package-archive"
    const val ACTION_CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL"

    /** Subdirectory of [android.content.Context.getExternalFilesDir] (DIRECTORY_DOWNLOADS). */
    const val DIRECTORY = "Tigris"

    data class Probe(
        val action: String,
        val mime: String? = null,
        val data: String? = null,
        val packageName: String? = null,
    )

    /**
     * Intents the system install confirmation page handles. Any resolved probe
     * means this firmware can show that page, so the session path stays unchanged.
     */
    val probes: List<Probe> = listOf(
        Probe("android.intent.action.INSTALL_PACKAGE", ARCHIVE_MIME, "content://com.leslie.tvhome.updates/package.apk"),
        Probe("android.intent.action.VIEW", ARCHIVE_MIME, "content://com.leslie.tvhome.updates/package.apk"),
        Probe("android.intent.action.INSTALL_PACKAGE", ARCHIVE_MIME, "file:///tmp/package.apk"),
        Probe("android.intent.action.VIEW", ARCHIVE_MIME, "file:///tmp/package.apk"),
        Probe("android.intent.action.INSTALL_PACKAGE", data = "package:com.leslie.tvhome"),
        Probe(ACTION_CONFIRM_INSTALL),
        Probe(ACTION_CONFIRM_INSTALL, packageName = "com.android.packageinstaller"),
    )

    fun sessionAllowed(anyProbeResolved: Boolean) = anyProbeResolved

    /**
     * STATUS_PENDING_USER_ACTION whose confirmation Intent is missing or cannot
     * be started (resolve failure or ActivityNotFoundException). Same outcome as
     * no confirmation page: do not keep the session.
     */
    fun pendingActionUnusable(intentPresent: Boolean, intentStartable: Boolean) =
        !intentPresent || !intentStartable

    fun safeToken(value: String): String {
        val cleaned = value.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_', '.')
        return if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") "update" else cleaned
    }

    fun fileName(packageName: String, version: String) =
        safeToken(packageName) + "-" + safeToken(version) + ".apk"

    fun relativePath(packageName: String, version: String) =
        "$DIRECTORY/" + fileName(packageName, version)

    fun previousApk(name: String, packageName: String, keepName: String): Boolean {
        val prefix = safeToken(packageName) + "-"
        return name != keepName && name.startsWith(prefix) && name.endsWith(".apk")
    }

    fun adbCommand(absolutePath: String): String {
        require(absolutePath.isNotBlank())
        val shown = if (absolutePath.any { it.isWhitespace() }) "\"$absolutePath\"" else absolutePath
        return "adb install -r $shown"
    }
}

/** Raised after the verified APK has been saved for sideload. The message is the UI text. */
internal class SideloadRequired(message: String) : IllegalStateException(message)
