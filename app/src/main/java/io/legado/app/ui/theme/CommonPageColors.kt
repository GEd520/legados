package io.legado.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.legado.app.constant.EventBus
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.utils.eventObservable

data class PageTopBarColors(
    val containerColor: Color,
    val contentColor: Color
)

@Composable
fun pageTopBarContainerColor(): Color {
    return pageTopBarColors().containerColor
}

@Composable
fun pageTopBarContentColor(): Color {
    return pageTopBarColors().contentColor
}

@Composable
fun pageTopBarColors(): PageTopBarColors {
    rememberTopBarConfigVersion()
    val context = LocalContext.current
    val primaryColor = ThemeStore.primaryColor(context)
    val alphaPercent = pageTopBarOpacityPercent()
    val containerColor = TopBarConfig.withOpacity(primaryColor, alphaPercent)
    return PageTopBarColors(
        containerColor = Color(containerColor),
        contentColor = Color(context.primaryTextColor)
    )
}

@Composable
private fun rememberTopBarConfigVersion(): Int {
    val lifecycleOwner = LocalLifecycleOwner.current
    var version by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = Observer<Boolean> { isNightMode ->
            if (isNightMode == AppConfig.isNightTheme) {
                version += 1
            }
        }
        val observable = eventObservable<Boolean>(EventBus.TOP_BAR_CHANGED)
        observable.observe(lifecycleOwner, observer)
        onDispose {
            observable.removeObserver(observer)
        }
    }
    return version
}

@Composable
fun pageCardContainerColor(): Color {
    return MaterialTheme.colorScheme.surfaceVariant
}

@Composable
fun pageCardElevatedContainerColor(): Color {
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
fun pageHeaderContainerColor(): Color {
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
fun pageSecondaryTextColor(): Color {
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
fun pageAccentColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    return if (background.luminance() < 0.18f) {
        lerp(primary, Color.White, 0.2f)
    } else {
        primary
    }
}

@Composable
fun pageSurfaceVariantColor(): Color {
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
fun pageMutedIconTint(): Color {
    val background = MaterialTheme.colorScheme.background
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    return if (background.luminance() < 0.18f) {
        lerp(onSurfaceVariant, onSurface, 0.24f).copy(alpha = 0.78f)
    } else {
        onSurfaceVariant.copy(alpha = 0.5f)
    }
}

@Composable
private fun pageTopBarOpacityPercent(): Int {
    val context = LocalContext.current
    val config = TopBarConfig.currentConfig(context, AppConfig.isNightTheme)
    return if (config.style == TopBarConfig.STYLE_REGULAR) {
        config.wallpaperAlpha
    } else {
        config.tagBarAlpha
    }
}
