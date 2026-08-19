package com.local.webcaster.cast

import com.google.android.gms.cast.MediaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastWatchdogPolicyTest {
    @Test fun loadingAndBufferingAreBothWatchedForStalls() {
        assertTrue(CastWatchdogPolicy.isStalledState(MediaStatus.PLAYER_STATE_LOADING))
        assertTrue(CastWatchdogPolicy.isStalledState(MediaStatus.PLAYER_STATE_BUFFERING))
        assertFalse(CastWatchdogPolicy.isStalledState(MediaStatus.PLAYER_STATE_PLAYING))
        assertFalse(CastWatchdogPolicy.isStalledState(MediaStatus.PLAYER_STATE_PAUSED))
    }

    @Test fun timeoutReasonPreservesReceiverState() {
        assertEquals("loading_timeout", CastWatchdogPolicy.timeoutReason(MediaStatus.PLAYER_STATE_LOADING))
        assertEquals("buffer_timeout", CastWatchdogPolicy.timeoutReason(MediaStatus.PLAYER_STATE_BUFFERING))
        assertEquals("start_timeout", CastWatchdogPolicy.timeoutReason(null))
    }
}
