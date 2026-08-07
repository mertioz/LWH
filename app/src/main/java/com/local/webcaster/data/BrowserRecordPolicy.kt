package com.local.webcaster.data

import java.net.URI

internal object BrowserRecordPolicy {
    private const val MAX_URL_LENGTH = 4_096
    private const val MAX_TITLE_LENGTH = 200

    fun normalizeUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_URL_LENGTH) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return trimmed
    }

    fun domain(url: String): String = runCatching {
        URI(url).host.orEmpty().lowercase().removePrefix("www.")
    }.getOrDefault("")

    fun title(value: String?, domain: String): String = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.startsWith("http://") && !it.startsWith("https://") }
        ?.take(MAX_TITLE_LENGTH)
        ?: domain.ifBlank { "Website" }
}
