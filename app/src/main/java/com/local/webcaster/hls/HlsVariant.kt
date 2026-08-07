package com.local.webcaster.hls

data class HlsVariant(
    val url: String,
    val bandwidth: Long? = null,
    val averageBandwidth: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val codecs: String? = null,
)
data class HlsManifest(
    val isValid: Boolean,
    val isMaster: Boolean,
    val isLive: Boolean,
    val isDrm: Boolean,
    val variants: List<HlsVariant>,
)
