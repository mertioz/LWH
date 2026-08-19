package com.local.webcaster.detection

class MediaDetector(private val repository: MediaCandidateRepository) {
    var onCandidate: ((MediaCandidate) -> Unit)? = null

    fun observe(observation: MediaObservation): MediaCandidate? = repository.add(observation)?.also { onCandidate?.invoke(it) }

    fun observeNetwork(url: String, pageUrl: String, mimeType: String? = null, headers: Map<String, String> = emptyMap()) {
        if (!MediaUrlClassifier.isPotentialMedia(url, mimeType)) return
        repository.add(
            MediaObservation(
                url = url,
                pageUrl = pageUrl,
                sourceType = SourceType.NETWORK,
                mimeType = mimeType,
                requiredHeaders = headers,
            )
        )?.also { onCandidate?.invoke(it) }
    }

    fun beginRescan(pageUrl: String): Long? = repository.beginRescan(pageUrl)

    fun finishRescan(id: Long, pageUrl: String) = repository.finishRescan(id, pageUrl)
}
