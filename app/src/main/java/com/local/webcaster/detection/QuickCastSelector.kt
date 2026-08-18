package com.local.webcaster.detection

object QuickCastSelector {
    fun select(candidates: List<MediaCandidate>): MediaCandidate? = candidates
        .asSequence()
        .filterNot { it.isDrm || it.unavailableReason != null || it.mediaType == MediaType.BLOB }
        .filterNot { MediaUrlClassifier.isSegment(it.resolvedUrl) }
        .distinctBy { canonicalKey(it) }
        .sortedWith(
            compareByDescending<MediaCandidate> { selectionScore(it) }
                .thenByDescending { it.height ?: 0 }
                .thenByDescending { it.bandwidth ?: 0L }
                .thenByDescending { it.discoveredAt }
        )
        .firstOrNull()

    internal fun selectionScore(candidate: MediaCandidate): Int = candidate.confidence + when {
        candidate.mediaType == MediaType.HLS && candidate.isMasterPlaylist -> 12
        candidate.mediaType == MediaType.DASH -> 8
        candidate.mediaType == MediaType.HLS && candidate.sourceType != SourceType.HLS_VARIANT -> 6
        else -> 0
    }

    private fun canonicalKey(candidate: MediaCandidate): String = buildString {
        append(candidate.mediaType)
        append('|')
        append(candidate.resolvedUrl.substringBefore('#'))
    }
}
