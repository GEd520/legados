package io.legado.app.help.config

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.Menu
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import com.google.gson.JsonArray
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent

@Keep
data class NavigationBarConfig(
    var id: String,
    var name: String,
    var isNight: Boolean,
    var isBuiltin: Boolean = false,
    var layoutMode: String = LAYOUT_FLOATING,
    var effectMode: String = EFFECT_GLASS,
    var backgroundColor: Int? = null,
    var opacity: Int = 76,
    var borderColor: Int? = null,
    var borderAlpha: Int = 100,
    var wallpaperPath: String? = null,
    var sidebarBackgroundPath: String? = null,
    var sidebarGravity: String = "start",
    var icons: Map<String, String> = emptyMap(),
    var updatedAt: Long = System.currentTimeMillis()
) {

    data class NavItem(
        val key: String,
        @StringRes val titleRes: Int,
        @IdRes val menuId: Int,
        @DrawableRes val defaultIconRes: Int
    )

    fun toJson(): String = GSON.toJson(this)

    fun copySelf(): NavigationBarConfig = copy(icons = icons.toMap())

    fun getLayoutModeText(): String = when (layoutMode) {
        LAYOUT_STANDARD -> "常规底栏"
        LAYOUT_SIDEBAR -> "侧边栏"
        else -> "悬浮底栏"
    }

    fun getEffectModeText(): String = when (effectMode) {
        EFFECT_SOLID -> "实色"
        EFFECT_FROSTED -> "磨砂"
        else -> "玻璃"
    }

    companion object {
        const val LAYOUT_FLOATING = "floating"
        const val LAYOUT_STANDARD = "standard"
        const val LAYOUT_SIDEBAR = "sidebar"
        const val EFFECT_SOLID = "solid"
        const val EFFECT_GLASS = "glass"
        const val EFFECT_FROSTED = "frosted"
        const val STATE_NORMAL = "normal"
        const val STATE_SELECTED = "selected"

        private const val PREF_KEY_ACTIVE_DAY = "activeDayNavBarId"
        private const val PREF_KEY_ACTIVE_NIGHT = "activeNightNavBarId"
        private const val PREF_KEY_CUSTOM_CONFIGS = "customNavBarConfigs"

        val items = listOf(
            NavItem("bookshelf", R.string.bookshelf, R.id.menu_bookshelf, R.drawable.ic_bottom_books),
            NavItem("discovery", R.string.discovery, R.id.menu_discovery, R.drawable.ic_bottom_explore),
            NavItem("rss", R.string.rss, R.id.menu_rss, R.drawable.ic_bottom_rss_feed),
            NavItem("my", R.string.my, R.id.menu_my_config, R.drawable.ic_bottom_person)
        )

        fun fromJson(json: String): NavigationBarConfig {
            return GSON.fromJsonObject<NavigationBarConfig>(json).getOrThrow()
        }

        fun createDefaultDay(): NavigationBarConfig {
            return NavigationBarConfig(
                id = "builtin_default_day",
                name = "日间底栏",
                isNight = false,
                isBuiltin = true
            )
        }

        fun createDefaultNight(): NavigationBarConfig {
            return NavigationBarConfig(
                id = "builtin_default_night",
                name = "夜间底栏",
                isNight = true,
                isBuiltin = true
            )
        }

        fun loadConfigs(context: Context): MutableList<NavigationBarConfig> {
            val configs = mutableListOf(createDefaultDay(), createDefaultNight())
            val stored = context.getPrefString(PREF_KEY_CUSTOM_CONFIGS)
            var shouldMigrate = false
            when {
                stored.isNullOrBlank() -> Unit
                stored.trimStart().startsWith("[") -> {
                    configs.addAll(parseConfigArray(stored))
                }
                else -> {
                    parseLegacyConfigObjects(stored)
                        .mapNotNull { json -> runCatching { fromJson(json) }.getOrNull() }
                        .also {
                            configs.addAll(it)
                            shouldMigrate = it.isNotEmpty()
                        }
                }
            }
            if (shouldMigrate) {
                saveConfigs(context, configs)
            }
            return configs
        }

        fun saveConfigs(context: Context, configs: List<NavigationBarConfig>) {
            context.defaultSharedPreferences.edit(commit = true) {
                putString(
                    PREF_KEY_CUSTOM_CONFIGS,
                    GSON.toJson(configs.filter { !it.isBuiltin })
                )
            }
        }

        private fun parseConfigArray(stored: String): List<NavigationBarConfig> {
            val array = runCatching {
                GSON.fromJson(stored, JsonArray::class.java)
            }.getOrNull() ?: return emptyList()
            return array.mapNotNull { element ->
                runCatching {
                    GSON.fromJson(element, NavigationBarConfig::class.java)
                }.getOrNull()
            }
        }

        private fun parseLegacyConfigObjects(stored: String): List<String> {
            val result = mutableListOf<String>()
            var depth = 0
            var start = -1
            var inString = false
            var escaped = false
            stored.forEachIndexed { index, char ->
                if (escaped) {
                    escaped = false
                    return@forEachIndexed
                }
                when {
                    char == '\\' && inString -> escaped = true
                    char == '"' -> inString = !inString
                    !inString && char == '{' -> {
                        if (depth == 0) start = index
                        depth++
                    }
                    !inString && char == '}' -> {
                        depth--
                        if (depth == 0 && start >= 0) {
                            result.add(stored.substring(start, index + 1))
                            start = -1
                        }
                    }
                }
            }
            return result
        }

        fun activeId(context: Context, isNight: Boolean): String? {
            return context.getPrefString(if (isNight) PREF_KEY_ACTIVE_NIGHT else PREF_KEY_ACTIVE_DAY)
        }

        fun setActiveId(context: Context, isNight: Boolean, id: String?) {
            context.defaultSharedPreferences.edit(commit = true) {
                putString(if (isNight) PREF_KEY_ACTIVE_NIGHT else PREF_KEY_ACTIVE_DAY, id.orEmpty())
            }
        }

        fun activeConfig(context: Context, isNight: Boolean): NavigationBarConfig {
            val configs = loadConfigs(context)
            val activeId = activeId(context, isNight)
            return configs.firstOrNull { it.isNight == isNight && it.id == activeId }
                ?: configs.first { it.isNight == isNight }
        }

        fun currentSignature(context: Context, isNight: Boolean): String {
            val config = activeConfig(context, isNight)
            val iconSignature = config.icons.entries
                .sortedBy { it.key }
                .joinToString("|") { "${it.key}:${it.value}" }
            return listOf(
                isNight,
                config.id,
                config.layoutMode,
                config.effectMode,
                config.backgroundColor,
                config.opacity,
                config.borderColor,
                config.borderAlpha,
                config.updatedAt,
                iconSignature
            ).joinToString("|")
        }

        fun applyConfig(context: Context, config: NavigationBarConfig, recreate: Boolean = false) {
            setActiveId(context, config.isNight, config.id)
            ThemeConfig.applyTheme(context)
            postEvent(EventBus.NAVIGATION_BAR_CHANGED, config.isNight)
            if (recreate) postEvent(EventBus.RECREATE, "")
        }

        fun resolveBottomColor(baseColor: Int, config: NavigationBarConfig): Int {
            val alpha = config.opacity.coerceIn(0, 100) / 100f
            if (config.isBuiltin) return ColorUtils.withAlpha(baseColor, 1f)
            return ColorUtils.withAlpha(config.backgroundColor ?: baseColor, alpha)
        }

        fun applyToMenu(menu: Menu, context: Context, isNight: Boolean, bgColor: Int? = null): Boolean {
            val config = activeConfig(context, isNight)
            var hasCustom = false
            items.forEach { item ->
                val normal = loadIconDrawable(context, config.icons[iconKey(item.key, STATE_NORMAL)])
                val selected = loadIconDrawable(context, config.icons[iconKey(item.key, STATE_SELECTED)])
                if (normal != null || selected != null) hasCustom = true
                menu.findItem(item.menuId)?.icon = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_checked), selected ?: normal ?: defaultDrawable(context, item.defaultIconRes, true, bgColor))
                    addState(intArrayOf(android.R.attr.state_selected), selected ?: normal ?: defaultDrawable(context, item.defaultIconRes, true, bgColor))
                    addState(intArrayOf(), normal ?: defaultDrawable(context, item.defaultIconRes, false, bgColor))
                }
            }
            return hasCustom
        }

        fun previewDrawable(context: Context, config: NavigationBarConfig, item: NavItem, selected: Boolean, bgColor: Int? = null): Drawable? {
            val state = if (selected) STATE_SELECTED else STATE_NORMAL
            return loadIconDrawable(context, config.icons[iconKey(item.key, state)])
                ?: loadIconDrawable(context, config.icons[iconKey(item.key, STATE_NORMAL)])
                ?: defaultDrawable(context, item.defaultIconRes, selected, bgColor)
        }

        fun iconKey(itemKey: String, state: String): String = "${itemKey}_$state"

        private fun loadIconDrawable(context: Context, path: String?): Drawable? {
            if (path.isNullOrBlank()) return null
            return Drawable.createFromPath(path)
        }

        private fun defaultDrawable(context: Context, @DrawableRes resId: Int, selected: Boolean, bgColor: Int? = null): Drawable {
            val drawable = ContextCompat.getDrawable(context, resId)!!.mutate()
            val bg = bgColor ?: ThemeStore.bottomBackground(context)
            val textIsDark = ColorUtils.isColorLight(bg)
            val color = if (selected) ThemeStore.accentColor(context) else context.getSecondaryTextColor(textIsDark)
            DrawableCompat.setTint(drawable, color)
            return drawable
        }
    }
}
