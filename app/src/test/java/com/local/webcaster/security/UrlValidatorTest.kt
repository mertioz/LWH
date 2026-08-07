package com.local.webcaster.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {
    @Test fun acceptsOnlyCredentialFreeHttpUrlsAndPreservesSignatures() {
        val signed = "https://cdn.example/video.mp4?token=a%2Bb&expires=42#ignored"
        assertTrue(UrlValidator.isValidMediaUrl(signed))
        assertEquals("https://cdn.example/video.mp4?token=a%2Bb&expires=42", UrlValidator.normalize(signed))
        assertFalse(UrlValidator.isValidMediaUrl("blob:https://example.test/id"))
        assertFalse(UrlValidator.isValidMediaUrl("file:///data/private.mp4"))
        assertFalse(UrlValidator.isValidMediaUrl("https://user:secret@example.test/video.mp4"))
        assertTrue(UrlValidator.isBlobMediaUrl("blob:https://example.test/id"))
        assertFalse(UrlValidator.isBlobMediaUrl("blob:file:///data/private"))
    }
}
