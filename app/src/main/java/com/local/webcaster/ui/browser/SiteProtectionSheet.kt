package com.local.webcaster.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteProtectionSheet(
    state: BrowserUiState,
    onDismiss: () -> Unit,
    onAds: (Boolean) -> Unit,
    onTrackers: (Boolean) -> Unit,
    onPopups: (Boolean) -> Unit,
    onQuickCast: (Boolean) -> Unit,
    onDesktopMode: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val settings = state.sitePreferences
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Protection pour ce site", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(domain(state.currentUrl), style = MaterialTheme.typography.bodySmall)
                }
                Text(if (state.blockedCount > 0) "${state.blockedCount} bloques" else "Aucun blocage", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                PreferenceToggle("Publicites", "Bloque les regies et redirections publicitaires.", settings.ads, onAds)
                HorizontalDivider()
                PreferenceToggle("Trackers", "Limite analytics, pixels et telemetrie tiers.", settings.trackers, onTrackers)
                HorizontalDivider()
                PreferenceToggle("Popups", "Bloque les fenetres non sollicitees sur ce site.", settings.popups, onPopups)
            }
            Spacer(Modifier.height(14.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                PreferenceToggle("Quick Cast", "Autorise la selection automatique du meilleur media.", settings.quickCast, onQuickCast)
                HorizontalDivider()
                PreferenceToggle("Version ordinateur", "Utilise un navigateur de bureau pour ce site.", settings.desktopMode, onDesktopMode)
            }
            TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Rounded.Refresh, null)
                Text(" Reinitialiser ce site")
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PreferenceToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun domain(url: String): String = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
