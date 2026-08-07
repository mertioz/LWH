package com.local.webcaster.relay

import java.net.URI

data class HlsRewriteResult(
    val text: String,
    val isDrm: Boolean,
    val isMaster: Boolean = false,
    val isLive: Boolean = false,
    val referenceCount: Int = 0,
)

class HlsRelayRewriter {
    private val uriAttribute = Regex("URI=\"([^\"]+)\"")

    fun rewrite(manifest: String, manifestUrl: String, relayUrlFor: (String) -> String): HlsRewriteResult {
        return rewriteTyped(manifest, manifestUrl) { url, _ -> relayUrlFor(url) }
    }

    fun rewriteTyped(manifest: String, manifestUrl: String, relayUrlFor: (String, Boolean) -> String): HlsRewriteResult {
        if (!manifest.removePrefix("\uFEFF").lineSequence().firstOrNull()?.trim().equals("#EXTM3U")) {
            throw IllegalArgumentException("Manifest HLS invalide")
        }
        var drm = false
        var nextUriIsPlaylist = false
        var isMaster = false
        var hasMediaSegments = false
        var hasEndList = false
        var referenceCount = 0
        val rewritten = manifest.lineSequence().map { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-KEY:") || line.startsWith("#EXT-X-SESSION-KEY:") -> {
                    val lower = line.lowercase()
                    if (!lower.contains("method=none")) drm = true
                    rawLine // Never rewrite encryption key URLs.
                }
                line.startsWith("#") && uriAttribute.containsMatchIn(line) -> {
                    val masterReference = line.startsWith("#EXT-X-MEDIA:", true) ||
                        line.startsWith("#EXT-X-I-FRAME-STREAM-INF:", true)
                    val uriIsPlaylist = masterReference ||
                        line.startsWith("#EXT-X-RENDITION-REPORT:", true)
                    if (masterReference) isMaster = true
                    uriAttribute.replace(rawLine) { match ->
                        referenceCount++
                        val upstream = resolve(manifestUrl, match.groupValues[1])
                        "URI=\"${relayUrlFor(upstream, uriIsPlaylist)}\""
                    }
                }
                line.startsWith("#EXT-X-STREAM-INF:", true) -> {
                    isMaster = true
                    nextUriIsPlaylist = true
                    rawLine
                }
                line.startsWith("#EXTINF:", true) || line.startsWith("#EXT-X-PART:", true) -> {
                    hasMediaSegments = true
                    rawLine
                }
                line.equals("#EXT-X-ENDLIST", true) -> {
                    hasEndList = true
                    rawLine
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    referenceCount++
                    val result = relayUrlFor(resolve(manifestUrl, line), nextUriIsPlaylist)
                    nextUriIsPlaylist = false
                    result
                }
                else -> rawLine
            }
        }.joinToString("\n")
        return HlsRewriteResult(
            text = rewritten,
            isDrm = drm,
            isMaster = isMaster,
            isLive = !isMaster && hasMediaSegments && !hasEndList,
            referenceCount = referenceCount,
        )
    }

    private fun resolve(base: String, child: String) = URI(base).resolve(child).toASCIIString()
}
