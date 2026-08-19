package com.local.webcaster.detection

import com.local.webcaster.security.UrlValidator
import com.local.webcaster.security.SafeLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.security.MessageDigest

class MediaCandidateRepository {
    private val lock = Any()
    private val candidates = linkedMapOf<String, MediaCandidate>()
    private val staleUntil = linkedMapOf<String, Long>()
    private var activeRescan: RescanSession? = null
    private var nextRescanId = 0L
    private val _items = MutableStateFlow<List<MediaCandidate>>(emptyList())
    val items: StateFlow<List<MediaCandidate>> = _items.asStateFlow()
    @Volatile private var currentPageUrl: String = ""
    @Volatile private var currentPageStartedAt: Long = 0

    fun resetForPage(pageUrl: String) = synchronized(lock) {
        val now = System.currentTimeMillis()
        candidates.keys.filterNot(UrlValidator::isBlobMediaUrl)
            .forEach { staleUntil[it] = now + STALE_NAVIGATION_GRACE_MS }
        staleUntil.entries.removeAll { it.value <= now }
        currentPageUrl = pageUrl
        currentPageStartedAt = now
        activeRescan = null
        SafeLogger.debug("MEDIA_RESET page=${SafeLogger.redactedUrl(pageUrl)} quarantined=${staleUntil.size}")
        candidates.clear()
        _items.value = emptyList()
    }

    fun updatePageUrl(pageUrl: String): Unit = synchronized(lock) {
        if (pageUrl.isBlank() || pageUrl == currentPageUrl) return@synchronized
        currentPageUrl = pageUrl
        candidates.replaceAll { _, candidate -> candidate.copy(pageUrl = pageUrl) }
        publish()
    }

    fun currentPageUrl(): String = currentPageUrl

    fun beginRescan(pageUrl: String): Long? = synchronized(lock) {
        if (pageUrl.isBlank() || pageUrl != currentPageUrl || activeRescan != null) return null
        val id = ++nextRescanId
        activeRescan = RescanSession(id, pageUrl)
        id
    }

    fun finishRescan(id: Long, pageUrl: String): Unit = synchronized(lock) {
        val rescan = activeRescan?.takeIf { it.id == id && it.pageUrl == pageUrl } ?: return
        activeRescan = null
        val now = System.currentTimeMillis()
        prune(now)
        candidates.entries.removeAll { (key, candidate) ->
            key !in rescan.seenKeys && candidate.discoverySources.all { it in DOM_RESCAN_SOURCES }
        }
        publish()
    }

    fun add(observation: MediaObservation): MediaCandidate? {
        val blob = UrlValidator.isBlobMediaUrl(observation.url)
        val normalized = if (blob) observation.url else UrlValidator.normalize(observation.url) ?: return null
        if (!blob && MediaUrlClassifier.isSegment(normalized)) return null
        val type = if (blob) MediaType.BLOB else MediaUrlClassifier.classify(normalized, observation.mimeType)
        if (type == MediaType.UNKNOWN && !MediaUrlClassifier.isPotentialMedia(normalized, observation.mimeType)) return null
        val key = normalized
        synchronized(lock) {
            if (currentPageUrl.isNotBlank() && observation.pageUrl.isNotBlank() && observation.pageUrl != currentPageUrl) return null
            if (observation.documentStartedAt != null &&
                observation.documentStartedAt + DOCUMENT_CLOCK_TOLERANCE_MS < currentPageStartedAt
            ) return null
            val repeatedAfterNavigation = (staleUntil[normalized] ?: 0L) > System.currentTimeMillis() &&
                normalized != UrlValidator.normalize(currentPageUrl)
            // A destroyed WebView document can emit callbacks after the new document starts, and
            // those callbacks may carry a fresh-looking timestamp. An exact URL cleared by the
            // navigation is quarantined briefly unless it is itself the new top-level URL.
            if (repeatedAfterNavigation) {
                SafeLogger.debug(
                    "MEDIA_STALE source=${observation.sourceType} url=${SafeLogger.redactedUrl(normalized)}"
                )
                return null
            }
            val previous = candidates[key]
            activeRescan?.takeIf { it.pageUrl == currentPageUrl }?.seenKeys?.add(key)
            val sources = previous?.discoverySources.orEmpty() + observation.sourceType
            val source = preferredSource(sources)
            val mime = observation.mimeType ?: previous?.mimeType
            val master = observation.isMasterPlaylist || previous?.isMasterPlaylist == true
            val drm = observation.isDrm || previous?.isDrm == true
            val now = System.currentTimeMillis()
            val candidate = MediaCandidate(
                id = previous?.id ?: sha256(key).take(16),
                url = previous?.url ?: observation.url,
                resolvedUrl = normalized,
                pageUrl = observation.pageUrl.ifBlank { previous?.pageUrl.orEmpty() },
                title = observation.title ?: previous?.title,
                mimeType = mime,
                mediaType = if (type != MediaType.UNKNOWN) type else previous?.mediaType ?: type,
                sourceType = source,
                host = runCatching {
                    URI(if (blob) observation.pageUrl else normalized).host.orEmpty()
                }.getOrDefault(""),
                width = observation.width ?: previous?.width,
                height = observation.height ?: previous?.height,
                bandwidth = observation.bandwidth ?: previous?.bandwidth,
                codecs = observation.codecs ?: previous?.codecs,
                isMasterPlaylist = master,
                isLive = observation.isLive || previous?.isLive == true,
                isDrm = drm,
                unavailableReason = observation.unavailableReason ?: previous?.unavailableReason ?: if (blob) {
                    "Cette video utilise blob:/MediaSource. Le flux reseau reel doit etre detecte avant de pouvoir la caster."
                } else null,
                relayRequired = observation.relayRequired || previous?.relayRequired == true,
                confidence = MediaScorer.score(type, source, mime, master, drm, sources.size, normalized),
                discoveredAt = now,
                requiredHeaders = previous?.requiredHeaders.orEmpty() + observation.requiredHeaders,
                discoverySources = sources,
                posterUrl = observation.posterUrl ?: previous?.posterUrl,
                subtitleTracks = (previous?.subtitleTracks.orEmpty() + observation.subtitleTracks)
                    .distinctBy { it.url },
                lastHttpStatus = previous?.lastHttpStatus,
                durationMs = observation.durationMs ?: previous?.durationMs,
            )
            candidates[key] = candidate
            if (previous == null) {
                SafeLogger.debug(
                    "MEDIA_DETECT source=$source type=$type url=${SafeLogger.redactedUrl(normalized)}"
                )
            }
            prune(now)
            publish()
            return candidate
        }
    }

    fun update(id: String, transform: (MediaCandidate) -> MediaCandidate): Unit = synchronized(lock) {
        val entry = candidates.entries.firstOrNull { it.value.id == id } ?: return
        candidates[entry.key] = transform(entry.value)
        publish()
    }

    private fun publish() {
        val values = candidates.values
        val visible = if (values.any { it.mediaType != MediaType.BLOB }) values.filter { it.mediaType != MediaType.BLOB } else values
        _items.value = visible.sortedWith(
            compareByDescending<MediaCandidate> { it.confidence }
                .thenByDescending { it.isMasterPlaylist }
                .thenByDescending { it.height ?: 0 }
                .thenByDescending { it.discoveredAt }
        )
    }

    private fun prune(now: Long) {
        candidates.entries.removeAll { now - it.value.discoveredAt > MAX_CANDIDATE_AGE_MS }
        if (candidates.size <= MAX_CANDIDATES) return
        candidates.entries.sortedBy { it.value.discoveredAt }
            .take(candidates.size - MAX_CANDIDATES)
            .forEach { candidates.remove(it.key) }
    }

    private fun preferredSource(sources: Set<SourceType>): SourceType = listOf(
        SourceType.VIDEO_CURRENT_SRC, SourceType.DOM, SourceType.SOURCE_ELEMENT,
        SourceType.ENCRYPTED_MEDIA, SourceType.NETWORK, SourceType.FETCH, SourceType.XHR,
        SourceType.PERFORMANCE, SourceType.HLS_VARIANT,
    ).firstOrNull { it in sources } ?: sources.first()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private data class RescanSession(
        val id: Long,
        val pageUrl: String,
        val seenKeys: MutableSet<String> = linkedSetOf(),
    )

    private companion object {
        const val MAX_CANDIDATES = 48
        const val MAX_CANDIDATE_AGE_MS = 30 * 60 * 1_000L
        const val DOCUMENT_CLOCK_TOLERANCE_MS = 1_000L
        const val STALE_NAVIGATION_GRACE_MS = 2 * 60 * 1_000L
        val DOM_RESCAN_SOURCES = setOf(
            SourceType.DOM,
            SourceType.VIDEO_CURRENT_SRC,
            SourceType.SOURCE_ELEMENT,
            SourceType.ENCRYPTED_MEDIA,
        )
    }
}
