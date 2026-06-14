package io.legado.app.help.config

import android.graphics.Color
import androidx.annotation.Keep
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import java.util.Date

/**
 * 底栏配置包 - 参考 mmr 项目的 NavigationBarConfig
 */
@Keep
data class NavigationBarConfig(
    var id: String,
    var name: String,
    var isNight: Boolean,
    var isBuiltin: Boolean = false,
    var layoutMode: String = "floating",  // floating, standard, sidebar
    var effectMode: String = "glass",     // solid, glass, frosted
    var opacity: Int = 72,                // 0 ~ 100
    var borderColor: Int? = null,         // 边框颜色
    var borderAlpha: Int = 100,           // 边框透明度 0 ~ 100
    var wallpaperPath: String? = null,    // 底栏壁纸路径 (仅标准模式)
    var sidebarBackgroundPath: String? = null, // 侧边栏背景路径 (仅侧边栏模式)
    var sidebarGravity: String = "start", // start, end (仅侧边栏模式)
    var icons: Map<String, String> = emptyMap(), // 图标配置
    var updatedAt: Long = System.currentTimeMillis()
) {

    fun toJson(): String {
        return GSON.toJson(this)
    }

    companion object {
        fun fromJson(json: String): NavigationBarConfig {
            return GSON.fromJsonObject<NavigationBarConfig>(json).getOrThrow()
        }

        // 创建默认日间底栏配置
        fun createDefaultDay(): NavigationBarConfig {
            return NavigationBarConfig(
                id = "builtin_default_day",
                name = "默认",
                isNight = false,
                isBuiltin = true,
                layoutMode = "floating",
                effectMode = "glass",
                opacity = 72
            )
        }

        // 创建默认夜间底栏配置
        fun createDefaultNight(): NavigationBarConfig {
            return NavigationBarConfig(
                id = "builtin_default_night",
                name = "默认",
                isNight = true,
                isBuiltin = true,
                layoutMode = "floating",
                effectMode = "glass",
                opacity = 72
            )
        }
    }

    fun copy(): NavigationBarConfig {
        return NavigationBarConfig(
            id = id,
            name = name,
            isNight = isNight,
            isBuiltin = isBuiltin,
            layoutMode = layoutMode,
            effectMode = effectMode,
            opacity = opacity,
            borderColor = borderColor,
            borderAlpha = borderAlpha,
            wallpaperPath = wallpaperPath,
            sidebarBackgroundPath = sidebarBackgroundPath,
            sidebarGravity = sidebarGravity,
            icons = icons,
            updatedAt = updatedAt
        )
    }

    fun getLayoutModeText(): String {
        return when (layoutMode) {
            "floating" -> "悬浮"
            "standard" -> "标准"
            "sidebar" -> "侧边栏"
            else -> "悬浮"
        }
    }

    fun getEffectModeText(): String {
        return when (effectMode) {
            "solid" -> "实心"
            "glass" -> "玻璃"
            "frosted" -> "磨砂"
            else -> "玻璃"
        }
    }

    fun getSidebarGravityText(): String {
        return when (sidebarGravity) {
            "start" -> "左侧"
            "end" -> "右侧"
            else -> "左侧"
        }
    }
}