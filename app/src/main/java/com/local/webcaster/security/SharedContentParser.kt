package com.local.webcaster.security

object SharedContentParser {
    private val urlPattern = Regex("https?://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE)

    fun extractUrl(text: CharSequence?): String? {
        val value = text?.toString()?.trim()?.take(UrlValidator.MAX_URL_LENGTH) ?: return null
        val direct = UrlValidator.normalize(value)
        if (direct != null) return direct
        return urlPattern.findAll(value)
            .map { it.value.trimEnd('.', ',', ';', ':', ')', ']', '}') }
            .mapNotNull(UrlValidator::normalize)
            .firstOrNull()
    }
}
