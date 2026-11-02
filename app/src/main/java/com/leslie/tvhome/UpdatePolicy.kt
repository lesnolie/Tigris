package com.leslie.tvhome

object UpdatePolicy {
    fun newer(remote: String, installed: String): Boolean {
        fun parts(s: String): List<Int>? = Regex("^v?(\\d+(?:\\.\\d+){1,3})$").matchEntire(s.trim())
            ?.groupValues?.get(1)?.split('.')?.map { it.toIntOrNull() ?: return null }
        val a = parts(remote) ?: return false
        val b = parts(installed.substringBefore('-')) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (d != 0) return d > 0
        }
        return false
    }
}
