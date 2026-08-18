package com.local.webcaster.adblock

import com.local.webcaster.data.SitePreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleAdBlockEngineTest {
    private val engine = SimpleAdBlockEngine(
        FilterRules(
            blockedDomains = setOf("ads.example"),
            allowedDomains = setOf("media.safe.example"),
            thirdPartyBlockedDomains = setOf("tracker.example"),
        )
    )

    @Test fun blocksKnownAdMediaButNeverGenericThirdPartyVideo() {
        assertTrue(
            engine.shouldBlock(
                AdBlockRequest(
                    "https://ads.example/pre.mp4",
                    "https://site.example/watch",
                    isMediaRequest = true,
                )
            )
        )
        assertFalse(
            engine.shouldBlock(
                AdBlockRequest(
                    "https://video.cdn.example/master.m3u8",
                    "https://site.example/watch",
                    isMediaRequest = true,
                )
            )
        )
    }

    @Test fun blocksDefinitePrerollPathWithoutBlockingNormalCdnSegments() {
        assertTrue(
            engine.shouldBlock(
                AdBlockRequest(
                    "https://cdn.other.example/vast/preroll.m3u8",
                    "https://site.example/watch",
                    isMediaRequest = true,
                )
            )
        )
        assertFalse(
            engine.shouldBlock(
                AdBlockRequest(
                    "https://cdn.other.example/video/segment-10.ts",
                    "https://site.example/watch",
                    isMediaRequest = true,
                )
            )
        )
    }

    @Test fun userGestureAllowsLegitimateNavigationButNotKnownAdDestination() {
        assertFalse(
            engine.shouldBlock(
                AdBlockRequest(
                    "https://news.other.example/article",
                    "https://site.example/watch",
                    isMainFrame = true,
                    hasUserGesture = true,
                )
            )
        )
        assertTrue(
            engine.shouldBlock(
                AdBlockRequest(
                    "https://ads.example/landing",
                    "https://site.example/watch",
                    isMainFrame = true,
                    hasUserGesture = true,
                )
            )
        )
    }

    @Test fun siteCanBlockTrackersWithoutBlockingAdsAndViceVersa() {
        val rules = FilterRules(
            blockedDomains = setOf("ads.example"),
            allowedDomains = emptySet(),
            thirdPartyBlockedDomains = setOf("tracker.example"),
        )
        val trackerOnly = SimpleAdBlockEngine(
            rules, { true }, { _, _ -> },
            { SitePreferences(ads = false, trackers = true) },
        )
        assertTrue(trackerOnly.shouldBlock(AdBlockRequest("https://tracker.example/pixel", "https://site.example")))
        assertFalse(trackerOnly.shouldBlock(AdBlockRequest("https://ads.example/banner", "https://site.example")))

        val adsOnly = SimpleAdBlockEngine(
            rules, { true }, { _, _ -> },
            { SitePreferences(ads = true, trackers = false) },
        )
        assertFalse(adsOnly.shouldBlock(AdBlockRequest("https://tracker.example/pixel", "https://site.example")))
        assertTrue(adsOnly.shouldBlock(AdBlockRequest("https://ads.example/banner", "https://site.example")))
    }
}
