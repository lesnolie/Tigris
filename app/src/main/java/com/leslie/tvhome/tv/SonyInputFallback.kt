package com.leslie.tvhome.tv

/** Match physical HDMI numbers, never list ordering, user labels or Sony's vendor hardware IDs. */
internal object SonyInputFallback {
    fun find(sonyUri: String, inputs: List<Input>): Input? {
        if (!sonyUri.startsWith("extInput:hdmi?")) return null
        val ports = sonyUri.substringAfter('?').split('&').filter { it.substringBefore('=') == "port" }
        val port = ports.singleOrNull()?.substringAfter('=', "")?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return inputs.filter { input ->
            input.uri.startsWith("content://android.media.tv/passthrough/") &&
                Regex("(?i)^HDMI[\\s_-]*([1-9][0-9]*)(?:\\b|$)").find(input.title.trim())
                    ?.groupValues?.get(1)?.toIntOrNull() == port
        }.singleOrNull()
    }
}
