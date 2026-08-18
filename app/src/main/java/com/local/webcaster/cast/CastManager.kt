package com.local.webcaster.cast

import android.app.Activity
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack
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
    val domain: String? = null,
    val artworkUrl: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 1f,
    val receiverState: String = "idle",
    val deliveryMode: String = "none",
    val relayState: String = "stopped",
    val fallbackReason: String? = null,
    val startupTimeMs: Long? = null,
    val lastHttpStatus: Int? = null,
    val subtitles: List<CastSubtitle> = emptyList(),
    val queue: List<CastQueueEntry> = emptyList(),
    val message: String? = null,
) {
    val showController: Boolean
        get() = hasMedia && (connected || reconnecting)
}

data class CastSubtitle(
    val id: Long,
    val label: String,
    val language: String? = null,
    val active: Boolean = false,
)

data class CastQueueEntry(
    val itemId: Int,
    val title: String,
    val domain: String? = null,
    val current: Boolean = false,
    val queueIndex: Int = 0,
)

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
    private val knownCandidates = linkedMapOf<String, MediaCandidate>()
    private var loadStartedAtElapsed = 0L

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
            adoptRemoteContentIfStable()
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
                if (status.loadingItemId != MediaQueueItem.INVALID_ITEM_ID) {
                    updateRemoteState()
                    return
                }
                if (status.idleReason == MediaStatus.IDLE_REASON_ERROR) {
                    if (!tryRelayAfterDirectFailure("receiver_error")) {
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
        override fun onQueueStatusUpdated() = updateRemoteState()
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
        rememberCandidate(candidate)
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

    fun setVolume(volume: Float) {
        runCatching {
            sessionManager?.currentCastSession?.setVolume(volume.coerceIn(0f, 1f).toDouble())
            _state.value = _state.value.copy(volume = volume.coerceIn(0f, 1f))
        }.onFailure { message("Le volume de l'appareil Cast n'a pas pu etre modifie.") }
    }

    fun setSubtitle(trackId: Long?) {
        val client = registeredRemote ?: return
        client.setActiveMediaTracks(trackId?.let { longArrayOf(it) } ?: longArrayOf())
            .setResultCallback { result ->
                if (!result.status.isSuccess) message("La piste de sous-titres n'a pas pu etre activee.")
            }
    }

    fun playNext(candidate: MediaCandidate) = enqueue(candidate, playNext = true)

    fun addToQueue(candidate: MediaCandidate) = enqueue(candidate, playNext = false)

    fun removeQueueItem(itemId: Int) {
        registeredRemote?.queueRemoveItem(itemId, null)
    }

    fun clearUpcomingQueue() {
        val current = registeredRemote?.mediaStatus?.currentItemId ?: return
        val ids = _state.value.queue.filterNot { it.itemId == current }.map { it.itemId }.toIntArray()
        if (ids.isNotEmpty()) registeredRemote?.queueRemoveItems(ids, null)
    }

    fun playQueueItem(itemId: Int) {
        registeredRemote?.queueJumpToItem(itemId, null)
    }

    fun moveQueueItem(itemId: Int, newIndex: Int) {
        registeredRemote?.queueMoveItemToNewIndex(itemId, newIndex.coerceAtLeast(0), null)
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
        val previousState = _state.value
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
        loadStartedAtElapsed = 0L
        loadSequence++
        knownCandidates.clear()
        _state.value = CastUiState(
            frameworkAvailable = castContext != null,
            devicesAvailable = castContext?.castState != CastState.NO_DEVICES_AVAILABLE,
            deliveryMode = previousState.deliveryMode,
            relayState = "stopped",
            fallbackReason = previousState.fallbackReason,
            startupTimeMs = previousState.startupTimeMs,
            lastHttpStatus = previousState.lastHttpStatus,
            message = message,
        )
    }

    private fun load(candidate: MediaCandidate, overrideUrl: String? = null, startPositionMs: Long = 0) {
        val client = registeredRemote ?: return
        val sequence = ++loadSequence
        pendingCandidate = null
        pendingForceRelay = false
        activeCandidate = candidate
        rememberCandidate(candidate)
        activeLoadRelayed = overrideUrl != null
        activeContentId = overrideUrl ?: candidate.resolvedUrl
        playbackStarted = false
        loadStartedAtElapsed = SystemClock.elapsedRealtime()
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
            domain = candidate.host.takeIf(String::isNotBlank),
            artworkUrl = candidate.posterUrl,
            playing = false,
            buffering = true,
            positionMs = startPositionMs,
            durationMs = 0,
            deliveryMode = if (overrideUrl == null) "direct" else "relay",
            relayState = if (overrideUrl == null) "stopped" else "running",
            receiverState = "loading",
            startupTimeMs = null,
            fallbackReason = if (overrideUrl == null) null else _state.value.fallbackReason,
            lastHttpStatus = candidate.lastHttpStatus,
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
                if (!tryRelayAfterDirectFailure("direct_rejected_${result.status.statusCode}")) {
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

    private fun tryRelayAfterDirectFailure(reason: String = "direct_load_failed"): Boolean {
        val candidate = activeCandidate ?: return false
        if (relayAttempted || candidate.isDrm || candidate.unavailableReason != null) {
            return relayRetryInFlight
        }
        _state.value = _state.value.copy(fallbackReason = reason.take(200), relayState = "starting")
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
        _state.value = _state.value.copy(relayState = "starting")
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
        adoptRemoteContentIfStable()
        val status = client.mediaStatus
        val statusMatchesLoad = activeContentId == null || client.mediaInfo?.contentId == activeContentId
        val playerState = status?.playerState
        val remoteContentId = client.mediaInfo?.contentId
        knownCandidates[remoteContentId]?.let { activeCandidate = it }
        if (statusMatchesLoad &&
            (playerState == MediaStatus.PLAYER_STATE_PLAYING || playerState == MediaStatus.PLAYER_STATE_PAUSED)
        ) {
            playbackStarted = true
            loadInFlight = false
            relayRetryInFlight = false
            playbackWatchdogJob?.cancel()
            if (_state.value.startupTimeMs == null && loadStartedAtElapsed > 0L) {
                _state.value = _state.value.copy(
                    startupTimeMs = (SystemClock.elapsedRealtime() - loadStartedAtElapsed).coerceAtLeast(0L)
                )
            }
        }
        val pendingPlayback = activeCandidate != null &&
            (!statusMatchesLoad || playbackWatchdogJob?.isActive == true)
        val metadata = client.mediaInfo?.metadata
        val activeTrackIds = status?.activeTrackIds?.toSet().orEmpty()
        val subtitles = client.mediaInfo?.mediaTracks.orEmpty()
            .filter { it.type == MediaTrack.TYPE_TEXT }
            .map { track ->
                CastSubtitle(
                    id = track.id,
                    label = track.name?.takeIf(String::isNotBlank)
                        ?: track.language?.takeIf(String::isNotBlank)
                        ?: "Subtitles",
                    language = track.language,
                    active = track.id in activeTrackIds,
                )
            }
        val currentItem = status?.currentItemId ?: MediaQueueItem.INVALID_ITEM_ID
        val allQueueItems = status?.queueItems.orEmpty()
        val currentQueueIndex = allQueueItems.indexOfFirst { it.itemId == currentItem }.coerceAtLeast(0)
        val queue = allQueueItems.drop(currentQueueIndex).mapIndexed { offset, item ->
            val itemMetadata = item.media?.metadata
            CastQueueEntry(
                itemId = item.itemId,
                title = itemMetadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE)
                    ?: "Media web",
                domain = itemMetadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE),
                current = item.itemId == currentItem,
                queueIndex = currentQueueIndex + offset,
            )
        }
        val receiverState = when (playerState) {
            MediaStatus.PLAYER_STATE_PLAYING -> "playing"
            MediaStatus.PLAYER_STATE_PAUSED -> "paused"
            MediaStatus.PLAYER_STATE_BUFFERING -> "buffering"
            MediaStatus.PLAYER_STATE_LOADING -> "loading"
            else -> "idle"
        }
        _state.value = _state.value.copy(
            connected = true,
            reconnecting = false,
            hasMedia = statusMatchesLoad && status != null && status.playerState != MediaStatus.PLAYER_STATE_IDLE || pendingPlayback,
            title = if (statusMatchesLoad) {
                metadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE)
                    ?: _state.value.title
            } else _state.value.title,
            domain = metadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE)
                ?: activeCandidate?.host?.takeIf(String::isNotBlank),
            artworkUrl = activeCandidate?.posterUrl ?: _state.value.artworkUrl,
            playing = statusMatchesLoad && status?.playerState == MediaStatus.PLAYER_STATE_PLAYING,
            buffering = statusMatchesLoad && status?.playerState == MediaStatus.PLAYER_STATE_BUFFERING || pendingPlayback,
            positionMs = if (statusMatchesLoad) client.approximateStreamPosition.coerceAtLeast(0) else _state.value.positionMs,
            durationMs = if (statusMatchesLoad) client.streamDuration.coerceAtLeast(0) else _state.value.durationMs,
            volume = runCatching { sessionManager?.currentCastSession?.volume?.toFloat() }.getOrNull()
                ?: _state.value.volume,
            receiverState = receiverState,
            subtitles = subtitles,
            queue = queue,
            lastHttpStatus = relay.lastStatusCode ?: activeCandidate?.lastHttpStatus,
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
        loadStartedAtElapsed = 0L
        knownCandidates.clear()
        _state.value = _state.value.copy(
            hasMedia = false,
            title = null,
            playing = false,
            buffering = false,
            positionMs = 0,
            durationMs = 0,
            domain = null,
            artworkUrl = null,
            receiverState = "idle",
            relayState = "stopped",
            subtitles = emptyList(),
            queue = emptyList(),
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
            if (!relayed && tryRelayAfterDirectFailure(reason)) return@launch
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

    private fun enqueue(candidate: MediaCandidate, playNext: Boolean) {
        if (candidate.isDrm || candidate.unavailableReason != null) {
            message(candidate.unavailableReason ?: "Ce flux protege ne peut pas etre ajoute a la file.")
            return
        }
        val client = registeredRemote
        if (client == null || !_state.value.hasMedia) {
            cast(candidate)
            return
        }
        rememberCandidate(candidate)
        val item = MediaQueueItem.Builder(CastMediaLoader.mediaInfo(candidate))
            .setAutoplay(true)
            .setPreloadTime(10.0)
            .build()
        val request = if (playNext) {
            val status = client.mediaStatus
            val items = status?.queueItems.orEmpty()
            val currentIndex = items.indexOfFirst { it.itemId == status?.currentItemId }
            val beforeId = items.getOrNull(currentIndex + 1)?.itemId ?: MediaQueueItem.INVALID_ITEM_ID
            client.queueInsertItems(arrayOf(item), beforeId, null)
        } else {
            client.queueAppendItem(item, null)
        }
        request.setResultCallback { result ->
            if (result.status.isSuccess) message(if (playNext) "Ajoute a Lire ensuite." else "Ajoute a la file Cast.")
            else message("Impossible d'ajouter ce media a la file Cast.")
        }
    }

    private fun adoptRemoteContentIfStable() {
        if (loadInFlight || relayRetryInFlight || playbackWatchdogJob?.isActive == true) return
        val remoteContentId = registeredRemote?.mediaInfo?.contentId ?: return
        if (remoteContentId == activeContentId) return
        if (activeContentId == null && relay.isRunning && activeCandidate != null) {
            activeContentId = remoteContentId
            activeLoadRelayed = true
            _state.value = _state.value.copy(deliveryMode = "relay", relayState = "running")
            return
        }
        activeContentId = remoteContentId
        activeCandidate = knownCandidates[remoteContentId]
        if (relay.isRunning) relay.stop()
        activeLoadRelayed = false
        playbackStarted = false
        loadStartedAtElapsed = 0L
        _state.value = _state.value.copy(
            deliveryMode = "direct",
            relayState = "stopped",
            fallbackReason = null,
            startupTimeMs = null,
        )
    }

    private fun rememberCandidate(candidate: MediaCandidate) {
        knownCandidates[candidate.resolvedUrl] = candidate
        while (knownCandidates.size > MAX_KNOWN_CANDIDATES) {
            knownCandidates.remove(knownCandidates.keys.first())
        }
    }

    private companion object {
        const val DIRECT_START_TIMEOUT_MS = 18_000L
        const val RELAY_START_TIMEOUT_MS = 25_000L
        const val STALL_TIMEOUT_MS = 30_000L
        const val MAX_KNOWN_CANDIDATES = 32
    }
}
