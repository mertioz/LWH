package com.local.webcaster.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.webcaster.detection.MediaCandidate
import com.local.webcaster.detection.MediaType
import com.local.webcaster.cast.CastUiState
import com.local.webcaster.diagnostics.DiagnosticBuilder
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCandidateSheet(
    candidates: List<MediaCandidate>,
    castState: CastUiState,
    isRescanning: Boolean,
    onDismiss: () -> Unit,
    onRescan: () -> Unit,
    onCast: (MediaCandidate) -> Unit,
    onCastViaRelay: (MediaCandidate) -> Unit,
    onPlayNext: (MediaCandidate) -> Unit,
    onAddToQueue: (MediaCandidate) -> Unit,
    onLocalPlay: (MediaCandidate) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Tv, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Videos detectees", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (isRescanning) "Analyse en cours..." else
                        "${candidates.size} source${if (candidates.size > 1) "s" else ""} disponible${if (candidates.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRescan, enabled = !isRescanning) {
                if (isRescanning) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, "Reanalyser les medias")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(candidates, key = { it.id }) { candidate ->
                CandidateCard(
                    candidate, castState, onCast, onCastViaRelay, onPlayNext, onAddToQueue,
                    onLocalPlay, Modifier.padding(horizontal = 12.dp)
                )
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CandidateCard(
    candidate: MediaCandidate,
    castState: CastUiState,
    onCast: (MediaCandidate) -> Unit,
    onCastViaRelay: (MediaCandidate) -> Unit,
    onPlayNext: (MediaCandidate) -> Unit,
    onAddToQueue: (MediaCandidate) -> Unit,
    onLocalPlay: (MediaCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var detailsVisible by remember(candidate.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val disabled = candidate.isDrm || candidate.unavailableReason != null
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                candidate.title?.takeIf(String::isNotBlank)
                    ?: candidate.host.takeIf(String::isNotBlank)
                    ?: "Media web",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetaChip(format(candidate))
                resolution(candidate)?.let { MetaChip(it) }
                candidate.bandwidth?.let { MetaChip(formatBitrate(it)) }
                candidate.durationMs?.takeIf { it > 0 }?.let { MetaChip(formatDuration(it)) }
                if (candidate.subtitleTracks.isNotEmpty()) MetaChip("${candidate.subtitleTracks.size} SUB")
                MetaChip(if (candidate.isLive) "LIVE" else if (candidate.isMasterPlaylist) "AUTO" else "VOD")
            }
            if (candidate.host.isNotBlank()) {
                Text(
                    candidate.host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            candidate.codecs?.takeIf(String::isNotBlank)?.let {
                Text("Codec: $it", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val warning = when {
                candidate.isDrm -> "Flux protege par DRM: le casting generique est desactive."
                candidate.unavailableReason != null -> candidate.unavailableReason
                candidate.requiredHeaders.isNotEmpty() -> "Un relay local securise sera essaye si l'acces direct echoue."
                else -> null
            }
            warning?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    if (disabled) Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (disabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (detailsVisible) {
                SelectionContainer {
                    Text(
                        candidate.resolvedUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onCastViaRelay(candidate) }, enabled = !disabled) {
                    Text("Caster via le telephone")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onPlayNext(candidate) }, enabled = !disabled && castState.hasMedia) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Text(" Lire ensuite")
                    }
                    TextButton(onClick = { onAddToQueue(candidate) }, enabled = !disabled && castState.hasMedia) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null)
                        Text(" File")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onLocalPlay(candidate) }, enabled = !disabled) {
                        Text("Lire sur le telephone")
                    }
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("CASTER diagnostic", DiagnosticBuilder.build(candidate, castState))
                        )
                    }) { Text("Copier diagnostic") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { detailsVisible = !detailsVisible }) {
                    Text(if (detailsVisible) "Masquer" else "Details")
                    Icon(if (detailsVisible) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
                Button(onClick = { onCast(candidate) }, enabled = !disabled) {
                    Icon(Icons.Rounded.Cast, null)
                    Text(" Caster")
                }
            }
        }
    }
}

@Composable
private fun MetaChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

private fun format(candidate: MediaCandidate): String = when (candidate.mediaType) {
    MediaType.HLS -> "HLS"
    MediaType.DASH -> "DASH"
    MediaType.MP4 -> "MP4"
    MediaType.WEBM -> "WEBM"
    MediaType.AUDIO -> "AUDIO"
    MediaType.IMAGE -> "IMAGE"
    MediaType.BLOB -> "MEDIASOURCE"
    MediaType.VIDEO -> "VIDEO"
    MediaType.UNKNOWN -> "MEDIA"
}

private fun resolution(candidate: MediaCandidate): String? = when {
    candidate.width != null && candidate.height != null -> "${candidate.width}x${candidate.height}"
    candidate.height != null -> "${candidate.height}p"
    else -> null
}

private fun formatBitrate(bitsPerSecond: Long): String = if (bitsPerSecond >= 1_000_000) {
    String.format(Locale.US, "%.1f Mb/s", bitsPerSecond / 1_000_000.0)
} else {
    "${bitsPerSecond / 1_000} kb/s"
}

private fun formatDuration(milliseconds: Long): String {
    val total = milliseconds / 1_000
    val hours = total / 3_600
    val minutes = total / 60 % 60
    val seconds = total % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%d:%02d", minutes, seconds)
}
