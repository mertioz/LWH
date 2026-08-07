package com.local.webcaster.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserRecordPolicyTest {
    @Test
    fun acceptsOnlyNetworkPages() {
        assertEquals("https://example.com/watch?id=1", BrowserRecordPolicy.normalizeUrl(" https://example.com/watch?id=1 "))
        assertNull(BrowserRecordPolicy.normalizeUrl("file:///tmp/video.html"))
        assertNull(BrowserRecordPolicy.normalizeUrl("javascript:alert(1)"))
        assertNull(BrowserRecordPolicy.normalizeUrl("https:///missing-host"))
    }

    @Test
    fun createsSafeDisplayMetadata() {
        assertEquals("example.com", BrowserRecordPolicy.domain("https://www.Example.com/page"))
        assertEquals("Example page", BrowserRecordPolicy.title("  Example page  ", "example.com"))
        assertEquals("example.com", BrowserRecordPolicy.title("https://example.com", "example.com"))
        assertEquals(200, BrowserRecordPolicy.title("x".repeat(300), "example.com").length)
    }
}
