package io.legado.app.ui.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.theme.elevation
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.BitmapUtils
import java.io.File

fun TitleBar.applyTopBarConfig() {
    val config = TopBarConfig.currentConfig(context, AppConfig.isNightTheme)
    applyTopBarConfig(config)
}

private fun TitleBar.applyTopBarConfig(config: TopBarConfig.Config) {
    val backgroundColor = if (config.style == TopBarConfig.STYLE_REGULAR) {
        TopBarConfig.resolveBackgroundColor(config)
    } else {
        config.tagBarColor ?: context.primaryColor
    }
    val radius = if (config.style == TopBarConfig.STYLE_REGULAR) {
        context.resources.getDimension(R.dimen.ui_panel_radius) *
            TopBarConfig.resolveCornerScale(config).coerceIn(0f, 3f)
    } else {
        0f
    }
    val backgroundAlpha = if (config.style == TopBarConfig.STYLE_REGULAR) {
        config.wallpaperAlpha
    } else {
        config.tagBarAlpha
    }
    val shape = regularBackground(
        backgroundColor,
        radius,
        backgroundAlpha
    )
    val wallpaper = TopBarConfig.currentWallpaperFile(context, AppConfig.isNightTheme)
        ?.takeIf { config.style == TopBarConfig.STYLE_REGULAR }
        ?.let { file -> bitmapLayer(file, config.wallpaperAlpha) }
    background = if (wallpaper == null) {
        shape
    } else {
        LayerDrawable(arrayOf(shape, wallpaper))
    }
    elevation = when {
        config.style == TopBarConfig.STYLE_REGULAR && config.cornerScale != 0f -> 0f
        backgroundAlpha < 100 -> 0.1f
        else -> context.elevation
    }
    applyTopBarChildConfig(config)
}

fun View.refreshTopBarConfigDeep() {
    val config = TopBarConfig.currentConfig(context, AppConfig.isNightTheme)
    refreshTopBarConfigDeep(config)
}

private fun View.refreshTopBarConfigDeep(config: TopBarConfig.Config) {
    if (this is TitleBar) {
        applyTopBarConfig(config)
        return
    }
    applyTopBarChildConfig(config)
    if (this is ViewGroup) {
        children.forEach { it.refreshTopBarConfigDeep(config) }
    }
}

fun View.applyTopBarChildConfig() {
    val config = TopBarConfig.currentConfig(context, AppConfig.isNightTheme)
    applyTopBarChildConfig(config)
}

private fun regularBackground(color: Int, radius: Float, alphaPercent: Int): Drawable {
    return GradientDrawable().apply {
        setColor(TopBarConfig.withOpacity(color, alphaPercent))
        cornerRadii = if (radius > 0f) {
            floatArrayOf(
                0f, 0f,
                0f, 0f,
                radius, radius,
                radius, radius
            )
        } else {
            null
        }
    }
}

private fun TitleBar.applyTopBarChildConfig(config: TopBarConfig.Config) {
    findViewById<TabLayout?>(R.id.tab_layout)?.applyTopBarChildConfig(config)
    findViewById<View?>(R.id.search_view)?.applyTopBarChildConfig(config)
}

private fun View.applyTopBarChildConfig(config: TopBarConfig.Config) {
    if (this !is TabLayout && id != R.id.search_view) return
    val tagBarColor = config.tagBarColor
        ?: ContextCompat.getColor(context, R.color.background_menu)
    val selectedColor = config.tagSelectedColor ?: context.primaryColor
    if (this is TabLayout && id == R.id.tab_layout) {
        setBackgroundColor(TopBarConfig.withOpacity(tagBarColor, config.tagBarAlpha))
        setSelectedTabIndicatorColor(
            TopBarConfig.withOpacity(selectedColor, config.tagSelectedAlpha)
        )
    }
    if (id == R.id.search_view) {
        setBackgroundColor(TopBarConfig.withOpacity(tagBarColor, config.tagBarAlpha))
    }
}

private fun TitleBar.bitmapLayer(file: File, alphaPercent: Int): Drawable? {
    val bitmap = kotlin.runCatching {
        BitmapUtils.decodeBitmap(
            file.absolutePath,
            resources.displayMetrics.widthPixels.coerceAtLeast(1),
            height.takeIf { it > 0 } ?: (56 * resources.displayMetrics.density).toInt()
        )
    }.getOrNull() ?: return null
    return TopBarWallpaperDrawable(
        bitmap = bitmap,
        radius = context.resources.getDimension(R.dimen.ui_panel_radius) *
            TopBarConfig.resolveCornerScale(TopBarConfig.currentConfig(context, AppConfig.isNightTheme))
                .coerceIn(0f, 3f),
        alphaPercent = alphaPercent
    )
}

private class TopBarWallpaperDrawable(
    private val bitmap: Bitmap,
    private val radius: Float,
    alphaPercent: Int
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        alpha = (alphaPercent.coerceIn(0, 100) * 255 / 100).coerceIn(0, 255)
    }
    private val rect = RectF()
    private val matrix = Matrix()
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty || bitmap.width <= 0 || bitmap.height <= 0) return
        rect.set(bounds)
        val scale = maxOf(
            bounds.width() / bitmap.width.toFloat(),
            bounds.height() / bitmap.height.toFloat()
        )
        val dx = bounds.left + (bounds.width() - bitmap.width * scale) / 2f
        val dy = bounds.top + (bounds.height() - bitmap.height * scale) / 2f
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
        paint.shader?.setLocalMatrix(matrix)
        path.reset()
        path.addRoundRect(
            rect,
            floatArrayOf(
                0f, 0f,
                0f, 0f,
                radius, radius,
                radius, radius
            ),
            Path.Direction.CW
        )
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android SDK")
    override fun getOpacity(): Int {
        return if (paint.alpha >= 255) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int = -1

    override fun getIntrinsicHeight(): Int = -1
}
