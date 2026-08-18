package com.local.webcaster.ui.cast

import android.graphics.BitmapFactory
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.webcaster.cast.CastUiState
import com.local.webcaster.security.PublicNetworkDns
import com.local.webcaster.security.BoundedBodyReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun CastMiniController(
    state: CastUiState,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Float) -> Unit,
    onSubtitle: (Long?) -> Unit,
    onQueue: () -> Unit,
    onOpenExpanded: () -> Unit,
    onStop: () -> Unit,
) {
    var slider by remember(state.title) { mutableFloatStateOf(state.positionMs.toFloat()) }
    var volume by remember { mutableFloatStateOf(state.volume) }
    var subtitlesOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.positionMs) { slider = state.positionMs.toFloat() }
    LaunchedEffect(state.volume) { volume = state.volume }

    Surface(
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        shadowElevation = 12.dp,
        tonalElevation = 4.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().animateContentSize().navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp).clickable(onClick = onOpenExpanded),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    when {
                        state.reconnecting -> CircularProgressIndicator(Modifier.padding(10.dp), strokeWidth = 2.dp)
                        state.artworkUrl != null -> RemoteArtwork(state.artworkUrl, Modifier.size(44.dp))
                        else -> Icon(Icons.Rounded.CastConnected, null, Modifier.padding(10.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable(onClick = onOpenExpanded)) {
                    Text(
                        if (state.reconnecting) "Reconnexion Cast..." else state.title ?: "Lecture sur la TV",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        listOfNotNull(state.domain, state.deviceName).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.queue.size > 1) {
                    IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "File Cast") }
                }
                if (state.subtitles.isNotEmpty()) {
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { subtitlesOpen = true }) {
                            Icon(Icons.Rounded.Subtitles, "Sous-titres")
                        }
                        DropdownMenu(expanded = subtitlesOpen, onDismissRequest = { subtitlesOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Desactives") },
                                onClick = { subtitlesOpen = false; onSubtitle(null) },
                            )
                            state.subtitles.forEach { track ->
                                DropdownMenuItem(
                                    text = { Text((if (track.active) "✓ " else "") + track.label) },
                                    onClick = { subtitlesOpen = false; onSubtitle(track.id) },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onToggle, enabled = state.connected && !state.buffering) {
                    Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pause" else "Lecture")
                }
                IconButton(onClick = onStop) { Icon(Icons.Rounded.StopCircle, "Arreter la diffusion") }
            }
            if (state.durationMs > 0) {
                Slider(
                    value = slider.coerceIn(0f, state.durationMs.toFloat()),
                    onValueChange = { slider = it },
                    onValueChangeFinished = { onSeek(slider.toLong()) },
                    valueRange = 0f..state.durationMs.toFloat(),
                    enabled = state.connected && !state.reconnecting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(state.positionMs), style = MaterialTheme.typography.labelSmall)
                Text(
                    when {
                        state.reconnecting -> "Reconnexion"
                        state.buffering -> "Chargement..."
                        state.durationMs > 0 -> formatTime(state.durationMs)
                        else -> "Direct"
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Volume", Modifier.size(18.dp))
                Slider(
                    value = volume.coerceIn(0f, 1f),
                    onValueChange = { volume = it },
                    onValueChangeFinished = { onVolume(volume) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                Text("${(volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RemoteArtwork(url: String, modifier: Modifier = Modifier) {
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).get().build()
                ArtworkHttp.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val source = response.body?.source() ?: return@use null
                    val bytes = BoundedBodyReader.read(source, 3_000_000) ?: return@use null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    if (image != null) Image(image!!, null, modifier, contentScale = ContentScale.Crop)
    else Icon(Icons.Rounded.CastConnected, null, modifier.padding(10.dp))
}

private object ArtworkHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(PublicNetworkDns)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
}

private fun formatTime(milliseconds: Long): String {
    val total = milliseconds / 1000
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
