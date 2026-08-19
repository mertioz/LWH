package com.local.webcaster.localmedia

import android.app.Application
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.webcaster.LocalWebCasterApp
import com.local.webcaster.detection.MediaCandidate
import com.local.webcaster.detection.MediaType
import com.local.webcaster.detection.SourceType
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalMediaItem(
    val uri: Uri,
    val title: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val rotation: Int? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val castWarning: String? = null,
    val candidate: MediaCandidate,
) {
    val isPhoto: Boolean get() = mimeType.startsWith("image/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

data class LocalMediaUiState(
    val items: List<LocalMediaItem> = emptyList(),
    val selectedIndex: Int = 0,
    val loading: Boolean = false,
    val slideshowEnabled: Boolean = false,
    val slideshowIntervalSeconds: Int = 8,
) {
    val selected: LocalMediaItem? get() = items.getOrNull(selectedIndex)
    val photoCount: Int get() = items.count(LocalMediaItem::isPhoto)
}

class LocalMediaViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LocalWebCasterApp
    private val _state = MutableStateFlow(LocalMediaUiState())
    val state: StateFlow<LocalMediaUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun setSelection(uris: List<Uri>) {
        val selected = uris.distinctBy(Uri::toString).take(MAX_ITEMS)
        app.mediaRelay.setSelectedLocalMedia(selected.map(Uri::toString).toSet())
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, slideshowEnabled = false) }
            val items = withContext(Dispatchers.IO) { selected.mapNotNull(::readItem) }
            _state.value = LocalMediaUiState(items = items)
        }
    }

    fun select(index: Int) {
        _state.update { state -> state.copy(selectedIndex = index.coerceIn(0, (state.items.size - 1).coerceAtLeast(0))) }
    }

    fun move(offset: Int, photosOnly: Boolean = false): LocalMediaItem? {
        val current = _state.value
        if (current.items.isEmpty()) return null
        val eligible = current.items.indices.filter { !photosOnly || current.items[it].isPhoto }
        if (eligible.isEmpty()) return null
        val position = eligible.indexOf(current.selectedIndex).takeIf { it >= 0 } ?: 0
        val nextIndex = eligible[(position + offset).mod(eligible.size)]
        _state.value = current.copy(selectedIndex = nextIndex)
        return current.items[nextIndex]
    }

    fun setSlideshowEnabled(enabled: Boolean) {
        _state.update { it.copy(slideshowEnabled = enabled && it.photoCount > 1) }
    }

    fun setSlideshowInterval(seconds: Int) {
        _state.update { it.copy(slideshowIntervalSeconds = seconds.coerceIn(3, 30)) }
    }

    fun clear() {
        loadJob?.cancel()
        app.mediaRelay.setSelectedLocalMedia(emptySet())
        _state.value = LocalMediaUiState()
    }

    private fun readItem(uri: Uri): LocalMediaItem? = runCatching {
        val resolver = app.contentResolver
        var title: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                title = cursor.getString(0)?.takeIf(String::isNotBlank)
                size = if (cursor.isNull(1)) null else cursor.getLong(1).takeIf { it >= 0 }
            }
        }
        val retriever = MediaMetadataRetriever()
        var metadata = runCatching {
            retriever.setDataSource(app, uri)
            RetrievedMetadata(
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull(),
            )
        }.getOrDefault(RetrievedMetadata())
        retriever.release()
        val mime = (resolver.getType(uri) ?: metadata.mimeType ?: mimeFromName(title)).lowercase()
        if (!mime.startsWith("image/") && !mime.startsWith("video/")) return@runCatching null
        if (mime.startsWith("video/")) {
            val codecs = readCodecs(uri)
            metadata = metadata.copy(videoCodec = codecs.first, audioCodec = codecs.second)
        }
        val imageSize = if (mime.startsWith("image/")) readImageSize(uri) else null
        val width = metadata.width ?: imageSize?.first
        val height = metadata.height ?: imageSize?.second
        val mediaType = when {
            mime.startsWith("image/") -> MediaType.IMAGE
            mime == "video/mp4" -> MediaType.MP4
            mime == "video/webm" -> MediaType.WEBM
            else -> MediaType.VIDEO
        }
        val warning = compatibilityWarning(mime)
        val safeTitle = title ?: if (mime.startsWith("image/")) "Photo locale" else "Video locale"
        val candidate = MediaCandidate(
            id = UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString(),
            url = uri.toString(),
            resolvedUrl = uri.toString(),
            pageUrl = "",
            title = safeTitle,
            mimeType = mime,
            mediaType = mediaType,
            sourceType = SourceType.LOCAL_PICKER,
            host = "Media local",
            width = width,
            height = height,
            codecs = listOfNotNull(metadata.videoCodec, metadata.audioCodec).joinToString(", ").ifBlank { null },
            unavailableReason = warning,
            relayRequired = true,
            confidence = 100,
            durationMs = metadata.durationMs,
        )
        LocalMediaItem(
            uri = uri,
            title = safeTitle,
            mimeType = mime,
            sizeBytes = size,
            durationMs = metadata.durationMs,
            width = width,
            height = height,
            rotation = metadata.rotation,
            videoCodec = metadata.videoCodec,
            audioCodec = metadata.audioCodec,
            castWarning = warning,
            candidate = candidate,
        )
    }.getOrNull()

    private fun readImageSize(uri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        val orientation = app.contentResolver.openInputStream(uri)?.use { input ->
            runCatching { ExifInterface(input).rotationDegrees }.getOrDefault(0)
        } ?: 0
        return if (orientation == 90 || orientation == 270) options.outHeight to options.outWidth
        else options.outWidth to options.outHeight
    }

    private fun readCodecs(uri: Uri): Pair<String?, String?> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(app, uri, null)
            var video: String? = null
            var audio: String? = null
            repeat(extractor.trackCount) { index ->
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true && video == null) video = mime
                if (mime?.startsWith("audio/") == true && audio == null) audio = mime
            }
            video to audio
        } catch (_: Exception) {
            null to null
        } finally {
            extractor.release()
        }
    }

    private data class RetrievedMetadata(
        val mimeType: String? = null,
        val durationMs: Long? = null,
        val width: Int? = null,
        val height: Int? = null,
        val rotation: Int? = null,
        val videoCodec: String? = null,
        val audioCodec: String? = null,
    )

    private companion object {
        const val MAX_ITEMS = 100

        fun compatibilityWarning(mime: String): String? = when {
            mime in setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp") -> null
            mime.startsWith("image/") -> "Ce format d'image n'est pas pris en charge par le receiver Cast."
            mime in setOf("video/mp4", "video/webm") -> null
            mime.startsWith("video/") -> "Ce conteneur video peut ne pas etre pris en charge par le receiver Cast."
            else -> "Format local non pris en charge."
        }

        fun mimeFromName(name: String?): String = when (name?.substringAfterLast('.', "")?.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            else -> "application/octet-stream"
        }
    }
}
