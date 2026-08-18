package com.local.webcaster.ui.cast

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.webcaster.cast.CastQueueEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastQueueSheet(
    queue: List<CastQueueEntry>,
    onDismiss: () -> Unit,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("File Cast", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("En cours et elements a venir", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClear, enabled = queue.count { !it.current } > 0) { Text("Vider la suite") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding()) {
            itemsIndexed(queue, key = { _, item -> item.itemId }) { index, item ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.current) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                ) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onPlay(item.itemId) }, enabled = !item.current) {
                            Icon(Icons.Rounded.PlayArrow, if (item.current) "En cours" else "Lire")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(if (item.current) "Lecture en cours" else item.domain.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(
                            onClick = { onMove(item.itemId, queue[index - 1].queueIndex) },
                            enabled = index > 0 && !item.current && !queue[index - 1].current,
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Monter")
                        }
                        IconButton(
                            onClick = { onMove(item.itemId, queue[index + 1].queueIndex) },
                            enabled = index < queue.lastIndex && !item.current,
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Descendre")
                        }
                        IconButton(onClick = { onRemove(item.itemId) }) {
                            Icon(Icons.Rounded.DeleteOutline, "Retirer")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
