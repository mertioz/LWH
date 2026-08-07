package com.local.webcaster.security

import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedBodyReaderTest {
    @Test fun readsNormalShortBodiesWithoutEofException() {
        val bytes = "#EXTM3U\n#EXTINF:4,\nsegment.ts".toByteArray()
        assertArrayEquals(bytes, BoundedBodyReader.read(Buffer().write(bytes), 1_024))
    }

    @Test fun acceptsExactLimitAndRejectsOneByteOver() {
        assertArrayEquals(ByteArray(8), BoundedBodyReader.read(Buffer().write(ByteArray(8)), 8))
        assertNull(BoundedBodyReader.read(Buffer().write(ByteArray(9)), 8))
    }
}
