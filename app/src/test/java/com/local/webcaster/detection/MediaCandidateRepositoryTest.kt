package com.local.webcaster.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCandidateRepositoryTest {
    @Test fun keepsBlobExplanationUntilRealNetworkStreamIsFound() {
        val repository = MediaCandidateRepository()
        repository.resetForPage("https://site.test/watch")
        repository.add(
            MediaObservation(
                url = "blob:https://site.test/id",
                pageUrl = "https://site.test/watch",
                sourceType = SourceType.VIDEO_CURRENT_SRC,
            )
        )
        assertEquals(MediaType.BLOB, repository.items.value.single().mediaType)
        assertTrue(repository.items.value.single().unavailableReason != null)

        repository.updatePageUrl("https://site.test/watch/episode-2")
        repository.add(
            MediaObservation(
                url = "https://cdn.test/master.m3u8",
                pageUrl = "https://site.test/watch/episode-2",
                sourceType = SourceType.NETWORK,
            )
        )
        assertEquals(listOf(MediaType.HLS), repository.items.value.map { it.mediaType })
    }

    @Test fun rejectsLateObservationsFromThePreviousDocument() {
        val repository = MediaCandidateRepository()
        repository.resetForPage("https://new.test/watch")
        repository.add(
            MediaObservation(
                url = "https://old-cdn.test/video.mp4",
                pageUrl = "https://new.test/watch",
                sourceType = SourceType.VIDEO_CURRENT_SRC,
                documentStartedAt = 1L,
            )
        )
        assertTrue(repository.items.value.isEmpty())
    }

    @Test fun rejectsLateNetworkRequestsForMediaClearedByNavigation() {
        val repository = MediaCandidateRepository()
        repository.resetForPage("https://old.test/watch")
        repository.add(
            MediaObservation(
                url = "https://cdn.test/episode.mp4",
                pageUrl = "https://old.test/watch",
                sourceType = SourceType.NETWORK,
            )
        )
        repository.resetForPage("https://new.test/watch")
        repository.add(
            MediaObservation(
                url = "https://cdn.test/episode.mp4",
                pageUrl = "https://new.test/watch",
                sourceType = SourceType.NETWORK,
            )
        )
        assertTrue(repository.items.value.isEmpty())
    }

    @Test fun rescanRemovesMissingDomCandidatesButKeepsNetworkCandidates() {
        val pageUrl = "https://site.test/watch"
        val repository = MediaCandidateRepository()
        repository.resetForPage(pageUrl)
        repository.add(
            MediaObservation(
                url = "https://cdn.test/stale.mp4",
                pageUrl = pageUrl,
                sourceType = SourceType.VIDEO_CURRENT_SRC,
            )
        )
        repository.add(
            MediaObservation(
                url = "https://cdn.test/network-only.m3u8",
                pageUrl = pageUrl,
                sourceType = SourceType.NETWORK,
            )
        )

        val rescanId = requireNotNull(repository.beginRescan(pageUrl))
        repository.add(
            MediaObservation(
                url = "https://cdn.test/current.mp4",
                pageUrl = pageUrl,
                sourceType = SourceType.DOM,
            )
        )
        repository.finishRescan(rescanId, pageUrl)

        assertEquals(
            setOf("https://cdn.test/current.mp4", "https://cdn.test/network-only.m3u8"),
            repository.items.value.map { it.resolvedUrl }.toSet(),
        )
    }
}
