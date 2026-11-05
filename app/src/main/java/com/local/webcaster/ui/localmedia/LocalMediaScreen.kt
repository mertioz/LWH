package com.local.webcaster.ui.localmedia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        Column(
            Modifier.widthIn(max = 320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.padding(20.dp).size(28.dp))
            }
            Text(
                "Votre galerie",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Choisissez les photos et vidéos à lire ou à diffuser sur votre télé.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onPickMedia) {
                Icon(Icons.Rounded.AddPhotoAlternate, null)
                Spacer(Modifier.size(8.dp))
                Text("Choisir des médias")
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
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${state.items.size} média${if (state.items.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalIconButton(onClick = onPickMedia) {
                    Icon(Icons.Rounded.AddPhotoAlternate, "Modifier la sélection")
                }
            }
        }
        item {
            Surface(
                color = Color(0xFF211E2B),
                shape = MaterialTheme.shapes.large,
            ) {
                Crossfade(
                    targetState = selected,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                    animationSpec = tween(160),
                    label = "Media preview",
                ) { item ->
                    Box(
                        modifier = Modifier.fillMaxSize().clickable(enabled = item.isVideo) { onLocalPlay(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        MediaPreview(item, Modifier.fillMaxSize(), ContentScale.Fit, 1600)
                        if (item.isVideo) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = .9f)) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    "Lire sur le téléphone",
                                    modifier = Modifier.padding(12.dp).size(28.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (state.items.size > 1) item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Précédent")
                }
                Text("${state.selectedIndex + 1} / ${state.items.size}", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Suivant")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    selected.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
        if (state.items.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.items, key = { _, item -> item.uri.toString() }) { index, item ->
                        val borderColor by animateColorAsState(
                            if (index == state.selectedIndex) MaterialTheme.colorScheme.primary else Color.Transparent,
                            label = "Selected thumbnail",
                        )
                        Surface(
                            onClick = { onSelect(index) },
                            modifier = Modifier.size(70.dp),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(2.dp, borderColor),
                        ) {
                            Box(Modifier.fillMaxSize().padding(4.dp)) {
                                MediaPreview(item, Modifier.fillMaxSize(), ContentScale.Crop, 240)
                                if (item.isVideo) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomEnd),
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surface,
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, null, Modifier.padding(2.dp).size(14.dp))
                                    }
                                }
                            }
                        }
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
                        AnimatedVisibility(state.slideshowEnabled) {
                            Column {
                                Text("Intervalle · ${state.slideshowIntervalSeconds} s", style = MaterialTheme.typography.bodySmall)
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
