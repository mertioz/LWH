package com.local.webcaster.cast

import com.local.webcaster.detection.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveStreamCorsTest {
    @Test fun adaptiveStreamsRequireWildcardReceiverCors() {
        assertTrue(AdaptiveStreamCors.requiresRelay(MediaType.DASH, emptyList()))
        assertTrue(AdaptiveStreamCors.requiresRelay(MediaType.DASH, listOf("https://my.mail.ru")))
        assertTrue(AdaptiveStreamCors.requiresRelay(MediaType.HLS, listOf("https://player.test")))
        assertFalse(AdaptiveStreamCors.requiresRelay(MediaType.HLS, listOf("*")))
    }

    @Test fun directFilesDoNotUseAdaptiveCorsRule() {
        assertFalse(AdaptiveStreamCors.requiresRelay(MediaType.MP4, emptyList()))
    }
}
