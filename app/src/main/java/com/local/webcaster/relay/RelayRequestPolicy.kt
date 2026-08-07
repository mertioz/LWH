package com.local.webcaster.relay

import com.local.webcaster.detection.MediaType

internal object RelayRequestPolicy {
    fun shouldForwardRange(type: MediaType): Boolean = type !in setOf(MediaType.HLS, MediaType.DASH)
}
