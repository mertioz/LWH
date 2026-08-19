package com.local.webcaster.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalByteRangeParserTest {
    @Test fun parsesOpenEndedRange() {
        assertEquals(LocalByteRange(400, 999), LocalByteRangeParser.parse("bytes=400-", 1_000))
    }

    @Test fun parsesBoundedAndSuffixRanges() {
        assertEquals(LocalByteRange(10, 19), LocalByteRangeParser.parse("bytes=10-19", 1_000))
        assertEquals(LocalByteRange(900, 999), LocalByteRangeParser.parse("bytes=-100", 1_000))
    }

    @Test fun rejectsUnsatisfiableAndMultipleRanges() {
        assertNull(LocalByteRangeParser.parse("bytes=1000-", 1_000))
        assertNull(LocalByteRangeParser.parse("bytes=0-1,4-5", 1_000))
    }
}
