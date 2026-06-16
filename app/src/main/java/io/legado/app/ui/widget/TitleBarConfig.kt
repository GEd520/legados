package io.legado.app.ui.widget

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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

private fun TitleBar.bitmapLayer(file: File, alphaPercent: Int): BitmapDrawable? {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    return BitmapDrawable(resources, bitmap).apply {
        alpha = (alphaPercent.coerceIn(0, 100) * 255 / 100).coerceIn(0, 255)
    }
}
