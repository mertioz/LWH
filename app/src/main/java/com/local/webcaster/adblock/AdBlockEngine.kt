package com.local.webcaster.adblock

interface AdBlockEngine {
    fun shouldBlock(request: AdBlockRequest): Boolean
    fun setEnabled(enabled: Boolean)
    fun isEnabledForSite(host: String): Boolean
    fun setEnabledForSite(host: String, enabled: Boolean)
}
