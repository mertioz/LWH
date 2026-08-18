package com.local.webcaster.diagnostics

import com.local.webcaster.detection.MediaCandidate
import com.local.webcaster.detection.MediaType
import com.local.webcaster.detection.SourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticBuilderTest {
    @Test fun stripsQueriesAndNeverPrintsHeaders() {
        val candidate = MediaCandidate(
            id = "id",
            url = "https://cdn.test/video.m3u8?token=super-secret",
            resolvedUrl = "https://cdn.test/video.m3u8?token=super-secret",
            pageUrl = "https://site.test/watch?session=private",
            mediaType = MediaType.HLS,
            sourceType = SourceType.NETWORK,
            requiredHeaders = mapOf("Authorization" to "Bearer secret", "Cookie" to "sid=secret"),
        )
        val text = DiagnosticBuilder.build(candidate)
        assertTrue(text.contains("https://cdn.test/....m3u8"))
        assertFalse(text.contains("super-secret"))
        assertFalse(text.contains("Bearer"))
        assertFalse(text.contains("sid="))
    }
}
