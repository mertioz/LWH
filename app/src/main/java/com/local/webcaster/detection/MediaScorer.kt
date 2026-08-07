package com.local.webcaster.detection

object MediaScorer {
    fun score(
        type: MediaType,
        source: SourceType,
        mimeType: String? = null,
        isMaster: Boolean = false,
        isDrm: Boolean = false,
        sourceCount: Int = 1,
        url: String = "",
    ): Int {
        if (isDrm) return 1
        val base = when {
            type == MediaType.BLOB -> 10
            type == MediaType.HLS && isMaster -> 100
            type == MediaType.HLS && mimeType?.contains("mpegurl", ignoreCase = true) == true -> 98
            type == MediaType.HLS -> 95
            type == MediaType.DASH -> 96
            source == SourceType.VIDEO_CURRENT_SRC -> 94
            type == MediaType.MP4 && mimeType?.startsWith("video/mp4", ignoreCase = true) == true -> 92
            type == MediaType.MP4 -> 90
            type == MediaType.WEBM -> 85
            type == MediaType.VIDEO -> 80
            type == MediaType.AUDIO -> 65
            else -> 20
        }
        val lowerUrl = url.lowercase()
        val penalty = when {
            listOf("/preroll", "/pre-roll", "/video-ad", "/vast/", "adunit=", "ad_unit=")
                .any(lowerUrl::contains) -> 45
            listOf("/preview/", "/trailer/", "/teaser/", "/promo/").any(lowerUrl::contains) -> 15
            else -> 0
        }
        return (base + (sourceCount - 1).coerceAtMost(3) * 2 - penalty).coerceIn(1, 100)
    }
}
