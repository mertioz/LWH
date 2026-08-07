package com.local.webcaster.security

import java.net.IDN
import java.net.URI
import java.util.Locale

object UrlValidator {
    const val MAX_URL_LENGTH = 8_192

    fun isValidMediaUrl(value: String): Boolean {
        if (value.isBlank() || value.length > MAX_URL_LENGTH) return false
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase(Locale.US)
            val host = uri.host
            scheme in setOf("http", "https") && !host.isNullOrBlank() &&
                IDN.toASCII(host).length in 1..253 && uri.userInfo == null
        }.getOrDefault(false)
    }

    fun isBlobMediaUrl(value: String): Boolean {
        if (value.isBlank() || value.length > MAX_URL_LENGTH) return false
        return value.startsWith("blob:http://", ignoreCase = true) ||
            value.startsWith("blob:https://", ignoreCase = true)
    }

    fun normalize(value: String): String? = if (!isValidMediaUrl(value)) null else runCatching {
        val uri = URI(value)
        buildString {
            append(uri.scheme.lowercase(Locale.US)).append("://").append(uri.rawAuthority)
            append(uri.rawPath.ifEmpty { "/" })
            uri.rawQuery?.let { append('?').append(it) }
        }
    }.getOrNull()
}
