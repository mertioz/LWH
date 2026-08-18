package com.local.webcaster.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver

class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(appContext: Context): CastOptions {
        val notificationOptions = NotificationOptions.Builder()
            .setTargetActivityClassName(ExpandedControlsActivity::class.java.name)
            .setActions(
                listOf(
                    MediaIntentReceiver.ACTION_REWIND,
                    MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                    MediaIntentReceiver.ACTION_FORWARD,
                    MediaIntentReceiver.ACTION_STOP_CASTING,
                ),
                intArrayOf(1, 3),
            )
            .setSkipStepMs(30_000)
            .build()
        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .setExpandedControllerActivityClassName(ExpandedControlsActivity::class.java.name)
            .build()
        return CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(mediaOptions)
            .setEnableReconnectionService(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
