package io.legado.app.ui.book.readRecord

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Composable
fun readRecordTopBarContainerColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val alpha = if (background.luminance() > 0.5f) 0.82f else 0.94f
    return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
}

@Composable
fun readRecordCardContainerColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val alpha = if (background.luminance() > 0.5f) 0.9f else 0.9f
    return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
}

@Composable
fun readRecordSummaryCardContainerColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    return if (background.luminance() < 0.18f) {
        lerp(surface, onSurface, 0.06f).copy(alpha = 0.98f)
    } else {
        surface.copy(alpha = 0.95f)
    }
}

@Composable
fun readRecordHeaderContainerColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    return if (background.luminance() < 0.18f) {
        lerp(surfaceVariant, onSurface, 0.08f).copy(alpha = 0.92f)
    } else {
        surfaceVariant.copy(alpha = 0.7f)
    }
}

@Composable
fun readRecordSecondaryTextColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    return if (background.luminance() < 0.18f) {
        lerp(onSurfaceVariant, onSurface, 0.32f)
    } else {
        onSurfaceVariant
    }
}

@Composable
fun readRecordTimelineAccentColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    return if (background.luminance() < 0.18f) {
        lerp(primary, Color.White, 0.2f)
    } else {
        primary
    }
}

@Composable
fun readRecordBookStackSurfaceColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    return if (background.luminance() < 0.18f) {
        lerp(surfaceVariant, onSurface, 0.08f)
    } else {
        surfaceVariant
    }
}

@Composable
fun readRecordMutedIconTint(): Color {
    val background = MaterialTheme.colorScheme.background
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    return if (background.luminance() < 0.18f) {
        lerp(onSurfaceVariant, onSurface, 0.24f).copy(alpha = 0.78f)
    } else {
        onSurfaceVariant.copy(alpha = 0.5f)
    }
}
