package io.legado.app.help.config

import androidx.annotation.Keep
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

/**
 * 顶栏配置包 - 参考 mmr 项目的 TopBarConfig
 */
@Keep
data class TopBarConfig(
    var id: String,
    var name: String,
    var isNight: Boolean,
    var isBuiltin: Boolean = false,
    var style: String = "default",       // default, regular
    var cornerScale: Float = 1.0f,       // 0.0 ~ 3.0 圆角倍率
    var backgroundColor: Int? = null,    // 背景色 (仅常规样式)
    var wallpaperPath: String? = null,   // 顶栏壁纸路径 (仅常规样式)
    var wallpaperAlpha: Int = 100,       // 壁纸透明度 0 ~ 100
    var tagBarColor: Int? = null,        // 标签栏背景色
    var tagBarAlpha: Int = 100,          // 标签栏透明度 0 ~ 100
    var tagSelectedColor: Int? = null,   // 选中标签背景色
    var tagSelectedAlpha: Int = 100,     // 选中标签透明度 0 ~ 100
    var expandFiltersByDefault: Boolean = false, // 筛选栏默认展开
    var updatedAt: Long = System.currentTimeMillis()
) {

    fun toJson(): String {
        return GSON.toJson(this)
    }

    companion object {
        fun fromJson(json: String): TopBarConfig {
            return GSON.fromJsonObject<TopBarConfig>(json).getOrThrow()
        }

        // 创建默认日间顶栏配置
        fun createDefaultDay(): TopBarConfig {
            return TopBarConfig(
                id = "builtin_default_day",
                name = "默认",
                isNight = false,
                isBuiltin = true,
                style = "default",
                cornerScale = 1.0f,
                tagBarAlpha = 100,
                tagSelectedAlpha = 100,
                wallpaperAlpha = 100
            )
        }

        // 创建默认夜间顶栏配置
        fun createDefaultNight(): TopBarConfig {
            return TopBarConfig(
                id = "builtin_default_night",
                name = "默认",
                isNight = true,
                isBuiltin = true,
                style = "default",
                cornerScale = 1.0f,
                tagBarAlpha = 100,
                tagSelectedAlpha = 100,
                wallpaperAlpha = 100
            )
        }
    }

    fun copy(): TopBarConfig {
        return TopBarConfig(
            id = id,
            name = name,
            isNight = isNight,
            isBuiltin = isBuiltin,
            style = style,
            cornerScale = cornerScale,
            backgroundColor = backgroundColor,
            wallpaperPath = wallpaperPath,
            wallpaperAlpha = wallpaperAlpha,
            tagBarColor = tagBarColor,
            tagBarAlpha = tagBarAlpha,
            tagSelectedColor = tagSelectedColor,
            tagSelectedAlpha = tagSelectedAlpha,
            expandFiltersByDefault = expandFiltersByDefault,
            updatedAt = updatedAt
        )
    }

    fun getStyleText(): String {
        return when (style) {
            "regular" -> "常规顶栏"
            else -> "默认顶栏"
        }
    }

    fun getFilterDefaultText(): String {
        return if (expandFiltersByDefault) "展开" else "折叠"
    }
}