package io.legado.app.ui.widget

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
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
import android.util.StateSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.theme.elevation
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.applyTint
import java.io.File

fun TitleBar.applyTopBarConfig() {
    val config = TopBarConfig.currentConfig(context, AppConfig.isNightTheme)
    applyTopBarConfig(config)
}

fun TitleBar.applyThemeTopBarOpacity() {
    if (AppConfig.isEInkMode) {
        setBackgroundResource(R.drawable.bg_eink_border_bottom)
        return
    }
    if (!opaque && context.transparentNavBar) {
        setBackgroundColor(Color.TRANSPARENT)
        applyTopBarContentColor(context.primaryTextColor)
        return
    }
    val config = TopBarConfig.currentConfig(context, AppConfig.isNightTheme)
    if (ignoreTopBarOpacity) {
        setBackgroundColor(context.primaryColor)
        applyTopBarContentColor(context.primaryTextColor)
        elevation = context.elevation
        return
    }
    val alpha = if (config.style == TopBarConfig.STYLE_REGULAR) {
        config.wallpaperAlpha
    } else {
        config.tagBarAlpha
    }
    setBackgroundColor(TopBarConfig.withOpacity(context.primaryColor, alpha))
    applyTopBarContentColor(context.primaryTextColor)
    elevation = if (alpha < 100) 0.1f else context.elevation
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
        ?.let { file -> bitmapLayer(file, backgroundAlpha) }
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
    applyTopBarContentColor(context.primaryTextColor)
    applyTopBarChildConfig(config)
}

private fun TitleBar.applyTopBarContentColor(color: Int) {
    setTextColor(color)
    setColorFilter(color)
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
    val contentColor = context.primaryTextColor
    if (this is TabLayout && id == R.id.tab_layout) {
        setBackgroundColor(TopBarConfig.withOpacity(tagBarColor, config.tagBarAlpha))
        setTabTextColors(tabTextColorStateList(contentColor))
        setSelectedTabIndicatorColor(
            TopBarConfig.withOpacity(selectedColor, config.tagSelectedAlpha)
        )
    }
    if (id == R.id.search_view) {
        background = searchViewBackground()
        applyTint(contentColor)
        (this as? SearchView)?.setSearchHintIconTint(contentColor)
    }
}

private fun View.searchViewBackground(): Drawable {
    val radius = 35f * resources.displayMetrics.density
    val strokeWidth = (0.5f * resources.displayMetrics.density).coerceAtLeast(1f).toInt()
    val color = ContextCompat.getColor(context, R.color.transparent20)
    return GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
        setStroke(strokeWidth, color)
    }
}

private fun tabTextColorStateList(contentColor: Int): ColorStateList {
    val normalColor = ColorUtils.setAlphaComponent(contentColor, 200)
    return ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_selected),
            StateSet.WILD_CARD
        ),
        intArrayOf(contentColor, normalColor)
    )
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
