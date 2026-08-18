package com.local.webcaster.security

import com.local.webcaster.detection.MediaObservation
import com.local.webcaster.detection.SourceType
import com.local.webcaster.detection.SubtitleTrack
import org.json.JSONObject

object MessageValidator {
    const val MAX_MESSAGE_LENGTH = 16_384

    fun parse(message: String, pageUrl: String): MediaObservation? {
        if (message.isBlank() || message.length > MAX_MESSAGE_LENGTH) return null
        return runCatching {
            val json = JSONObject(message)
            if (json.optString("type") != "mediaCandidate") return null
            val url = json.optString("url")
            val blob = UrlValidator.isBlobMediaUrl(url)
            if (!blob && !UrlValidator.isValidMediaUrl(url)) return null
            val allowedKeys = setOf(
                "type", "url", "source", "mime", "title", "width", "height", "drm", "documentStartedAt",
                "poster", "tracks", "durationMs",
            )
            if (json.keys().asSequence().any { it !in allowedKeys }) return null
            MediaObservation(
                url = url,
                pageUrl = pageUrl,
                sourceType = source(json.optString("source")),
                title = json.optString("title").takeIf { it.isNotBlank() }?.take(500),
                mimeType = json.optString("mime").takeIf { it.isNotBlank() }?.take(200),
                width = json.optInt("width", 0).takeIf { it in 1..16_384 },
                height = json.optInt("height", 0).takeIf { it in 1..16_384 },
                isDrm = json.optBoolean("drm", false),
                documentStartedAt = json.optLong("documentStartedAt", 0L).takeIf { it > 0L },
                posterUrl = json.optString("poster").takeIf(UrlValidator::isValidMediaUrl),
                subtitleTracks = buildList {
                    val tracks = json.optJSONArray("tracks")
                    for (index in 0 until minOf(tracks?.length() ?: 0, 32)) {
                        val track = tracks?.optJSONObject(index) ?: continue
                        val trackUrl = track.optString("url")
                        if (!UrlValidator.isValidMediaUrl(trackUrl)) continue
                        add(
                            SubtitleTrack(
                                url = UrlValidator.normalize(trackUrl) ?: continue,
                                label = track.optString("label").take(100).ifBlank { "Subtitles" },
                                language = track.optString("language").take(35).takeIf(String::isNotBlank),
                                mimeType = track.optString("mime").take(100).ifBlank { "text/vtt" },
                                isDefault = track.optBoolean("default"),
                            )
                        )
                    }
                },
                durationMs = json.optLong("durationMs", 0L).takeIf { it in 1..86_400_000L },
                unavailableReason = if (blob) {
                    "Cette video utilise blob:/MediaSource. Le flux reseau reel doit etre detecte avant de pouvoir la caster."
                } else null,
            )
        }.getOrNull()
    }

    private fun source(raw: String): SourceType = when (raw.uppercase()) {
        "VIDEO_CURRENT_SRC" -> SourceType.VIDEO_CURRENT_SRC
        "SOURCE_ELEMENT" -> SourceType.SOURCE_ELEMENT
        "FETCH" -> SourceType.FETCH
        "XHR" -> SourceType.XHR
        "PERFORMANCE" -> SourceType.PERFORMANCE
        "ENCRYPTED_MEDIA" -> SourceType.ENCRYPTED_MEDIA
        else -> SourceType.DOM
    }
}
