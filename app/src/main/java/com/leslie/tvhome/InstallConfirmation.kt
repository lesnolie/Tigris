package com.leslie.tvhome

/** The OS-issued confirmation Intent is authoritative; resolver probes are not. */
internal object InstallConfirmation {
    const val DIRECTORY = "Tigris"

    /** Only a missing OS-supplied confirmation Intent requires an immediate fallback. */
    fun missingSystemIntent(intentPresent: Boolean) = !intentPresent

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
        val filename = absolutePath.substringAfterLast('/')
        require(filename.matches(Regex("[A-Za-z0-9._-]+\\.apk")) && '"' !in absolutePath)
        return "adb pull \"$absolutePath\" \"$filename\" && adb install -r \"$filename\""
    }
}

/** Raised after the verified APK has been saved for sideload. The message is the UI text. */
internal class SideloadRequired(message: String) : IllegalStateException(message)
