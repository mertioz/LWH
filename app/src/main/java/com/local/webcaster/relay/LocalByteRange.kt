package com.local.webcaster.relay

data class LocalByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
}

object LocalByteRangeParser {
    fun parse(header: String?, size: Long): LocalByteRange? {
        if (header == null) return if (size > 0) LocalByteRange(0, size - 1) else null
        if (size <= 0 || !header.startsWith("bytes=") || ',' in header) return null
        val value = header.removePrefix("bytes=").trim()
        val separator = value.indexOf('-')
        if (separator < 0) return null
        val startText = value.substring(0, separator).trim()
        val endText = value.substring(separator + 1).trim()
        return when {
            startText.isEmpty() -> {
                val suffix = endText.toLongOrNull()?.takeIf { it > 0 } ?: return null
                val length = suffix.coerceAtMost(size)
                LocalByteRange(size - length, size - 1)
            }
            else -> {
                val start = startText.toLongOrNull()?.takeIf { it >= 0 && it < size } ?: return null
                val end = if (endText.isEmpty()) size - 1 else {
                    endText.toLongOrNull()?.coerceAtMost(size - 1) ?: return null
                }
                if (end < start) null else LocalByteRange(start, end)
            }
        }
    }
}
