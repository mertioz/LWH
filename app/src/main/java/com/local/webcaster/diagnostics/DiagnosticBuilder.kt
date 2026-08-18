package com.local.webcaster.diagnostics

import com.local.webcaster.cast.CastUiState
import com.local.webcaster.detection.MediaCandidate
import java.net.URI

object DiagnosticBuilder {
    fun build(candidate: MediaCandidate, cast: CastUiState? = null): String = buildString {
        appendLine("CASTER diagnostic")
        appendLine("media=${candidate.mediaType}")
        appendLine("mime=${candidate.mimeType.orEmpty().take(120)}")
        appendLine("url=${sanitizedUrl(candidate.resolvedUrl)}")
        appendLine("page=${sanitizedUrl(candidate.pageUrl)}")
        appendLine("resolution=${candidate.width ?: 0}x${candidate.height ?: 0}")
        appendLine("bitrate=${candidate.bandwidth ?: 0}")
        appendLine("duration_ms=${candidate.durationMs ?: cast?.durationMs ?: "unknown"}")
        appendLine("stream=${if (candidate.isLive) "live" else "vod"}")
        appendLine("manifest=${if (candidate.isMasterPlaylist) "master" else "media_or_direct"}")
        appendLine("detector=${candidate.discoverySources.sortedBy(Enum<*>::name).joinToString()}")
        appendLine("drm=${candidate.isDrm}")
        appendLine("relay_required=${candidate.relayRequired}")
        appendLine("request_context=${candidate.requiredHeaders.isNotEmpty()}")
        appendLine("http_status=${cast?.lastHttpStatus ?: candidate.lastHttpStatus ?: "unknown"}")
        appendLine("subtitles=${candidate.subtitleTracks.size}")
        if (cast != null) {
            appendLine("cast_connected=${cast.connected}")
            appendLine("receiver_state=${cast.receiverState}")
            appendLine("delivery=${cast.deliveryMode}")
            appendLine("relay_state=${cast.relayState}")
            appendLine("fallback_reason=${cast.fallbackReason.orEmpty().take(200)}")
            appendLine("startup_ms=${cast.startupTimeMs ?: "unknown"}")
        }
    }.trim()

    internal fun sanitizedUrl(value: String): String = runCatching {
        val uri = URI(value)
        val port = uri.port.takeIf { it >= 0 }?.let { ":$it" }.orEmpty()
        val extension = uri.path.orEmpty().substringAfterLast('/', "")
            .substringAfterLast('.', "")
            .takeIf { it.length in 1..8 && it.all(Char::isLetterOrDigit) }
            ?.let { ".$it" }
            .orEmpty()
        "${uri.scheme}://${uri.host}$port/...$extension"
    }.getOrDefault("<invalid>").take(2_048)
}
