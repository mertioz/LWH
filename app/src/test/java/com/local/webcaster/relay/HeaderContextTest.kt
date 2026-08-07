package com.local.webcaster.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeaderContextTest {
    @Test fun stripsOriginBoundCredentialsFromCrossOriginChildren() {
        val original = HeaderContext(cookie = "parent=secret", authorization = "Bearer secret")
        val child = original.forUrl("https://video.test/master.m3u8", "https://audio.test/track.m3u8") {
            "audio=session"
        }
        assertEquals("audio=session", child.cookie)
        assertNull(child.authorization)
    }

    @Test fun preservesCredentialsAcrossSameOriginRedirects() {
        val original = HeaderContext(cookie = "session=secret", authorization = "Bearer secret")
        val redirected = original.forUrl("https://video.test/start", "https://video.test/final/master.m3u8") { null }
        assertEquals("session=secret", redirected.cookie)
        assertEquals("Bearer secret", redirected.authorization)
    }
}
