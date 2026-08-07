package com.local.webcaster.adblock

data class AdBlockRequest(
    val url: String,
    val pageUrl: String,
    val isMainFrame: Boolean = false,
    val isMediaRequest: Boolean = false,
    val hasUserGesture: Boolean = false,
    val isRedirect: Boolean = false,
)
