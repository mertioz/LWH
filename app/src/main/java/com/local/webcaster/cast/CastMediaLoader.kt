package com.local.webcaster.cast

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.common.images.WebImage
import androidx.core.net.toUri
import com.local.webcaster.detection.MediaCandidate
import com.local.webcaster.detection.MediaType

object CastMediaLoader {
    fun request(candidate: MediaCandidate, overrideUrl: String? = null, startPositionMs: Long = 0): MediaLoadRequestData {
        val metadata = MediaMetadata(
            if (candidate.mediaType == MediaType.AUDIO) MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
            else MediaMetadata.MEDIA_TYPE_MOVIE
        ).apply {
            putString(MediaMetadata.KEY_TITLE, candidate.title ?: candidate.host.ifBlank { "Média Web" })
            candidate.host.takeIf(String::isNotBlank)?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            candidate.posterUrl?.let { poster ->
                runCatching { addImage(WebImage(poster.toUri())) }
            }
        }
        val info = mediaInfo(candidate, overrideUrl, metadata)
        return MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .apply {
                if (!candidate.isLive && startPositionMs > 0) setCurrentTime(startPositionMs)
                val defaults = candidate.subtitleTracks.mapIndexedNotNull { index, track ->
                    (index + 1).toLong().takeIf { track.isDefault }
                }.toLongArray()
                if (defaults.isNotEmpty()) setActiveTrackIds(defaults)
            }
            .build()
    }

    fun mediaInfo(
        candidate: MediaCandidate,
        overrideUrl: String? = null,
        metadataOverride: MediaMetadata? = null,
    ): MediaInfo {
        val metadata = metadataOverride ?: MediaMetadata(
            if (candidate.mediaType == MediaType.AUDIO) MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
            else MediaMetadata.MEDIA_TYPE_MOVIE
        ).apply {
            putString(MediaMetadata.KEY_TITLE, candidate.title ?: candidate.host.ifBlank { "Média Web" })
            candidate.host.takeIf(String::isNotBlank)?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            candidate.posterUrl?.let { runCatching { addImage(WebImage(it.toUri())) } }
        }
        return MediaInfo.Builder(overrideUrl ?: candidate.resolvedUrl)
            .setContentType(contentType(candidate))
            .setStreamType(if (candidate.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .apply {
                val tracks = candidate.subtitleTracks.mapIndexed { index, track ->
                    MediaTrack.Builder((index + 1).toLong(), MediaTrack.TYPE_TEXT)
                        .setContentId(track.url)
                        .setContentType(track.mimeType)
                        .setName(track.label)
                        .apply { track.language?.let(::setLanguage) }
                        .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                        .build()
                }
                if (tracks.isNotEmpty()) setMediaTracks(tracks)
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
