package com.leslie.tvhome

import java.net.URLEncoder

/** Public search, no shared API secret. Keep query text encoded and server-controlled filters fixed. */
internal object WallhavenApi {
    fun category(value: String) = value.takeIf { it in setOf("100", "010", "001", "111") } ?: "100"
    fun sorting(value: String) = value.takeIf { it in setOf("toplist", "random", "date_added") } ?: "toplist"
    fun resolution(value: String) = value.takeIf { it in setOf("3840x2160", "1920x1080") } ?: "3840x2160"
    fun search(query: String, categories: String, sort: String, minimum: String): String =
        "https://wallhaven.cc/api/v1/search?q=${URLEncoder.encode(query.take(150), "UTF-8")}" +
            "&categories=${category(categories)}&purity=100&sorting=${sorting(sort)}" +
            "&topRange=1M&atleast=${resolution(minimum)}&ratios=16x9&page=1"
}
