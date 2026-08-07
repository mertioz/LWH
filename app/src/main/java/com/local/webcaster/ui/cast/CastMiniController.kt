package com.local.webcaster.ui.cast

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.webcaster.cast.CastUiState
import java.util.Locale

@Composable
fun CastMiniController(
    state: CastUiState,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit,
) {
    var slider by remember(state.title) { mutableFloatStateOf(state.positionMs.toFloat()) }
    LaunchedEffect(state.positionMs) { slider = state.positionMs.toFloat() }

    Surface(
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        shadowElevation = 12.dp,
        tonalElevation = 4.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (state.reconnecting) {
                        CircularProgressIndicator(Modifier.padding(9.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.CastConnected, null, Modifier.padding(8.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.reconnecting) "Reconnexion Cast..." else state.title ?: "Lecture sur la TV",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        state.deviceName.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggle, enabled = state.connected && !state.buffering) {
                    Icon(
                        if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.playing) "Pause" else "Lecture",
                    )
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Rounded.StopCircle, "Arreter la diffusion")
                }
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
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val total = milliseconds / 1000
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
