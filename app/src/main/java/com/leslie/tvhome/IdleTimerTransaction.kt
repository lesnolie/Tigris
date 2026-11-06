package com.leslie.tvhome

/** The journal must durably save ownership before write() is called. */
internal class IdleTimerTransaction(
    private val read: (String) -> String?,
    private val write: (String, String?) -> Boolean,
    private val journal: Journal,
) {
    data class Saved(val original: String?, val applied: String)
    interface Journal {
        fun entries(): Map<String, Saved>
        fun save(key: String, value: Saved): Boolean
        fun forget(key: String): Boolean
    }
    fun restore(): Boolean {
        var ok = true
        for ((key, saved) in journal.entries()) {
            val current = read(key)
            if (current == saved.applied && (!write(key, saved.original) || read(key) != saved.original)) {
                ok = false
                continue
            }
            if (!journal.forget(key)) ok = false
        }
        return ok
    }
    fun apply(desired: Map<String, String>): Boolean {
        for ((key, value) in desired) {
            if (read(key) == value) continue
            val saved = journal.entries()[key]
            if (!journal.save(key, Saved(if (saved != null) saved.original else read(key), value)) ||
                !write(key, value) || read(key) != value) {
                restore()
                return false
            }
        }
        return true
    }
}
