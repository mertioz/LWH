package com.local.webcaster.cast

import android.app.Activity
import androidx.core.content.ContextCompat
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.local.webcaster.detection.MediaCandidate
import com.local.webcaster.relay.MediaRelay
import com.local.webcaster.security.SafeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CastUiState(
    val frameworkAvailable: Boolean? = null,
    val devicesAvailable: Boolean = false,
    val connected: Boolean = false,
    val reconnecting: Boolean = false,
    val deviceName: String? = null,
    val hasMedia: Boolean = false,
    val title: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val message: String? = null,
) {
    val showController: Boolean
        get() = hasMedia && (connected || reconnecting)
}

class CastManager(
    activity: Activity,
    private val relay: MediaRelay,
    private val requestRoutePicker: () -> Boolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainExecutor = ContextCompat.getMainExecutor(activity)
    private val _state = MutableStateFlow(CastUiState())
    val state: StateFlow<CastUiState> = _state.asStateFlow()

    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var registeredRemote: RemoteMediaClient? = null
    private var pendingCandidate: MediaCandidate? = null
    private var pendingForceRelay = false
    private var activeCandidate: MediaCandidate? = relay.activeCandidate
    private var relayAttempted = relay.isRunning
    private var relayRetryInFlight = false
    private var positionJob: Job? = null
    private var playbackWatchdogJob: Job? = null
    private var loadInFlight = false
    private var activeLoadRelayed = relay.isRunning
    private var activeContentId: String? = null
    private var playbackStarted = false
    private var released = false
    private var sessionEnding = false
    private var loadSequence = 0L

    private val castStateListener = CastStateListener { castState ->
        val session = sessionManager?.currentCastSession
        when {
            castState == CastState.CONNECTING -> {
                _state.value = _state.value.copy(reconnecting = true)
            }
            castState == CastState.CONNECTED && session?.isConnected == true && !_state.value.connected -> {
                connected(session)
            }
            castState != CastState.CONNECTED && session?.isConnected != true &&
                (_state.value.connected || _state.value.reconnecting) -> {
                disconnected()
            }
        }
        _state.value = _state.value.copy(
            devicesAvailable = castState != CastState.NO_DEVICES_AVAILABLE,
            reconnecting = castState == CastState.CONNECTING,
        )
    }

    private val remoteCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val status = registeredRemote?.mediaStatus
            val statusMatchesLoad = activeContentId == null ||
                registeredRemote?.mediaInfo?.contentId == activeContentId
            SafeLogger.debug(
                "CAST_MEDIA_STATUS player=${status?.playerState ?: "none"} idle=${status?.idleReason ?: "none"} " +
                    "position=${registeredRemote?.approximateStreamPosition ?: 0}"
            )
            if (!statusMatchesLoad && activeCandidate != null) {
                updateRemoteState()
                return
            }
            if (status?.playerState == MediaStatus.PLAYER_STATE_IDLE) {
                if (relayRetryInFlight || loadInFlight) return
                if (status.idleReason == MediaStatus.IDLE_REASON_ERROR) {
                    if (!tryRelayAfterDirectFailure()) {
                        clearMediaState(
                            endSession = true,
                            statusMessage = "La TV a refuse ce media: lien expire, acces requis ou format/codec incompatible.",
                        )
                    }
                } else {
                    clearMediaState(endSession = true)
                }
            } else {
                relayRetryInFlight = false
                updateRemoteState()
                if (status?.playerState == MediaStatus.PLAYER_STATE_BUFFERING &&
                    playbackWatchdogJob?.isActive != true
                ) {
                    schedulePlaybackWatchdog(loadSequence, activeLoadRelayed, STALL_TIMEOUT_MS)
                }
            }
        }

        override fun onMetadataUpdated() = updateRemoteState()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _state.value = _state.value.copy(reconnecting = true)
            message("Connexion a l'appareil Cast...")
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) = connected(session)

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            SafeLogger.warn("CAST_ERROR session_start code=$error")
            disconnected("Connexion Cast impossible. Verifiez le Wi-Fi et reessayez.")
        }

        override fun onSessionEnding(session: CastSession) = disconnected()
        override fun onSessionEnded(session: CastSession, error: Int) = disconnected()

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            _state.value = _state.value.copy(reconnecting = true)
            message("Reconnexion a la session Cast...")
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            connected(session)
            if (wasSuspended && relayAttempted && relay.hasNetworkChanged()) restartRelayAfterNetworkChange()
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            SafeLogger.warn("CAST_ERROR session_resume code=$error")
            disconnected("Reconnexion Cast impossible.")
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            registeredRemote?.unregisterCallback(remoteCallback)
            registeredRemote = null
            positionJob?.cancel()
            playbackWatchdogJob?.cancel()
            _state.value = _state.value.copy(connected = false, reconnecting = true, playing = false)
            message("Session Cast suspendue; reconnexion en cours...")
        }
    }

    init {
        CastContext.getSharedInstance(activity.applicationContext, mainExecutor)
            .addOnSuccessListener(mainExecutor, ::initialize)
            .addOnFailureListener(mainExecutor) {
                if (!released) {
                    _state.value = _state.value.copy(frameworkAvailable = false, devicesAvailable = false)
                    message("Google Cast n'est pas disponible sur cet appareil.")
                }
            }
    }

    fun cast(candidate: MediaCandidate, forceRelay: Boolean = false) {
        if (candidate.isDrm) {
            message("Ce flux est protege par DRM et ne peut pas etre caste.")
            return
        }
        candidate.unavailableReason?.let {
            message(it)
            return
        }
        pendingCandidate = candidate
        pendingForceRelay = forceRelay || candidate.relayRequired
        val manager = sessionManager
        when {
            _state.value.frameworkAvailable == false -> message("Google Cast n'est pas disponible sur cet appareil.")
            manager == null -> message("Initialisation de Google Cast...")
            manager.currentCastSession?.isConnected == true -> loadCandidate(candidate, pendingForceRelay)
            !requestRoutePicker() -> message("Le selecteur Cast n'est pas encore pret. Touchez l'icone Cast.")
            else -> message("Choisissez un Chromecast ou un Google TV.")
        }
    }

    fun togglePlayPause() {
        registeredRemote?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seek(positionMs: Long) {
        registeredRemote?.seek(MediaSeekOptions.Builder().setPosition(positionMs.coerceAtLeast(0)).build())
    }

    fun stopMedia() {
        registeredRemote?.stop()
        clearMediaState(endSession = true)
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun release() {
        released = true
        registeredRemote?.unregisterCallback(remoteCallback)
        registeredRemote = null
        sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        castContext?.removeCastStateListener(castStateListener)
        positionJob?.cancel()
        playbackWatchdogJob?.cancel()
        scope.cancel()
        // The application-scoped relay must survive Activity recreation and backgrounding.
        // It is stopped when playback/session ends, or when the process terminates.
    }

    private fun initialize(context: CastContext) {
        if (released) return
        castContext = context
        val manager = context.sessionManager
        sessionManager = manager
        manager.addSessionManagerListener(sessionListener, CastSession::class.java)
        context.addCastStateListener(castStateListener)
        val castState = context.castState
        _state.value = _state.value.copy(
            frameworkAvailable = true,
            devicesAvailable = castState != CastState.NO_DEVICES_AVAILABLE,
            reconnecting = castState == CastState.CONNECTING,
        )
        val currentSession = manager.currentCastSession?.takeIf { it.isConnected }
        currentSession?.let(::connected)
        if (currentSession != null && relayAttempted && relay.hasNetworkChanged()) restartRelayAfterNetworkChange()
        if (currentSession == null && pendingCandidate != null && !requestRoutePicker()) {
            message("Google Cast est pret. Touchez l'icone Cast pour choisir un appareil.")
        }
    }

    private fun connected(session: CastSession) {
        sessionEnding = false
        registeredRemote?.unregisterCallback(remoteCallback)
        registeredRemote = session.remoteMediaClient?.also { it.registerCallback(remoteCallback) }
        _state.value = _state.value.copy(
            frameworkAvailable = true,
            devicesAvailable = true,
            connected = true,
            reconnecting = false,
            deviceName = session.castDevice?.friendlyName,
            message = null,
        )
        updateRemoteState()
        startPositionUpdates()
        if (_state.value.buffering && activeCandidate != null) {
            schedulePlaybackWatchdog(loadSequence, activeLoadRelayed, STALL_TIMEOUT_MS)
        }
        pendingCandidate?.let { loadCandidate(it, pendingForceRelay) }
    }

    private fun disconnected(message: String? = null) {
        sessionEnding = false
        registeredRemote?.unregisterCallback(remoteCallback)
        registeredRemote = null
        relay.stop()
        positionJob?.cancel()
        playbackWatchdogJob?.cancel()
        pendingCandidate = null
        pendingForceRelay = false
        activeCandidate = null
        relayAttempted = false
        relayRetryInFlight = false
        loadInFlight = false
        activeContentId = null
        playbackStarted = false
        loadSequence++
        _state.value = CastUiState(
            frameworkAvailable = castContext != null,
            devicesAvailable = castContext?.castState != CastState.NO_DEVICES_AVAILABLE,
            message = message,
        )
    }

    private fun load(candidate: MediaCandidate, overrideUrl: String? = null, startPositionMs: Long = 0) {
        val client = registeredRemote ?: return
        val sequence = ++loadSequence
        pendingCandidate = null
        pendingForceRelay = false
        activeCandidate = candidate
        activeLoadRelayed = overrideUrl != null
        activeContentId = overrideUrl ?: candidate.resolvedUrl
        playbackStarted = false
        loadInFlight = true
        if (overrideUrl == null) {
            relay.stop()
            relayAttempted = false
            relayRetryInFlight = false
        }
        message(
            if (overrideUrl == null) "Envoi vers ${_state.value.deviceName ?: "l'appareil Cast"}..."
            else "Nouvelle tentative via le telephone..."
        )
        _state.value = _state.value.copy(
            hasMedia = true,
            title = candidate.title ?: candidate.host.takeIf(String::isNotBlank) ?: "Media web",
            playing = false,
            buffering = true,
            positionMs = startPositionMs,
            durationMs = 0,
        )
        SafeLogger.debug(
            "CAST_LOAD mode=${if (overrideUrl == null) "direct" else "relay"} type=${candidate.mediaType} " +
                "live=${candidate.isLive} url=${SafeLogger.redactedUrl(candidate.resolvedUrl)}"
        )
        schedulePlaybackWatchdog(
            sequence = sequence,
            relayed = overrideUrl != null,
            timeoutMs = if (overrideUrl == null) DIRECT_START_TIMEOUT_MS else RELAY_START_TIMEOUT_MS,
        )
        client.load(CastMediaLoader.request(candidate, overrideUrl, startPositionMs)).setResultCallback { result ->
            if (released || sequence != loadSequence) return@setResultCallback
            loadInFlight = false
            if (result.status.isSuccess) {
                relayRetryInFlight = false
                SafeLogger.debug(
                    "CAST_LOAD accepted mode=${if (overrideUrl == null) "direct" else "relay"} code=${result.status.statusCode}"
                )
                message("Lecture envoyee a ${_state.value.deviceName ?: "l'appareil Cast"}.")
            } else if (overrideUrl == null) {
                SafeLogger.warn(
                    "CAST_ERROR load mode=direct code=${result.status.statusCode} " +
                        "message=${result.status.statusMessage.orEmpty().take(160)}"
                )
                if (!tryRelayAfterDirectFailure()) {
                    clearMediaState(endSession = true, statusMessage = "Lecture Cast impossible.")
                }
            } else {
                SafeLogger.warn(
                    "CAST_ERROR load mode=relay code=${result.status.statusCode} " +
                        "message=${result.status.statusMessage.orEmpty().take(160)}"
                )
                clearMediaState(
                    endSession = true,
                    statusMessage = "Le receiver n'a pas pu lire ce media. Le lien a peut-etre expire.",
                )
            }
        }
    }

    private fun tryRelayAfterDirectFailure(): Boolean {
        val candidate = activeCandidate ?: return false
        if (relayAttempted || candidate.isDrm || candidate.unavailableReason != null) {
            return relayRetryInFlight
        }
        startRelay(candidate, if (playbackStarted) _state.value.positionMs else 0)
        return true
    }

    private fun loadCandidate(candidate: MediaCandidate, forceRelay: Boolean) {
        if (forceRelay) startRelay(candidate, 0) else load(candidate)
    }

    private fun startRelay(candidate: MediaCandidate, startPositionMs: Long) {
        playbackWatchdogJob?.cancel()
        relayAttempted = true
        relayRetryInFlight = true
        loadInFlight = true
        message("Tentative securisee via le telephone...")
        SafeLogger.debug(
            "MEDIA_RELAY selected type=${candidate.mediaType} url=${SafeLogger.redactedUrl(candidate.resolvedUrl)}"
        )
        relay.start(candidate)
            .onSuccess { load(candidate, it, startPositionMs) }
            .onFailure {
                loadInFlight = false
                SafeLogger.warn("CAST_ERROR relay_start=${it.javaClass.simpleName}")
                clearMediaState(
                    endSession = true,
                    statusMessage = it.message ?: "Relay local indisponible.",
                )
            }
    }

    private fun restartRelayAfterNetworkChange() {
        val candidate = activeCandidate ?: relay.activeCandidate ?: return
        val position = _state.value.positionMs
        message("Le reseau a change; actualisation du relay local...")
        relay.start(candidate)
            .onSuccess { load(candidate, it, position) }
            .onFailure { message(it.message ?: "Relay local indisponible apres le changement de reseau.") }
    }

    private fun updateRemoteState() {
        val client = registeredRemote ?: return
        val status = client.mediaStatus
        val statusMatchesLoad = activeContentId == null || client.mediaInfo?.contentId == activeContentId
        val playerState = status?.playerState
        if (statusMatchesLoad &&
            (playerState == MediaStatus.PLAYER_STATE_PLAYING || playerState == MediaStatus.PLAYER_STATE_PAUSED)
        ) {
            playbackStarted = true
            loadInFlight = false
            relayRetryInFlight = false
            playbackWatchdogJob?.cancel()
        }
        val pendingPlayback = activeCandidate != null &&
            (!statusMatchesLoad || playbackWatchdogJob?.isActive == true)
        _state.value = _state.value.copy(
            connected = true,
            reconnecting = false,
            hasMedia = statusMatchesLoad && status != null && status.playerState != MediaStatus.PLAYER_STATE_IDLE || pendingPlayback,
            title = if (statusMatchesLoad) {
                client.mediaInfo?.metadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE)
                    ?: _state.value.title
            } else _state.value.title,
            playing = statusMatchesLoad && status?.playerState == MediaStatus.PLAYER_STATE_PLAYING,
            buffering = statusMatchesLoad && status?.playerState == MediaStatus.PLAYER_STATE_BUFFERING || pendingPlayback,
            positionMs = if (statusMatchesLoad) client.approximateStreamPosition.coerceAtLeast(0) else _state.value.positionMs,
            durationMs = if (statusMatchesLoad) client.streamDuration.coerceAtLeast(0) else _state.value.durationMs,
        )
    }

    private fun clearMediaState(endSession: Boolean, statusMessage: String? = null) {
        loadSequence++
        positionJob?.cancel()
        playbackWatchdogJob?.cancel()
        relay.stop()
        pendingCandidate = null
        activeCandidate = null
        relayAttempted = false
        relayRetryInFlight = false
        loadInFlight = false
        activeContentId = null
        playbackStarted = false
        _state.value = _state.value.copy(
            hasMedia = false,
            title = null,
            playing = false,
            buffering = false,
            positionMs = 0,
            durationMs = 0,
            message = statusMessage ?: _state.value.message,
        )
        if (endSession && !sessionEnding) {
            sessionEnding = true
            sessionManager?.endCurrentSession(true)
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                updateRemoteState()
                delay(500)
            }
        }
    }

    private fun schedulePlaybackWatchdog(sequence: Long, relayed: Boolean, timeoutMs: Long) {
        playbackWatchdogJob?.cancel()
        playbackWatchdogJob = scope.launch {
            delay(timeoutMs)
            if (released || sequence != loadSequence) return@launch
            val status = registeredRemote?.mediaStatus
            val statusMatchesLoad = registeredRemote?.mediaInfo?.contentId == activeContentId
            if (statusMatchesLoad &&
                (status?.playerState == MediaStatus.PLAYER_STATE_PLAYING ||
                    status?.playerState == MediaStatus.PLAYER_STATE_PAUSED)
            ) return@launch
            val reason = if (status?.playerState == MediaStatus.PLAYER_STATE_BUFFERING) {
                "buffer_timeout"
            } else {
                "start_timeout"
            }
            SafeLogger.warn(
                "CAST_ERROR reason=$reason mode=${if (relayed) "relay" else "direct"} " +
                    "player=${status?.playerState ?: "none"} idle=${status?.idleReason ?: "none"}"
            )
            if (!relayed && tryRelayAfterDirectFailure()) return@launch
            clearMediaState(
                endSession = true,
                statusMessage = if (relayed) {
                    "Le media reste inaccessible meme via le telephone. Le lien peut etre expire ou incompatible."
                } else {
                    "Le receiver n'a pas demarre la lecture dans le delai attendu."
                },
            )
        }
    }

    private fun message(text: String) {
        _state.value = _state.value.copy(message = text)
    }

    private companion object {
        const val DIRECT_START_TIMEOUT_MS = 18_000L
        const val RELAY_START_TIMEOUT_MS = 25_000L
        const val STALL_TIMEOUT_MS = 30_000L
    }
}
