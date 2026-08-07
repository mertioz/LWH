package com.local.webcaster.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUrlClassifierTest {
    @Test fun classifiesExtensionsWithoutDroppingQueries() {
        assertEquals(MediaType.HLS, MediaUrlClassifier.classify("https://cdn.test/master.m3u8?token=abc"))
        assertEquals(MediaType.DASH, MediaUrlClassifier.classify("https://cdn.test/manifest.mpd"))
        assertEquals(MediaType.MP4, MediaUrlClassifier.classify("https://cdn.test/movie.mp4#part"))
        assertEquals(MediaType.WEBM, MediaUrlClassifier.classify("https://cdn.test/movie.webm"))
    }

    @Test fun contentTypeWinsAndUnsafeSchemesAreRejected() {
        assertEquals(MediaType.HLS, MediaUrlClassifier.classify("https://cdn.test/no-extension", "application/vnd.apple.mpegurl"))
        assertEquals(MediaType.VIDEO, MediaUrlClassifier.classify("https://cdn.test/get?id=1", "video/av1"))
        assertFalse(MediaUrlClassifier.isPotentialMedia("blob:https://site.test/id", "video/mp4"))
        assertFalse(MediaUrlClassifier.isPotentialMedia("https://cdn.test/segment.m4s"))
        assertTrue(MediaUrlClassifier.isPotentialMedia("https://cdn.test/path/manifest?id=1"))
    }

    @Test fun recognizesExtensionlessMediaAndSuppressesTransportChunks() {
        assertEquals(
            MediaType.MP4,
            MediaUrlClassifier.classify("https://cdn.test/videoplayback?id=1&mime=video%2Fmp4"),
        )
        assertTrue(MediaUrlClassifier.isPotentialMedia("https://cdn.test/videoplayback?id=1&mime=video%2Fmp4"))
        assertTrue(MediaUrlClassifier.isSegment("https://cdn.test/init-video.mp4"))
        assertTrue(MediaUrlClassifier.isSegment("https://cdn.test/videoplayback?mime=video%2Fmp4&range=0-999"))
        assertFalse(MediaUrlClassifier.isPotentialMedia("https://cdn.test/videoplayback?mime=video%2Fmp4&range=0-999"))
        assertTrue(MediaUrlClassifier.isMediaTransport("https://cdn.test/chunk-42.m4s"))
    }
}
