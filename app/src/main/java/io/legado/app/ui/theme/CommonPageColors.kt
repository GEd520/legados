package io.legado.app.ui.theme

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.elevation
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.eventObservable
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class PageTopBarColors(
    val containerColor: Color,
    val contentColor: Color,
    val cornerRadius: Dp = 0.dp,
    val shadowElevation: Dp = 0.dp,
    val wallpaperFile: File? = null,
    val wallpaperAlpha: Float = 1f
)

@Composable
fun pageTopBarColors(): PageTopBarColors {
    val topBarConfigVersion = rememberTopBarConfigVersion()
    val context = LocalContext.current
    val config = TopBarConfig.currentConfig(context, AppConfig.isNightTheme)
    val transparentNavBar = context.transparentNavBar
    val backgroundColor = if (config.style == TopBarConfig.STYLE_REGULAR) {
        TopBarConfig.resolveBackgroundColor(config)
    } else {
        config.tagBarColor ?: context.primaryColor
    }
    val alphaPercent = if (transparentNavBar) {
        0
    } else if (config.style == TopBarConfig.STYLE_REGULAR) {
        config.wallpaperAlpha
    } else {
        config.tagBarAlpha
    }
    val cornerRadius = if (config.style == TopBarConfig.STYLE_REGULAR) {
        val radiusPx = context.resources.getDimension(R.dimen.ui_panel_radius) *
            TopBarConfig.resolveCornerScale(config).coerceIn(0f, 3f)
        with(LocalDensity.current) { radiusPx.toDp() }
    } else {
        0.dp
    }
    val shadowElevation = when {
        transparentNavBar -> 0.dp
        config.style == TopBarConfig.STYLE_REGULAR && config.cornerScale != 0f -> 0.dp
        alphaPercent < 100 -> 0.1.dp
        else -> with(LocalDensity.current) { context.elevation.toDp() }
    }
    val containerColor = TopBarConfig.withOpacity(backgroundColor, alphaPercent)
    val contentColor = if (transparentNavBar) {
        val isBackgroundLight = Color(context.backgroundColor).luminance() > 0.5f
        Color(context.getPrimaryTextColor(isBackgroundLight))
    } else {
        Color(context.primaryTextColor)
    }
    return PageTopBarColors(
        containerColor = Color(containerColor),
        contentColor = contentColor,
        cornerRadius = cornerRadius,
        shadowElevation = shadowElevation,
        wallpaperFile = TopBarConfig.currentWallpaperFile(context, AppConfig.isNightTheme)
            ?.takeIf { !transparentNavBar && config.style == TopBarConfig.STYLE_REGULAR },
        wallpaperAlpha = TopBarConfig.opacityToAlpha(alphaPercent) / 255f
    )
}

@Composable
fun PageTopBarContainer(
    colors: PageTopBarColors,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.pageTopBarBackground(colors)) {
        content()
    }
}

@Composable
fun Modifier.pageTopBarBackground(colors: PageTopBarColors): Modifier {
    val shape = RoundedCornerShape(
        bottomStart = colors.cornerRadius,
        bottomEnd = colors.cornerRadius
    )
    return this
        .fillMaxWidth()
        .shadow(colors.shadowElevation, shape, clip = false)
        .clip(shape)
        .background(colors.containerColor)
        .drawTopBarWallpaper(colors)
}

@Composable
private fun Modifier.drawTopBarWallpaper(colors: PageTopBarColors): Modifier {
    val wallpaper = colors.wallpaperFile
    val context = LocalContext.current
    val targetWidth = context.resources.displayMetrics.widthPixels.coerceAtLeast(1)
    val targetHeight = with(LocalDensity.current) { 160.dp.roundToPx() }.coerceAtLeast(1)
    val wallpaperBitmap = remember(
        wallpaper?.absolutePath,
        wallpaper?.lastModified(),
        targetWidth,
        targetHeight
    ) {
        wallpaper?.takeIf { it.exists() && it.isFile }?.let { file ->
            runCatching {
                BitmapUtils.decodeBitmap(file.absolutePath, targetWidth, targetHeight)
            }.getOrNull()
        }
    }
    return if (wallpaperBitmap == null) {
        this
    } else {
        this.drawWithContent {
            drawIntoCanvas { canvas ->
                val dstWidth = size.width.roundToInt().coerceAtLeast(1)
                val dstHeight = size.height.roundToInt().coerceAtLeast(1)
                val scale = max(
                    dstWidth / wallpaperBitmap.width.toFloat(),
                    dstHeight / wallpaperBitmap.height.toFloat()
                )
                val srcWidth = (dstWidth / scale).roundToInt().coerceAtMost(wallpaperBitmap.width)
                val srcHeight = (dstHeight / scale).roundToInt().coerceAtMost(wallpaperBitmap.height)
                val srcLeft = ((wallpaperBitmap.width - srcWidth) / 2).coerceAtLeast(0)
                val srcTop = ((wallpaperBitmap.height - srcHeight) / 2).coerceAtLeast(0)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (colors.wallpaperAlpha.coerceIn(0f, 1f) * 255).roundToInt()
                }
                canvas.nativeCanvas.drawBitmap(
                    wallpaperBitmap,
                    Rect(srcLeft, srcTop, srcLeft + srcWidth, srcTop + srcHeight),
                    Rect(0, 0, dstWidth, dstHeight),
                    paint
                )
            }
            drawContent()
        }
    }
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

