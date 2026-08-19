package com.local.webcaster.cast

import com.local.webcaster.detection.MediaType

/**
 * The Default Media Receiver fetches adaptive manifests and their children from a Cast web
 * application origin. A page-specific CORS grant cannot authorize that receiver, so only a
 * wildcard grant is safe for direct HLS/DASH delivery. The tokenized LAN relay supplies its own
 * receiver-facing CORS headers when this check fails.
 */
internal object AdaptiveStreamCors {
    fun requiresRelay(mediaType: MediaType, allowOriginValues: List<String>): Boolean {
        if (mediaType !in setOf(MediaType.HLS, MediaType.DASH)) return false
        return allowOriginValues
            .flatMap { it.split(',') }
            .map(String::trim)
            .none { it == "*" }
    }
}
