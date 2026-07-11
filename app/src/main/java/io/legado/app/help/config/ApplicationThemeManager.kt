package io.legado.app.help.config

import android.content.Context
import androidx.annotation.Keep
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefString
import io.legado.app.utils.normalizeFileName
import splitties.init.appCtx
import java.util.UUID
import java.io.File

object ApplicationThemeManager {

    private const val fileName = "applicationThemes.json"
    private const val currentIdKey = "currentApplicationThemeId"
    private val filePath = FileUtils.getPath(appCtx.filesDir, fileName)

    @Keep
    data class Config(
        val id: String = UUID.randomUUID().toString(),
        var name: String = "",
        var dayTheme: ThemeConfig.Config? = null,
        var nightTheme: ThemeConfig.Config? = null,
        var dayTopBarDir: String = TopBarConfig.DEFAULT_DIR_NAME,
        var nightTopBarDir: String = TopBarConfig.DEFAULT_DIR_NAME,
        var dayBottomBarId: String? = null,
        var nightBottomBarId: String? = null,
        var dayCoverGroupId: Long? = null,
        var nightCoverGroupId: Long? = null,
        var updatedAt: Long = System.currentTimeMillis()
    )

    fun load(): MutableList<Config> {
        val file = java.io.File(filePath)
        if (!file.isFile) return mutableListOf()
        return GSON.fromJsonArray<Config>(file.readText()).getOrNull()?.toMutableList()
            ?: mutableListOf()
    }

    fun currentId(context: Context): String = context.getPrefString(currentIdKey).orEmpty()

    fun find(id: String): Config? = load().firstOrNull { it.id == id }

    fun exportCurrent(context: Context): File {
        val current = load().firstOrNull { isCurrent(context, it) }
            ?: captureCurrent(context, appCtx.getString(io.legado.app.R.string.application_theme_manage))
        val dir = appCtx.cacheDir.resolve("applicationThemeExports").apply { mkdirs() }
        val exportName = current.name.normalizeFileName().ifBlank { "application_theme" }
        return dir.resolve("$exportName.json").apply {
            writeText(GSON.toJson(current))
        }
    }

    fun importFile(file: File): Config {
        val imported = GSON.fromJson(file.readText(), Config::class.java)
            ?: throw IllegalArgumentException("Invalid application theme")
        val items = load()
        val baseName = imported.name.trim().ifBlank {
            appCtx.getString(io.legado.app.R.string.application_theme_manage)
        }
        var name = baseName
        var suffix = 2
        while (items.any { it.name == name }) {
            name = "$baseName $suffix"
            suffix++
        }
        val next = imported.copy(
            id = UUID.randomUUID().toString(),
            name = name,
            updatedAt = System.currentTimeMillis()
        )
        items.add(next)
        save(items)
        return next
    }

    fun isCurrent(context: Context, config: Config): Boolean {
        return currentId(context) == config.id &&
            (config.dayTheme == null || context.getPrefString(PreferKey.dThemeName).orEmpty() == config.dayTheme?.themeName) &&
            (config.nightTheme == null || context.getPrefString(PreferKey.dNThemeName).orEmpty() == config.nightTheme?.themeName) &&
            (config.dayTopBarDir.isBlank() || TopBarConfig.activeDirName(false) == config.dayTopBarDir) &&
            (config.nightTopBarDir.isBlank() || TopBarConfig.activeDirName(true) == config.nightTopBarDir) &&
            (config.dayBottomBarId == null || NavigationBarConfig.activeConfig(context, false).id == config.dayBottomBarId) &&
            (config.nightBottomBarId == null || NavigationBarConfig.activeConfig(context, true).id == config.nightBottomBarId) &&
            selectedCoverGroupId(context, false) == config.dayCoverGroupId &&
            selectedCoverGroupId(context, true) == config.nightCoverGroupId
    }

    fun captureCurrent(context: Context, name: String, id: String? = null): Config {
        val dayThemeName = context.getPrefString(PreferKey.dThemeName).orEmpty()
        val nightThemeName = context.getPrefString(PreferKey.dNThemeName).orEmpty()
        return Config(
            id = id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            dayTheme = ThemeConfig.configList.firstOrNull {
                !it.isNightTheme && it.themeName == dayThemeName
            }?.copy(),
            nightTheme = ThemeConfig.configList.firstOrNull {
                it.isNightTheme && it.themeName == nightThemeName
            }?.copy(),
            dayTopBarDir = TopBarConfig.activeDirName(false),
            nightTopBarDir = TopBarConfig.activeDirName(true),
            dayBottomBarId = NavigationBarConfig.activeConfig(context, false).id,
            nightBottomBarId = NavigationBarConfig.activeConfig(context, true).id,
            dayCoverGroupId = selectedCoverGroupId(context, false),
            nightCoverGroupId = selectedCoverGroupId(context, true)
        )
    }

    fun add(config: Config) {
        val items = load()
        require(config.name.isNotBlank())
        require(items.none { it.name == config.name })
        items.add(config)
        save(items)
    }

    fun replace(config: Config) {
        val items = load()
        require(config.name.isNotBlank())
        require(items.none { it.id != config.id && it.name == config.name })
        val index = items.indexOfFirst { it.id == config.id }
        if (index >= 0) items[index] = config else items.add(config)
        save(items)
    }

    fun rename(id: String, name: String) {
        val items = load()
        val nextName = name.trim()
        require(nextName.isNotBlank())
        require(items.none { it.id != id && it.name == nextName })
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            items[index] = items[index].copy(name = nextName, updatedAt = System.currentTimeMillis())
            save(items)
        }
    }

    fun delete(context: Context, id: String) {
        save(load().filterNot { it.id == id })
        if (currentId(context) == id) context.putPrefString(currentIdKey, "")
    }

    fun apply(context: Context, config: Config) {
        val wasNight = AppConfig.isNightTheme
        config.dayTheme?.let { ThemeConfig.applyConfig(context, it.copy(isNightTheme = false), applyNow = false) }
        config.nightTheme?.let { ThemeConfig.applyConfig(context, it.copy(isNightTheme = true), applyNow = false) }

        applyTopBar(context, false, config.dayTopBarDir)
        applyTopBar(context, true, config.nightTopBarDir)
        applyBottomBar(context, false, config.dayBottomBarId)
        applyBottomBar(context, true, config.nightBottomBarId)

        val coverRepository = CoverGalleryRepository()
        val groupIds = coverRepository.allGroupsWithImages().map { it.group.id }.toSet()
        coverRepository.setSelectedGroup(false, config.dayCoverGroupId?.takeIf(groupIds::contains))
        coverRepository.setSelectedGroup(true, config.nightCoverGroupId?.takeIf(groupIds::contains))

        AppConfig.isNightTheme = wasNight
        ThemeConfig.applyDayNight(context)
        context.putPrefString(currentIdKey, config.id)
        postEvent(EventBus.TOP_BAR_CHANGED, wasNight)
        postEvent(EventBus.NAVIGATION_BAR_CHANGED, wasNight)
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

    fun summary(context: Context, config: Config): String {
        val dayTheme = config.dayTheme?.themeName ?: context.getString(io.legado.app.R.string.application_theme_not_set)
        val nightTheme = config.nightTheme?.themeName ?: context.getString(io.legado.app.R.string.application_theme_not_set)
        val dayTop = topBarName(context, false, config.dayTopBarDir)
        val nightTop = topBarName(context, true, config.nightTopBarDir)
        val dayBottom = bottomBarName(context, false, config.dayBottomBarId)
        val nightBottom = bottomBarName(context, true, config.nightBottomBarId)
        val covers = CoverGalleryRepository()
        val dayCover = covers.getGroupName(config.dayCoverGroupId) ?: context.getString(io.legado.app.R.string.application_theme_not_set)
        val nightCover = covers.getGroupName(config.nightCoverGroupId) ?: context.getString(io.legado.app.R.string.application_theme_not_set)
        return "日间：$dayTheme / $dayTop / $dayBottom / $dayCover\n夜间：$nightTheme / $nightTop / $nightBottom / $nightCover"
    }

    private fun save(items: List<Config>) {
        FileUtils.createFileIfNotExist(filePath).writeText(GSON.toJson(items))
    }

    private fun applyTopBar(context: Context, isNight: Boolean, dirName: String) {
        TopBarConfig.loadEntries(context, isNight).firstOrNull { it.dirName == dirName }
            ?.let(TopBarConfig::apply)
    }

    private fun applyBottomBar(context: Context, isNight: Boolean, id: String?) {
        NavigationBarConfig.loadConfigs(context)
            .firstOrNull { it.isNight == isNight && it.id == id }
            ?.let { NavigationBarConfig.setActiveId(context, isNight, it.id) }
    }

    private fun topBarName(context: Context, isNight: Boolean, dirName: String): String {
        return TopBarConfig.loadEntries(context, isNight)
            .firstOrNull { it.dirName == dirName }?.config?.name
            ?: context.getString(io.legado.app.R.string.application_theme_not_set)
    }

    private fun bottomBarName(context: Context, isNight: Boolean, id: String?): String {
        return NavigationBarConfig.loadConfigs(context)
            .firstOrNull { it.isNight == isNight && it.id == id }?.name
            ?: context.getString(io.legado.app.R.string.application_theme_not_set)
    }

    private fun selectedCoverGroupId(context: Context, isNight: Boolean): Long? {
        return context.getPrefString(
            if (isNight) PreferKey.coverCollectionNight else PreferKey.coverCollectionDay
        )?.toLongOrNull()
    }
}
