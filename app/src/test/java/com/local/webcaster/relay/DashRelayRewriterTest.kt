package com.local.webcaster.relay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DashRelayRewriterTest {
    @Test fun rewritesBaseUrlsAndSegmentTemplates() {
        val input = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011">
              <Period><AdaptationSet><Representation>
                <BaseURL>video/</BaseURL>
                <SegmentTemplate initialization="init-${'$'}RepresentationID${'$'}.mp4" media="chunk-${'$'}Number${'$'}.m4s" />
              </Representation></AdaptationSet></Period>
            </MPD>
        """.trimIndent()
        val result = DashRelayRewriter().rewrite(input, "https://cdn.test/root/manifest.mpd") { "relay:$it" }
        assertFalse(result.isDrm)
        assertTrue(result.text.contains("relay:https://cdn.test/root/video/"))
        assertTrue(result.text.contains("initialization=\"init-${'$'}RepresentationID${'$'}.mp4\""))
        assertTrue(result.text.contains("media=\"chunk-${'$'}Number${'$'}.m4s\""))
    }

    @Test fun rejectsContentProtection() {
        val input = "<MPD><Period><ContentProtection schemeIdUri=\"urn:uuid:test\"/></Period></MPD>"
        val result = DashRelayRewriter().rewrite(input, "https://cdn.test/manifest.mpd") { it }
        assertTrue(result.isDrm)
    }

    @Test fun rewritesRelativeTemplatesWhenNoBaseUrlExists() {
        val input = "<MPD><Period><SegmentTemplate media=\"seg-${'$'}Number${'$'}.m4s\"/></Period></MPD>"
        val result = DashRelayRewriter().rewrite(input, "https://cdn.test/live/manifest.mpd") { "relay:$it" }
        assertTrue(result.text.contains("relay:https://cdn.test/live/seg-${'$'}Number${'$'}.m4s"))
    }

    @Test fun preservesSignedAudioAndVideoTemplateQueries() {
        val input = """
            <?xml version="1.0"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate initialization="vinit.mp4?sign=abc&amp;expires=9" media="vseg-${'$'}Number${'$'}.m4s?sign=abc&amp;expires=9" />
                </AdaptationSet>
                <AdaptationSet mimeType="audio/mp4">
                  <SegmentTemplate initialization="ainit.mp4?sign=abc&amp;expires=9" media="aseg-${'$'}Number${'$'}.m4s?sign=abc&amp;expires=9" />
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val requested = mutableListOf<String>()
        DashRelayRewriter().rewrite(input, "https://cdn.test/movie/stream.mpd?manifest=1") {
            requested += it
            "relay:$it"
        }
        assertTrue(requested.contains("https://cdn.test/movie/vinit.mp4?sign=abc&expires=9"))
        assertTrue(requested.contains("https://cdn.test/movie/vseg-${'$'}Number${'$'}.m4s?sign=abc&expires=9"))
        assertTrue(requested.contains("https://cdn.test/movie/ainit.mp4?sign=abc&expires=9"))
        assertTrue(requested.contains("https://cdn.test/movie/aseg-${'$'}Number${'$'}.m4s?sign=abc&expires=9"))
    }

    @Test fun rejectsDocumentTypeEvenWhenXmlProviderLacksOptionalFeatures() {
        val input = "<!DOCTYPE MPD [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><MPD>&xxe;</MPD>"
        assertThrows(IllegalArgumentException::class.java) {
            DashRelayRewriter().rewrite(input, "https://cdn.test/manifest.mpd") { it }
        }
    }
}
