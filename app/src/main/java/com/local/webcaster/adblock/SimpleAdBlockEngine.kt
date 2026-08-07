package com.local.webcaster.adblock

import com.local.webcaster.data.PreferencesRepository
import java.net.URI
import java.util.Locale

class SimpleAdBlockEngine(
    initialRules: FilterRules,
    private val siteEnabled: (String) -> Boolean,
    private val updateSiteEnabled: (String, Boolean) -> Unit,
) : AdBlockEngine {
    constructor(initialRules: FilterRules, preferences: PreferencesRepository) : this(
        initialRules,
        preferences::isAdBlockEnabled,
        preferences::setAdBlockEnabled,
    )

    internal constructor(initialRules: FilterRules) : this(initialRules, { true }, { _, _ -> })

    @Volatile private var enabled = true
    @Volatile private var rules = initialRules

    override fun shouldBlock(request: AdBlockRequest): Boolean {
        if (!enabled) return false
        val pageHost = host(request.pageUrl)
        if (pageHost.isNotBlank() && !isEnabledForSite(pageHost)) return false
        val requestHost = host(request.url)
        if (requestHost.isBlank()) return false

        val thirdParty = pageHost.isBlank() || !sameSite(requestHost, pageHost)
        if (matches(requestHost, rules.allowedDomains) ||
            thirdParty && matches(requestHost, rules.thirdPartyAllowedDomains)
        ) return false
        val blockedDomain = matches(requestHost, rules.blockedDomains) ||
            thirdParty && matches(requestHost, rules.thirdPartyBlockedDomains)
        val definiteAd = definiteAdvertisingRequest(request.url)
        if (request.isMainFrame) {
            // A real user gesture may open a legitimate third-party site, but never turns a known
            // ad-network or fake redirect destination into a safe navigation.
            return thirdParty && (blockedDomain || definiteAd && (request.isRedirect || !request.hasUserGesture))
        }
        if (request.isMediaRequest) return blockedDomain || thirdParty && definiteAd
        return blockedDomain || suspiciousRequest(request.url) && (thirdParty || definiteAd)
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun isEnabledForSite(host: String): Boolean = siteEnabled(host)

    override fun setEnabledForSite(host: String, enabled: Boolean) =
        updateSiteEnabled(host, enabled)

    fun updateRules(newRules: FilterRules) {
        rules = newRules
    }

    private fun matches(host: String, domains: Set<String>): Boolean {
        var suffix = host
        while (true) {
            if (suffix in domains) return true
            val dot = suffix.indexOf('.')
            if (dot < 0) return false
            suffix = suffix.substring(dot + 1)
        }
    }

    private fun sameSite(first: String, second: String): Boolean =
        first == second || first.endsWith(".$second") || second.endsWith(".$first")

    private fun suspiciousRequest(url: String): Boolean {
        val value = runCatching {
            val uri = URI(url)
            "${uri.path.orEmpty()}?${uri.rawQuery.orEmpty()}".lowercase(Locale.US)
        }.getOrElse { url.lowercase(Locale.US) }
        return TRACKING_PATHS.any(value::contains)
    }

    private fun definiteAdvertisingRequest(url: String): Boolean {
        val value = runCatching {
            val uri = URI(url)
            "${uri.path.orEmpty()}?${uri.rawQuery.orEmpty()}".lowercase(Locale.US)
        }.getOrElse { url.lowercase(Locale.US) }
        return DEFINITE_AD_PATHS.any(value::contains)
    }

    private fun host(url: String) = runCatching {
        URI(url).host.orEmpty().lowercase(Locale.US).removeSuffix(".")
    }.getOrDefault("")

    private companion object {
        val TRACKING_PATHS = listOf(
            "/pagead/", "/ads/", "/adserver/", "/adservice/", "/prebid", "/advertising/",
            "/analytics", "/tracking", "/tracker", "/telemetry", "/collect?", "/pixel?",
            "/beacon?", "ad_unit=", "adunit=", "utm_source=adnetwork", "doubleclick",
        )
        val DEFINITE_AD_PATHS = listOf(
            "/vast/", "/vmap/", "/preroll/", "/pre-roll/", "/midroll/", "/video-ad/",
            "/adserver/", "/pagead/", "ad_break=", "adbreak=", "ad_unit=", "adunit=",
        )
    }
}
