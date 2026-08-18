package com.local.webcaster.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickCastSelectorTest {
    @Test fun prefersAdaptiveMasterOverVariantPreviewAndDrm() {
        val base = MediaCandidate(
            id = "mp4",
            url = "https://cdn.test/preview/video.mp4",
            pageUrl = "https://site.test/watch",
            mediaType = MediaType.MP4,
            sourceType = SourceType.NETWORK,
            confidence = 95,
        )
        val master = base.copy(
            id = "master",
            url = "https://cdn.test/master.m3u8",
            resolvedUrl = "https://cdn.test/master.m3u8",
            mediaType = MediaType.HLS,
            isMasterPlaylist = true,
            confidence = 100,
        )
        val drm = master.copy(id = "drm", isDrm = true)
        assertEquals("master", QuickCastSelector.select(listOf(base, drm, master))?.id)
    }

    @Test fun detectorConfidenceCanOutweighAWeakPreviewManifest() {
        val main = MediaCandidate(
            id = "main",
            url = "https://cdn.test/movie.mp4",
            pageUrl = "https://site.test/watch",
            mediaType = MediaType.MP4,
            sourceType = SourceType.VIDEO_CURRENT_SRC,
            confidence = 96,
        )
        val previewDash = main.copy(
            id = "preview",
            url = "https://cdn.test/preview/manifest.mpd",
            resolvedUrl = "https://cdn.test/preview/manifest.mpd",
            mediaType = MediaType.DASH,
            confidence = 80,
        )
        assertEquals("main", QuickCastSelector.select(listOf(previewDash, main))?.id)
    }
}
