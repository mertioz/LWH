package com.local.webcaster.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsRelayRewriterTest {
    @Test fun rewritesVariantsSegmentsAndUriAttributes() {
        val input = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,URI="audio/index.m3u8?token=keep"
            #EXT-X-STREAM-INF:BANDWIDTH=1000
            video/main.m3u8
        """.trimIndent()
        val result = HlsRelayRewriter().rewrite(input, "https://cdn.test/root/master.m3u8") { "http://phone/token/media?up=${it}" }
        assertTrue(result.text.contains("up=https://cdn.test/root/audio/index.m3u8?token=keep"))
        assertTrue(result.text.contains("up=https://cdn.test/root/video/main.m3u8"))
        assertFalse(result.isDrm)
    }

    @Test fun doesNotRewriteEncryptionKeysAndMarksDrm() {
        val input = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"secret.key\"\n#EXTINF:3,\nsegment.ts"
        val result = HlsRelayRewriter().rewrite(input, "https://cdn.test/a/list.m3u8") { "relay:$it" }
        assertTrue(result.isDrm)
        assertTrue(result.text.contains("URI=\"secret.key\""))
        assertTrue(result.text.contains("relay:https://cdn.test/a/segment.ts"))
    }

    @Test fun marksExtensionlessVariantAsPlaylist() {
        var playlistFlag = false
        HlsRelayRewriter().rewriteTyped(
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nvariant?id=hd",
            "https://cdn.test/master",
        ) { _, isPlaylist ->
            playlistFlag = isPlaylist
            "relay"
        }
        assertTrue(playlistFlag)
    }

    @Test fun recognizesLiveVariantAndExtensionlessRenditionReportAsPlaylist() {
        var renditionIsPlaylist = false
        val result = HlsRelayRewriter().rewriteTyped(
            "#EXTM3U\n#EXT-X-PART:DURATION=0.5,URI=\"part.m4s\"\n" +
                "#EXT-X-RENDITION-REPORT:URI=\"next?id=2\"\n#EXTINF:4,\nsegment.ts",
            "https://cdn.test/live/current",
        ) { url, isPlaylist ->
            if (url.endsWith("next?id=2")) renditionIsPlaylist = isPlaylist
            "relay"
        }
        assertTrue(result.isLive)
        assertTrue(renditionIsPlaylist)
        assertEquals(3, result.referenceCount)
    }
}
