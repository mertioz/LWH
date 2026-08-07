package com.local.webcaster.hls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsManifestParserTest {
    private val parser = HlsManifestParser()

    @Test fun parsesMasterWithRelativeAbsoluteAndSignedVariants() {
        val text = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.42e01e"
            low/360.m3u8
            #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=2800000,RESOLUTION=1280x720
            https://other.test/720.m3u8?token=signed
            #EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080
            1080.m3u8?sig=keep
        """.trimIndent()
        val result = parser.parse(text, "https://cdn.test/path/master.m3u8?auth=master")
        assertTrue(result.isValid)
        assertTrue(result.isMaster)
        assertEquals(3, result.variants.size)
        assertEquals("https://cdn.test/path/low/360.m3u8", result.variants[0].url)
        assertEquals("https://other.test/720.m3u8?token=signed", result.variants[1].url)
        assertEquals("https://cdn.test/path/1080.m3u8?sig=keep", result.variants[2].url)
        assertEquals(1080, result.variants[2].height)
    }

    @Test fun distinguishesVodAndLive() {
        val vod = parser.parse("#EXTM3U\n#EXTINF:10,\na.ts\n#EXT-X-ENDLIST", "https://a.test/vod.m3u8")
        val live = parser.parse("#EXTM3U\n#EXTINF:4,\na.ts", "https://a.test/live.m3u8")
        assertFalse(vod.isLive)
        assertTrue(live.isLive)
    }

    @Test fun rejectsInvalidAndDetectsDrm() {
        assertFalse(parser.parse("not a manifest", "https://a.test/a.m3u8").isValid)
        val drm = parser.parse("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,KEYFORMAT=\"com.widevine\"\n#EXTINF:4,\na.ts", "https://a.test/a.m3u8")
        assertTrue(drm.isDrm)
        val encrypted = parser.parse("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n#EXTINF:4,\na.ts", "https://a.test/a.m3u8")
        assertTrue(encrypted.isDrm)
    }
}
