package com.local.webcaster.detection

enum class MediaType { HLS, DASH, MP4, WEBM, VIDEO, AUDIO, IMAGE, BLOB, UNKNOWN }

enum class SourceType {
    DOM, VIDEO_CURRENT_SRC, SOURCE_ELEMENT, NETWORK, FETCH, XHR, PERFORMANCE, HLS_VARIANT, ENCRYPTED_MEDIA,
    LOCAL_PICKER,
}

data class SubtitleTrack(
    val url: String,
    val label: String,
    val language: String? = null,
    val mimeType: String = "text/vtt",
    val isDefault: Boolean = false,
)

data class MediaCandidate(
    val id: String,
    val url: String,
    val resolvedUrl: String = url,
    val pageUrl: String,
    val title: String? = null,
    val mimeType: String? = null,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val sourceType: SourceType,
    val host: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val bandwidth: Long? = null,
    val codecs: String? = null,
    val isMasterPlaylist: Boolean = false,
    val isLive: Boolean = false,
    val isDrm: Boolean = false,
    val unavailableReason: String? = null,
    val relayRequired: Boolean = false,
    val confidence: Int = 0,
    val discoveredAt: Long = System.currentTimeMillis(),
    val requiredHeaders: Map<String, String> = emptyMap(),
    val discoverySources: Set<SourceType> = setOf(sourceType),
    val posterUrl: String? = null,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val lastHttpStatus: Int? = null,
    val durationMs: Long? = null,
)

data class MediaObservation(
    val url: String,
    val pageUrl: String,
    val sourceType: SourceType,
    val title: String? = null,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bandwidth: Long? = null,
    val codecs: String? = null,
    val isMasterPlaylist: Boolean = false,
    val isLive: Boolean = false,
    val isDrm: Boolean = false,
    val unavailableReason: String? = null,
    val relayRequired: Boolean = false,
    val requiredHeaders: Map<String, String> = emptyMap(),
    val documentStartedAt: Long? = null,
    val posterUrl: String? = null,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val durationMs: Long? = null,
)
