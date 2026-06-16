package io.legado.app.ui.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.theme.elevation
import io.legado.app.lib.theme.primaryColor
import java.io.File

fun TitleBar.applyTopBarConfig() {
    val config = TopBarConfig.currentConfig(context, AppConfig.isNightTheme)
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
    val shape = GradientDrawable().apply {
        setColor(backgroundColor)
        cornerRadius = radius
    }
    val wallpaper = TopBarConfig.currentWallpaperFile(context, AppConfig.isNightTheme)
        ?.takeIf { config.style == TopBarConfig.STYLE_REGULAR }
        ?.let { file -> bitmapLayer(file, config.wallpaperAlpha) }
    background = if (wallpaper == null) {
        shape
    } else {
        LayerDrawable(arrayOf(shape, wallpaper))
    }
    elevation = if (config.style == TopBarConfig.STYLE_REGULAR && config.cornerScale != 0f) {
        0f
    } else {
        context.elevation
    }
    applyTopBarChildConfig(config)
}

private fun TitleBar.applyTopBarChildConfig(config: TopBarConfig.Config) {
    val tagBarColor = config.tagBarColor
        ?: ContextCompat.getColor(context, R.color.background_menu)
    val selectedColor = config.tagSelectedColor
        ?: ContextCompat.getColor(context, R.color.background_card)
    findViewById<TabLayout?>(R.id.tab_layout)?.let { tabLayout ->
        tabLayout.setBackgroundColor(TopBarConfig.withOpacity(tagBarColor, config.tagBarAlpha))
        tabLayout.setSelectedTabIndicatorColor(
            TopBarConfig.withOpacity(selectedColor, config.tagSelectedAlpha)
        )
    }
    findViewById<View?>(R.id.search_view)?.setBackgroundColor(
        TopBarConfig.withOpacity(tagBarColor, config.tagBarAlpha)
    )
}

private fun TitleBar.bitmapLayer(file: File, alphaPercent: Int): Drawable? {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    return TopBarWallpaperDrawable(
        bitmap = bitmap,
        alpha = (alphaPercent.coerceIn(0, 100) * 255 / 100).coerceIn(0, 255)
    )
}

private class TopBarWallpaperDrawable(
    private val bitmap: Bitmap,
    alpha: Int
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        this.alpha = alpha
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        val src = centerCropSrcRect(bitmap.width, bitmap.height, bounds.width(), bounds.height())
        canvas.drawBitmap(bitmap, src, bounds, paint)
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

    private fun centerCropSrcRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Rect {
        if (targetWidth <= 0 || targetHeight <= 0) {
            return Rect(0, 0, bitmapWidth, bitmapHeight)
        }
        val bitmapRatio = bitmapWidth.toFloat() / bitmapHeight.toFloat()
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
        return if (bitmapRatio > targetRatio) {
            val scaledWidth = (bitmapHeight * targetRatio).toInt().coerceAtLeast(1)
            val left = ((bitmapWidth - scaledWidth) / 2).coerceAtLeast(0)
            Rect(left, 0, (left + scaledWidth).coerceAtMost(bitmapWidth), bitmapHeight)
        } else {
            val scaledHeight = (bitmapWidth / targetRatio).toInt().coerceAtLeast(1)
            val top = ((bitmapHeight - scaledHeight) / 2).coerceAtLeast(0)
            Rect(0, top, bitmapWidth, (top + scaledHeight).coerceAtMost(bitmapHeight))
        }
    }
}
