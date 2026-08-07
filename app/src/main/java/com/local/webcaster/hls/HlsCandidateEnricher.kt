package com.local.webcaster.hls

import com.local.webcaster.detection.MediaCandidate
import com.local.webcaster.detection.MediaCandidateRepository
import com.local.webcaster.detection.MediaObservation
import com.local.webcaster.detection.MediaType
import com.local.webcaster.detection.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import android.webkit.CookieManager
import com.local.webcaster.relay.HeaderContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import com.local.webcaster.security.SafeLogger
import com.local.webcaster.security.PublicNetworkDns
import com.local.webcaster.security.BoundedBodyReader

class HlsCandidateEnricher(
    private val repository: MediaCandidateRepository,
    private val scope: CoroutineScope,
    private val parser: HlsManifestParser = HlsManifestParser(),
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .dns(PublicNetworkDns)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val active = ConcurrentHashMap.newKeySet<String>()
    private val completedAt = ConcurrentHashMap<String, Long>()

    fun enrich(candidate: MediaCandidate) {
        if (candidate.mediaType !in setOf(MediaType.HLS, MediaType.DASH)) return
        val now = System.currentTimeMillis()
        if (now - (completedAt[candidate.resolvedUrl] ?: 0L) < ENRICHMENT_CACHE_MS ||
            !active.add(candidate.resolvedUrl)
        ) return
        scope.launch(Dispatchers.IO) {
            var completed = false
            try {
                if (candidate.mediaType == MediaType.DASH) {
                    enrichDash(candidate)
                    completed = true
                    return@launch
                }
                val request = Request.Builder().url(candidate.resolvedUrl)
                    .header("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,text/plain")
                    .apply { candidate.requiredHeaders.forEach { (name, value) -> header(name, value) } }
                    .build()
                client.newCall(request).execute().use { response ->
                    SafeLogger.debug(
                        "HTTP_STATUS manifest=${response.code} type=HLS url=${SafeLogger.redactedUrl(candidate.resolvedUrl)}"
                    )
                    if (!response.isSuccessful) return@use
                    val source = response.body?.source() ?: return@use
                    val bytes = BoundedBodyReader.read(source, MAX_MANIFEST_BYTES) ?: return@use
                    val finalManifestUrl = response.request.url.toString()
                    val manifest = parser.parse(bytes.toString(Charsets.UTF_8), finalManifestUrl)
                    if (!manifest.isValid) return@use
                    val childManifest = manifest.variants.firstOrNull()?.let { variant ->
                        inspectVariant(candidate, finalManifestUrl, variant.url)
                    }
                    val drm = manifest.isDrm || childManifest?.isDrm == true
                    val live = manifest.isLive || childManifest?.isLive == true
                    SafeLogger.debug(
                        "${if (manifest.isMaster) "HLS_MASTER" else "HLS_VARIANT"} " +
                            "live=$live variants=${manifest.variants.size} drm=$drm " +
                            "url=${SafeLogger.redactedUrl(finalManifestUrl)}"
                    )
                    repository.update(candidate.id) {
                        it.copy(
                            mimeType = response.header("Content-Type")?.substringBefore(';') ?: it.mimeType,
                            isMasterPlaylist = manifest.isMaster,
                            isLive = live,
                            isDrm = drm,
                            confidence = if (drm) 1 else if (manifest.isMaster) 100 else 98,
                        )
                    }
                    if (!manifest.isDrm) manifest.variants.forEach { variant ->
                        repository.add(
                            MediaObservation(
                                url = variant.url,
                                // Never publish a completed background enrichment into a newer page.
                                pageUrl = candidate.pageUrl,
                                sourceType = SourceType.HLS_VARIANT,
                                title = variant.height?.let { "${it}p" } ?: "Variante HLS",
                                mimeType = "application/x-mpegURL",
                                width = variant.width,
                                height = variant.height,
                                bandwidth = variant.averageBandwidth ?: variant.bandwidth,
                                codecs = variant.codecs,
                                requiredHeaders = candidate.requiredHeaders,
                            )
                        )
                    }
                    completed = true
                }
            } catch (error: Exception) {
                SafeLogger.warn(
                    "CAST_ERROR manifest_enrichment=${error.javaClass.simpleName} url=${SafeLogger.redactedUrl(candidate.resolvedUrl)}"
                )
            } finally {
                active.remove(candidate.resolvedUrl)
                if (completed) completedAt[candidate.resolvedUrl] = System.currentTimeMillis()
            }
        }
    }

    private fun enrichDash(candidate: MediaCandidate) {
        val request = Request.Builder().url(candidate.resolvedUrl)
            .header("Accept", "application/dash+xml,application/xml,text/xml")
            .apply { candidate.requiredHeaders.forEach { (name, value) -> header(name, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            SafeLogger.debug(
                "HTTP_STATUS manifest=${response.code} type=DASH url=${SafeLogger.redactedUrl(candidate.resolvedUrl)}"
            )
            if (!response.isSuccessful) return
            val source = response.body?.source() ?: return
            val bytes = BoundedBodyReader.read(source, MAX_MANIFEST_BYTES) ?: return
            val text = bytes.toString(Charsets.UTF_8)
            if (!text.contains("<MPD", ignoreCase = true)) return
            val drm = text.contains("ContentProtection", ignoreCase = true) ||
                text.contains("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed", ignoreCase = true) ||
                text.contains("widevine", ignoreCase = true)
            repository.update(candidate.id) {
                it.copy(
                    mimeType = response.header("Content-Type")?.substringBefore(';') ?: "application/dash+xml",
                    isDrm = drm,
                    confidence = if (drm) 1 else 96,
                )
            }
        }
    }

    private fun inspectVariant(candidate: MediaCandidate, parentUrl: String, variantUrl: String): HlsManifest? {
        val headers = HeaderContext.from(candidate)
            .forUrl(parentUrl, variantUrl) { url ->
                runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
            }
        val request = Request.Builder().url(variantUrl)
            .header("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,text/plain")
            .apply { headers.asMap().forEach { (name, value) -> header(name, value) } }
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val source = response.body?.source() ?: return@use null
            val bytes = BoundedBodyReader.read(source, MAX_MANIFEST_BYTES) ?: return@use null
            parser.parse(bytes.toString(Charsets.UTF_8), response.request.url.toString())
                .takeIf(HlsManifest::isValid)
        }
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 1_048_576
        const val ENRICHMENT_CACHE_MS = 10 * 60 * 1_000L
    }
}
