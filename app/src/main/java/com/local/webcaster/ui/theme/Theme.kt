package com.local.webcaster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF5139C5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E3FF),
    onPrimaryContainer = Color(0xFF211259),
    secondaryContainer = Color(0xFFEDE9F5),
    onSecondaryContainer = Color(0xFF292337),
    background = Color(0xFFFBF9FF),
    onBackground = Color(0xFF1C1924),
    surface = Color(0xFFFBF9FF),
    onSurface = Color(0xFF1C1924),
    onSurfaceVariant = Color(0xFF5D576D),
    surfaceContainerLow = Color(0xFFF5F1FA),
    surfaceContainer = Color(0xFFF0EBF7),
    surfaceContainerHigh = Color(0xFFEAE5F2),
    outlineVariant = Color(0xFFDCD5E6),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFC8BFFF),
    onPrimary = Color(0xFF261865),
    primaryContainer = Color(0xFF382777),
    onPrimaryContainer = Color(0xFFEDE7FF),
    secondaryContainer = Color(0xFF2F293D),
    onSecondaryContainer = Color(0xFFE9E2F4),
    background = Color(0xFF15131B),
    onBackground = Color(0xFFF2EDF9),
    surface = Color(0xFF15131B),
    onSurface = Color(0xFFF2EDF9),
    onSurfaceVariant = Color(0xFFC9C1D6),
    surfaceContainerLow = Color(0xFF1C1924),
    surfaceContainer = Color(0xFF24202E),
    surfaceContainerHigh = Color(0xFF2D2839),
    outlineVariant = Color(0xFF484153),
)

private val CasterShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun LocalWebCasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = CasterShapes,
        content = content,
    )
}
