package com.local.webcaster.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastUiStateTest {
    @Test
    fun controllerRequiresRealMediaAndSession() {
        assertFalse(CastUiState(connected = true).showController)
        assertFalse(CastUiState(hasMedia = true).showController)
        assertTrue(CastUiState(connected = true, hasMedia = true).showController)
    }

    @Test
    fun controllerCanRemainVisibleDuringRealResume() {
        assertTrue(CastUiState(reconnecting = true, hasMedia = true).showController)
        assertFalse(CastUiState(reconnecting = true, hasMedia = false).showController)
    }
}
