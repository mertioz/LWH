package com.local.webcaster.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.local.webcaster.detection.MediaCandidate
import com.local.webcaster.detection.MediaType
import com.local.webcaster.relay.HeaderContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class LocalPlayerActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var shouldEnterPip = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val candidate = token?.let(LocalPlaybackStore::take)
        if (candidate == null) {
            Toast.makeText(this, "Le media local n'est plus disponible.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        supportActionBar?.apply {
            title = candidate.title ?: candidate.host.ifBlank { "Lecture locale" }
            subtitle = candidate.host
            setDisplayHomeAsUpEnabled(true)
        }
        playerView = PlayerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setShowSubtitleButton(true)
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
        setContentView(playerView)
        initializePlayer(candidate)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initializePlayer(candidate: MediaCandidate) {
        val headerContext = HeaderContext.from(candidate)
        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val baseFactory = DefaultDataSource.Factory(this, httpFactory)
        val dataSourceFactory = ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            val destination = dataSpec.uri.toString()
            val headers = headerContext.forUrl(candidate.resolvedUrl, destination) { url ->
                runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
            }.asMap().filter { (name, value) ->
                name.lowercase() in SAFE_PLAYBACK_HEADERS && value.length <= 16_384
            }
            dataSpec.withAdditionalHeaders(headers)
        }
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exoPlayer
        playerView.player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                shouldEnterPip = isPlaying
                updatePipParams()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) = updatePipParams()

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Toast.makeText(this@LocalPlayerActivity, "Lecture locale impossible: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })
        val subtitleConfigurations = candidate.subtitleTracks
            .filter { it.mimeType.contains("vtt", true) || it.url.substringBefore('?').endsWith(".vtt", true) }
            .map { track ->
                MediaItem.SubtitleConfiguration.Builder(track.url.toUri())
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLabel(track.label)
                    .apply {
                        track.language?.let(::setLanguage)
                        if (track.isDefault) setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    }
                    .build()
            }
        val item = MediaItem.Builder()
            .setUri(candidate.resolvedUrl)
            .setMediaId(candidate.id)
            .setMimeType(localMimeType(candidate))
            .setSubtitleConfigurations(subtitleConfigurations)
            .build()
        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !::playerView.isInitialized) return
        val builder = PictureInPictureParams.Builder()
        val source = Rect()
        if (playerView.getGlobalVisibleRect(source)) builder.setSourceRectHint(source)
        val size = player?.videoSize
        if (size != null && size != VideoSize.UNKNOWN && size.width > 0 && size.height > 0) {
            val ratio = size.width.toDouble() / size.height.toDouble()
            if (ratio in (1.0 / 2.39)..2.39) builder.setAspectRatio(Rational(size.width, size.height))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(shouldEnterPip)
        setPictureInPictureParams(builder.build())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S && shouldEnterPip) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (::playerView.isInitialized) playerView.useController = !isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && !isChangingConfigurations) player?.pause()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TOKEN = "local_playback_token"
        private val SAFE_PLAYBACK_HEADERS = setOf("user-agent", "referer", "origin", "authorization", "accept", "accept-language", "cookie")

        fun start(context: Context, candidate: MediaCandidate) {
            val token = LocalPlaybackStore.put(candidate)
            context.startActivity(Intent(context, LocalPlayerActivity::class.java).putExtra(EXTRA_TOKEN, token))
        }

        private fun localMimeType(candidate: MediaCandidate): String? = when (candidate.mediaType) {
            MediaType.HLS -> MimeTypes.APPLICATION_M3U8
            MediaType.DASH -> MimeTypes.APPLICATION_MPD
            MediaType.MP4 -> MimeTypes.VIDEO_MP4
            MediaType.WEBM -> MimeTypes.VIDEO_WEBM
            else -> candidate.mimeType?.substringBefore(';')
        }
    }
}

private object LocalPlaybackStore {
    private val entries = ConcurrentHashMap<String, Pair<Long, MediaCandidate>>()

    fun put(candidate: MediaCandidate): String {
        val now = System.currentTimeMillis()
        entries.entries.removeAll { now - it.value.first > 60_000 }
        return UUID.randomUUID().toString().also { entries[it] = now to candidate }
    }

    fun take(token: String): MediaCandidate? = entries.remove(token)?.second
}
