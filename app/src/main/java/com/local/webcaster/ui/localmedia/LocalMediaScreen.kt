package com.local.webcaster.ui.localmedia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.local.webcaster.cast.CastUiState
import com.local.webcaster.localmedia.LocalMediaItem
import com.local.webcaster.localmedia.LocalMediaUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun LocalMediaScreen(
    state: LocalMediaUiState,
    castState: CastUiState,
    onPickMedia: () -> Unit,
    onSelect: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCastNow: (LocalMediaItem) -> Unit,
    onLocalPlay: (LocalMediaItem) -> Unit,
    onPlayNext: (LocalMediaItem) -> Unit,
    onAddToQueue: (LocalMediaItem) -> Unit,
    onSlideshowEnabled: (Boolean) -> Unit,
    onSlideshowInterval: (Int) -> Unit,
    onSlideshowNext: () -> Unit,
) {
    LaunchedEffect(
        state.slideshowEnabled,
        state.slideshowIntervalSeconds,
        state.selectedIndex,
        state.items,
    ) {
        if (state.slideshowEnabled && state.photoCount > 1) {
            delay(state.slideshowIntervalSeconds * 1_000L)
            onSlideshowNext()
        }
    }

    Surface(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> EmptyLocalMedia(onPickMedia)
            else -> LocalGallery(
                state = state,
                castState = castState,
                onPickMedia = onPickMedia,
                onSelect = onSelect,
                onPrevious = onPrevious,
                onNext = onNext,
                onCastNow = onCastNow,
                onLocalPlay = onLocalPlay,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onSlideshowEnabled = onSlideshowEnabled,
                onSlideshowInterval = onSlideshowInterval,
            )
        }
    }
}

@Composable
private fun EmptyLocalMedia(onPickMedia: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                Icons.Rounded.AddPhotoAlternate,
                null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Photos et videos locales", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Selectionnez uniquement les medias a afficher ou caster. Aucun acces global au stockage n'est demande.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onPickMedia) {
                Icon(Icons.Rounded.AddPhotoAlternate, null)
                Spacer(Modifier.size(8.dp))
                Text("Choisir des medias")
            }
        }
    }
}

@Composable
private fun LocalGallery(
    state: LocalMediaUiState,
    castState: CastUiState,
    onPickMedia: () -> Unit,
    onSelect: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCastNow: (LocalMediaItem) -> Unit,
    onLocalPlay: (LocalMediaItem) -> Unit,
    onPlayNext: (LocalMediaItem) -> Unit,
    onAddToQueue: (LocalMediaItem) -> Unit,
    onSlideshowEnabled: (Boolean) -> Unit,
    onSlideshowInterval: (Int) -> Unit,
) {
    val selected = state.selected ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Medias locaux", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${state.items.size} element${if (state.items.size > 1) "s" else ""}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onPickMedia) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Modifier")
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(22.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f).clickable(enabled = selected.isVideo) {
                        onLocalPlay(selected)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    MediaPreview(selected, Modifier.fillMaxSize(), ContentScale.Fit, 1600)
                    if (selected.isVideo) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = .9f)) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                "Lire sur le telephone",
                                modifier = Modifier.padding(12.dp).size(36.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPrevious, enabled = state.items.size > 1) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Precedent")
                }
                Text("${state.selectedIndex + 1} / ${state.items.size}", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onNext, enabled = state.items.size > 1) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Suivant")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(selected.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    mediaDetails(selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                selected.castWarning?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onCastNow(selected) },
                    enabled = selected.castWarning == null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Cast, null)
                    Spacer(Modifier.size(7.dp))
                    Text("Caster")
                }
                if (selected.isVideo) {
                    FilledTonalButton(onClick = { onLocalPlay(selected) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.size(7.dp))
                        Text("Lire ici")
                    }
                }
            }
        }
        if (selected.isVideo && castState.connected && castState.hasMedia && selected.castWarning == null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onPlayNext(selected) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.QueuePlayNext, null)
                        Spacer(Modifier.size(6.dp))
                        Text("Lire ensuite")
                    }
                    OutlinedButton(onClick = { onAddToQueue(selected) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null)
                        Spacer(Modifier.size(6.dp))
                        Text("File Cast")
                    }
                }
            }
        }
        if (state.photoCount > 1) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Slideshow, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(10.dp))
                            Text("Diaporama", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Switch(checked = state.slideshowEnabled, onCheckedChange = onSlideshowEnabled)
                        }
                        Text("Intervalle : ${state.slideshowIntervalSeconds} s", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = state.slideshowIntervalSeconds.toFloat(),
                            onValueChange = { onSlideshowInterval(it.roundToInt()) },
                            valueRange = 3f..30f,
                            steps = 26,
                        )
                    }
                }
            }
        }
        item {
            Text("Selection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(state.items, key = { _, item -> item.uri.toString() }) { index, item ->
                    val selectedItem = index == state.selectedIndex
                    Card(
                        modifier = Modifier.size(width = 112.dp, height = 92.dp).clickable { onSelect(index) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedItem) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            MediaPreview(item, Modifier.fillMaxSize(), ContentScale.Crop, 320)
                            Icon(
                                if (item.isPhoto) Icons.Rounded.Image else Icons.Rounded.VideoFile,
                                null,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPreview(item: LocalMediaItem, modifier: Modifier, contentScale: ContentScale, maxSize: Int) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, item.uri, maxSize) {
        value = withContext(Dispatchers.IO) { loadPreview(context, item.uri, item.isVideo, maxSize) }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), item.title, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.BrokenImage, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun loadPreview(context: Context, uri: Uri, video: Boolean, maxSize: Int): Bitmap? = runCatching {
    if (video) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSize, maxSize)
                    ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
                    val scale = (maxSize.toFloat() / maxOf(frame.width, frame.height)).coerceAtMost(1f)
                    if (scale == 1f) frame else Bitmap.createScaledBitmap(
                        frame,
                        (frame.width * scale).roundToInt().coerceAtLeast(1),
                        (frame.height * scale).roundToInt().coerceAtLeast(1),
                        true,
                    ).also { frame.recycle() }
                }
            }
        } finally {
            retriever.release()
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = (maxSize.toFloat() / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxSize * 2) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val rotation = context.contentResolver.openInputStream(uri)?.use {
            runCatching { ExifInterface(it).rotationDegrees }.getOrDefault(0)
        } ?: 0
        if (rotation == 0) decoded else Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true,
        ).also { if (it !== decoded) decoded.recycle() }
    }
}.getOrNull()

private fun mediaDetails(item: LocalMediaItem): String = buildList {
    add(item.mimeType)
    item.durationMs?.let { add(formatDuration(it)) }
    if (item.width != null && item.height != null) add("${item.width} x ${item.height}")
    item.sizeBytes?.let { add(formatSize(it)) }
    item.videoCodec?.substringAfterLast('/')?.let { add(it.uppercase()) }
}.joinToString("  •  ")

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f Go".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f Mo".format(bytes / 1_048_576.0)
    else -> "%.0f Ko".format(bytes / 1_024.0)
}
