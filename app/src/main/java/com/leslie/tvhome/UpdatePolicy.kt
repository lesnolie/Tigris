package com.leslie.tvhome

object UpdatePolicy {
    fun newer(remote: String, installed: String): Boolean {
        val a = numbers(remote) ?: return false
        val b = numbers(installed.substringBefore('-')) ?: return false
        return compare(a, b) > 0
    }

    /** Same numeric version, ignoring one leading v and one hyphen suffix. */
    fun sameNumeric(remote: String, installed: String): Boolean {
        val a = numbers(remote) ?: return false
        val b = numbers(installed.substringBefore('-')) ?: return false
        return compare(a, b) == 0
    }

    /**
     * Whether [remoteName] should be offered over what is installed.
     *
     * Version names break the tie only when the release APK's versionCode is not
     * known yet. A known versionCode that is lower than or equal to the installed
     * one is never an update, even if the name looks newer. When the names are
     * the same number, the installed versionCode is the tie-breaker: only a
     * strictly higher code is an update. A hyphen suffix such as `-test7679`
     * does not make the plain release newer.
     */
    fun offer(remoteName: String, remoteCode: Long?, installedName: String?, installedCode: Long?): Boolean {
        if (installedCode != null && remoteCode != null && remoteCode <= installedCode) return false
        if (installedName.isNullOrBlank()) return true
        if (newer(remoteName, installedName)) return true
        if (!sameNumeric(remoteName, installedName)) return false
        return installedCode != null && remoteCode != null && remoteCode > installedCode
    }

    private fun numbers(raw: String): List<Int>? {
        val matched = Regex("^v?(\\d+(?:\\.\\d+){1,3})$").matchEntire(raw.trim()) ?: return null
        val parts = ArrayList<Int>()
        for (piece in matched.groupValues[1].split('.')) {
            val value = piece.toIntOrNull() ?: return null
            parts.add(value)
        }
        return parts
    }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}
