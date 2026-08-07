package com.local.webcaster.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterRuleParserTest {
    @Test fun parsesDomainAndExceptionRulesOnly() {
        val result = FilterRuleParser().parse("""
            ! comment
            ||ads.example^
            @@||media.ads.example^
            /unsupported-regex/
        """.trimIndent())
        assertEquals(setOf("ads.example"), result.blockedDomains)
        assertTrue("media.ads.example" in result.allowedDomains)
    }

    @Test fun preservesThirdPartyScopeAndSkipsUnsupportedResourceRules() {
        val result = FilterRuleParser().parse(
            "||ads.example^${'$'}third-party\n" +
                "||scripts.example^${'$'}script\n" +
                "@@||media.example^${'$'}third-party"
        )
        assertEquals(setOf("ads.example"), result.thirdPartyBlockedDomains)
        assertEquals(setOf("media.example"), result.thirdPartyAllowedDomains)
        assertTrue("scripts.example" !in result.blockedDomains)
    }
}
