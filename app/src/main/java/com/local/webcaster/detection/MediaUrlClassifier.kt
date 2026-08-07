package com.local.webcaster.detection

import java.net.URI
import java.util.Locale

object MediaUrlClassifier {
    private val manifestHints = listOf("manifest", "playlist", "master", "index.m3u", "/hls/", "/dash/")
    private val segmentExtensions = listOf(
        ".ts", ".m4s", ".cmfv", ".cmfa", ".aac", ".vtt", ".srt", ".webvtt", ".key"
    )
    private val nonMediaExtensions = listOf(
        ".json", ".webmanifest", ".js", ".css", ".png", ".jpg", ".jpeg", ".gif",
        ".webp", ".svg", ".ico", ".woff", ".woff2", ".ttf", ".map"
    )
    private val numberedSegment = Regex(
        "(?:^|/)(?:segment|seg|chunk|fragment|frag|part)[-_.]?\\d+(?:[-_.][^/]*)?(?:\\.[a-z0-9]+)?$",
        RegexOption.IGNORE_CASE,
    )
    private val initSegment = Regex(
        "(?:^|/)(?:init|initialization)(?:[-_.][^/]*)?\\.(?:mp4|m4v|webm)$",
        RegexOption.IGNORE_CASE,
    )
    private val segmentQuery = Regex(
        "(?:^|&)(?:range|sq|segment|seg|fragment|frag|chunk|part)=\\d+(?:[-%2d]\\d+)?(?:&|$)",
        RegexOption.IGNORE_CASE,
    )

    fun classify(url: String, mimeType: String? = null): MediaType {
        classifyMime(mimeType)?.let { return it }
        val parts = parts(url)
        val path = parts.first
        val query = parts.second
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") ||
                query.contains("mpegurl") || query.contains("m3u8") -> MediaType.HLS
            path.endsWith(".mpd") || query.contains("dash+xml") || query.contains("%2fmpd") -> MediaType.DASH
            path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mov") ||
                query.contains("video%2fmp4") || query.contains("video/mp4") || query.contains("format=mp4") -> MediaType.MP4
            path.endsWith(".webm") || query.contains("video%2fwebm") || query.contains("video/webm") -> MediaType.WEBM
            path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".ogg") || path.endsWith(".opus") -> MediaType.AUDIO
            else -> MediaType.UNKNOWN
        }
    }

    fun isPotentialMedia(url: String, mimeType: String? = null): Boolean {
        if (!isCastableScheme(url) || isSegment(url)) return false
        if (classify(url, mimeType) != MediaType.UNKNOWN) return true
        val (path, query) = parts(url)
        if (nonMediaExtensions.any(path::endsWith)) return false
        val searchable = "$path?$query"
        return manifestHints.any(searchable::contains) ||
            path.endsWith("/videoplayback") && (query.contains("mime=video") || query.contains("mime=audio"))
    }

    fun isMediaTransport(url: String, mimeType: String? = null): Boolean =
        isPotentialMedia(url, mimeType) || isSegment(url)

    fun isSegment(url: String): Boolean {
        val (path, query) = parts(url)
        return segmentExtensions.any(path::endsWith) || initSegment.containsMatchIn(path) ||
            numberedSegment.containsMatchIn(path) || segmentQuery.containsMatchIn(query)
    }

    fun isCastableScheme(url: String): Boolean = runCatching {
        URI(url).scheme?.lowercase(Locale.US) in setOf("http", "https")
    }.getOrDefault(false)

    private fun classifyMime(value: String?): MediaType? {
        val mime = value.orEmpty().substringBefore(';').trim().lowercase(Locale.US)
        return when {
            mime.isBlank() -> null
            mime.contains("mpegurl") || mime == "application/vnd.apple.mpegurl" -> MediaType.HLS
            mime == "application/dash+xml" -> MediaType.DASH
            mime == "video/mp4" || mime == "application/mp4" -> MediaType.MP4
            mime == "video/webm" -> MediaType.WEBM
            mime.startsWith("video/") -> MediaType.VIDEO
            mime.startsWith("audio/") -> MediaType.AUDIO
            else -> null
        }
    }

    private fun parts(url: String): Pair<String, String> = runCatching {
        val uri = URI(url)
        uri.path.orEmpty().lowercase(Locale.US) to uri.rawQuery.orEmpty().lowercase(Locale.US)
    }.getOrElse {
        url.substringBefore('?').lowercase(Locale.US) to url.substringAfter('?', "").lowercase(Locale.US)
    }
}
