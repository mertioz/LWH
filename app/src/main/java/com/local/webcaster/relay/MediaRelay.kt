package com.local.webcaster.relay

import com.local.webcaster.detection.MediaCandidate
import java.net.URI

interface MediaRelay {
    fun start(candidate: MediaCandidate): Result<String>
    fun createRelayUrl(candidate: MediaCandidate): Result<String>
    fun stop()
    val isRunning: Boolean
    val activeCandidate: MediaCandidate?
    val lastStatusCode: Int? get() = null
    fun hasNetworkChanged(): Boolean
    fun setSelectedLocalMedia(uris: Set<String>) = Unit
}

data class HeaderContext(
    val userAgent: String? = null,
    val referer: String? = null,
    val origin: String? = null,
    val cookie: String? = null,
    val authorization: String? = null,
    val accept: String? = null,
    val acceptLanguage: String? = null,
) {
    fun asMap(): Map<String, String> = buildMap {
        userAgent?.let { put("User-Agent", it) }
        referer?.let { put("Referer", it) }
        origin?.let { put("Origin", it) }
        cookie?.let { put("Cookie", it) }
        authorization?.let { put("Authorization", it) }
        accept?.let { put("Accept", it) }
        acceptLanguage?.let { put("Accept-Language", it) }
    }

    /**
     * Browser credentials are origin-bound. A redirected or manifest child request may use
     * cookies for its own URL, but Authorization and the parent's Cookie must never be copied
     * to a different origin.
     */
    fun forUrl(fromUrl: String, toUrl: String, cookieForUrl: (String) -> String?): HeaderContext {
        val sameOrigin = runCatching { sameOrigin(URI(fromUrl), URI(toUrl)) }.getOrDefault(false)
        val destinationCookie = cookieForUrl(toUrl)?.takeIf(String::isNotBlank)
        return copy(
            cookie = if (sameOrigin) destinationCookie ?: cookie else destinationCookie,
            authorization = authorization.takeIf { sameOrigin },
        )
    }

    companion object {
        fun from(candidate: MediaCandidate) = HeaderContext(
            candidate.requiredHeaders.value("User-Agent"),
            candidate.requiredHeaders.value("Referer"),
            candidate.requiredHeaders.value("Origin"),
            candidate.requiredHeaders.value("Cookie"),
            candidate.requiredHeaders.value("Authorization"),
            candidate.requiredHeaders.value("Accept"),
            candidate.requiredHeaders.value("Accept-Language"),
        )

        private fun Map<String, String>.value(name: String): String? =
            entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.takeIf(String::isNotBlank)

        private fun sameOrigin(first: URI, second: URI): Boolean =
            first.scheme.equals(second.scheme, true) && first.host.equals(second.host, true) &&
                effectivePort(first) == effectivePort(second)

        private fun effectivePort(uri: URI): Int = when {
            uri.port >= 0 -> uri.port
            uri.scheme.equals("https", true) -> 443
            else -> 80
        }
    }
}

data class RelaySession(val token: String, val port: Int, val hostAddress: String)
