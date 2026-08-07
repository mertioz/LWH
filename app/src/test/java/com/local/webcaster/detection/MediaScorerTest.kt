package com.local.webcaster.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaScorerTest {
    @Test fun respectsPriorityOrder() {
        val master = MediaScorer.score(MediaType.HLS, SourceType.NETWORK, isMaster = true)
        val dash = MediaScorer.score(MediaType.DASH, SourceType.NETWORK)
        val mp4 = MediaScorer.score(MediaType.MP4, SourceType.NETWORK)
        assertEquals(100, master)
        assertTrue(dash > mp4)
    }

    @Test fun combinedSourcesBoostConfidenceAndDrmIsUnsupported() {
        val one = MediaScorer.score(MediaType.MP4, SourceType.NETWORK, sourceCount = 1)
        val three = MediaScorer.score(MediaType.MP4, SourceType.NETWORK, sourceCount = 3)
        assertTrue(three > one)
        assertEquals(1, MediaScorer.score(MediaType.HLS, SourceType.NETWORK, isDrm = true))
    }

    @Test fun likelyAdAndPreviewMediaCannotOutrankTheRealManifest() {
        val real = MediaScorer.score(
            MediaType.HLS, SourceType.NETWORK, url = "https://cdn.test/master.m3u8"
        )
        val preroll = MediaScorer.score(
            MediaType.HLS, SourceType.NETWORK, url = "https://cdn.test/preroll/ad.m3u8"
        )
        val preview = MediaScorer.score(
            MediaType.HLS, SourceType.NETWORK, url = "https://cdn.test/preview/clip.m3u8"
        )
        assertTrue(real > preview)
        assertTrue(preview > preroll)
    }
}
