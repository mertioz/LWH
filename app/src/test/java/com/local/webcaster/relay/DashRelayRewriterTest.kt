package com.local.webcaster.relay

import org.junit.Assert.assertFalse
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
}
