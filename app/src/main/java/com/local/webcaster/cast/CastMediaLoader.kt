package com.local.webcaster.cast

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.local.webcaster.detection.MediaCandidate
import com.local.webcaster.detection.MediaType

object CastMediaLoader {
    fun request(candidate: MediaCandidate, overrideUrl: String? = null, startPositionMs: Long = 0): MediaLoadRequestData {
        val metadata = MediaMetadata(
            if (candidate.mediaType == MediaType.AUDIO) MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
            else MediaMetadata.MEDIA_TYPE_MOVIE
        ).apply {
            putString(MediaMetadata.KEY_TITLE, candidate.title ?: candidate.host.ifBlank { "Média Web" })
        }
        val info = MediaInfo.Builder(overrideUrl ?: candidate.resolvedUrl)
            .setContentType(contentType(candidate))
            .setStreamType(if (candidate.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .build()
        return MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .apply {
                if (!candidate.isLive && startPositionMs > 0) setCurrentTime(startPositionMs)
            }
            .build()
    }

    fun contentType(candidate: MediaCandidate): String {
        // CAF receivers are less tolerant of server aliases such as audio/mpegurl. Always send
        // the canonical adaptive-streaming types, regardless of the WebView response spelling.
        if (candidate.mediaType == MediaType.HLS) return "application/x-mpegURL"
        if (candidate.mediaType == MediaType.DASH) return "application/dash+xml"
        val observed = candidate.mimeType?.substringBefore(';')?.trim()
        if (!observed.isNullOrBlank() && (observed.startsWith("video/") || observed.startsWith("audio/") ||
                observed.contains("mpegurl", true) || observed == "application/dash+xml")) return observed
        return when (candidate.mediaType) {
            MediaType.HLS -> "application/x-mpegURL"
            MediaType.DASH -> "application/dash+xml"
            MediaType.MP4 -> "video/mp4"
            MediaType.WEBM -> "video/webm"
            MediaType.AUDIO -> "audio/mpeg"
            else -> "video/mp4"
        }
    }
}
