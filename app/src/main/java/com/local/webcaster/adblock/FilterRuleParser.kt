package com.local.webcaster.adblock

data class FilterRules(
    val blockedDomains: Set<String>,
    val allowedDomains: Set<String>,
    val thirdPartyBlockedDomains: Set<String> = emptySet(),
    val thirdPartyAllowedDomains: Set<String> = emptySet(),
) {
    val domainCount: Int
        get() = blockedDomains.size + thirdPartyBlockedDomains.size

    operator fun plus(other: FilterRules) = FilterRules(
        blockedDomains + other.blockedDomains,
        allowedDomains + other.allowedDomains,
        thirdPartyBlockedDomains + other.thirdPartyBlockedDomains,
        thirdPartyAllowedDomains + other.thirdPartyAllowedDomains,
    )
}

class FilterRuleParser {
    fun parse(text: String): FilterRules {
        val blocked = linkedSetOf<String>()
        val allowed = linkedSetOf<String>()
        val thirdPartyBlocked = linkedSetOf<String>()
        val thirdPartyAllowed = linkedSetOf<String>()
        text.lineSequence().map(String::trim).forEach { line ->
            if (line.isBlank() || line.startsWith("!") || line.startsWith("[")) return@forEach
            val exception = line.startsWith("@@")
            val body = if (exception) line.removePrefix("@@") else line
            if (!body.startsWith("||")) return@forEach
            val networkPart = body.substringBefore('$')
            val options = body.substringAfter('$', "").split(',').map(String::trim).filter(String::isNotEmpty)
            // Resource-, site- and redirect-scoped ABP rules need request metadata WebView does
            // not expose reliably. Skipping them is safer than broadening them and breaking media.
            if (options.any { it.lowercase() !in setOf("third-party") }) return@forEach
            val hostPart = networkPart.removePrefix("||").substringBefore('^')
            if (hostPart.contains('/')) return@forEach
            val domain = hostPart.trim('.').lowercase()
            if (!validDomain(domain)) return@forEach
            val thirdPartyOnly = options.any { it.equals("third-party", true) }
            when {
                exception && thirdPartyOnly -> thirdPartyAllowed += domain
                exception -> allowed += domain
                thirdPartyOnly -> thirdPartyBlocked += domain
                else -> blocked += domain
            }
        }
        return FilterRules(blocked, allowed, thirdPartyBlocked, thirdPartyAllowed)
    }

    private fun validDomain(domain: String): Boolean =
        domain.length in 1..253 && '.' in domain &&
            domain.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
}
