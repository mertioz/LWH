package com.local.webcaster.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherSheet(
    tabs: List<BrowserTab>,
    activeTabId: String,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Onglets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${tabs.size} ouverts · 3 pages maximum gardees en memoire", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onNewTab) {
                Icon(Icons.Rounded.Add, null)
                Text(" Nouveau")
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tabs, key = BrowserTab::id) { tab ->
                Card(
                    onClick = { onSelect(tab.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (tab.id == activeTabId) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Language, null)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(domain(tab.url), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onClose(tab.id) }) {
                            Icon(Icons.Rounded.Close, "Fermer l'onglet")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun domain(url: String): String = runCatching { URI(url).host.orEmpty() }
    .getOrDefault("").ifBlank { "Nouvel onglet" }
