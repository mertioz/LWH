package com.local.webcaster.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedContentParserTest {
    @Test fun acceptsDirectAndEmbeddedHttpUrls() {
        assertEquals("https://example.test/watch", SharedContentParser.extractUrl("https://example.test/watch"))
        assertEquals(
            "https://example.test/watch?id=4",
            SharedContentParser.extractUrl("Regarde ceci: https://example.test/watch?id=4)."),
        )
    }

    @Test fun rejectsCredentialsScriptsAndPlainText() {
        assertNull(SharedContentParser.extractUrl("javascript:alert(1)"))
        assertNull(SharedContentParser.extractUrl("https://user:password@example.test/private"))
        assertNull(SharedContentParser.extractUrl("juste du texte"))
    }
}
