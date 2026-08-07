package com.local.webcaster.cast

import com.local.webcaster.detection.MediaCandidate
import com.local.webcaster.detection.MediaType
import com.local.webcaster.detection.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class CastMediaLoaderTest {
    @Test fun normalizesNonCanonicalHlsMimeFromWebServers() {
        val candidate = MediaCandidate(
            id = "id",
            url = "https://media.test/master.m3u8",
            pageUrl = "https://site.test/watch",
            mimeType = "audio/mpegurl; charset=utf-8",
            mediaType = MediaType.HLS,
            sourceType = SourceType.NETWORK,
        )
        assertEquals("application/x-mpegURL", CastMediaLoader.contentType(candidate))
    }
}
