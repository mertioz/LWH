package com.local.webcaster.relay

import com.local.webcaster.detection.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayRequestPolicyTest {
    @Test fun neverForwardsRangeToManifests() {
        assertFalse(RelayRequestPolicy.shouldForwardRange(MediaType.HLS))
        assertFalse(RelayRequestPolicy.shouldForwardRange(MediaType.DASH))
        assertTrue(RelayRequestPolicy.shouldForwardRange(MediaType.MP4))
        assertTrue(RelayRequestPolicy.shouldForwardRange(MediaType.UNKNOWN))
    }
}
