package com.leslie.tvhome

/**
 * A HOME alias launches its target class; both are evidence for the same package, never arbitrary windows.
 * Compatible with the Sony alias handling in PR #61 (issue #53).
 */
internal object StockHomeActivities {
    fun classes(pkg: String, alias: String?, target: String?): Set<String> =
        listOfNotNull(alias, target).filter { it.isNotBlank() }.map {
            when {
                it.startsWith('.') -> pkg + it
                '.' !in it -> "$pkg.$it"
                else -> it
            }
        }.toSet()
}
