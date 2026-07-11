package io.legado.app.help.config

import android.content.Context
import android.graphics.Color
import android.net.Uri
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
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import splitties.init.appCtx
import java.util.UUID
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

object ApplicationThemeManager {

    private const val fileName = "applicationThemes.json"
    private const val currentIdKey = "currentApplicationThemeId"
    private const val maxConfigBytes = 5L * 1024 * 1024
    private const val maxManifestBytes = 2L * 1024 * 1024
    private const val maxAssetBytes = 64L * 1024 * 1024
    private const val maxCoverImages = 500
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

    @Keep
    private data class PackageData(
        val version: Int = 1,
        val config: Config,
        val dayTopBar: TopBarConfig.Config? = null,
        val nightTopBar: TopBarConfig.Config? = null,
        val dayBottomBar: NavigationBarConfig? = null,
        val nightBottomBar: NavigationBarConfig? = null,
        val dayCover: CoverPayload? = null,
        val nightCover: CoverPayload? = null
    )

    @Keep
    private data class CoverPayload(
        val name: String,
        val images: List<String>
    )

    fun load(): MutableList<Config> {
        val file = File(filePath)
        if (!file.isFile) return mutableListOf()
        require(file.length() <= maxConfigBytes) { "应用主题配置文件过大" }
        val parsed = GSON.fromJsonArray<Config>(file.readText()).getOrElse {
            throw IllegalStateException("应用主题配置文件损坏，已保留原文件", it)
        }
        return parsed.map { sanitize(it) }.toMutableList()
    }

    fun currentId(context: Context): String = context.getPrefString(currentIdKey).orEmpty()

    fun find(id: String): Config? = load().firstOrNull { it.id == id }

    fun exportCurrent(context: Context): File {
        val current = load().firstOrNull { isCurrent(context, it) }
            ?: captureCurrent(context, appCtx.getString(io.legado.app.R.string.application_theme_manage))
        validateForApply(context, current)
        val dir = appCtx.cacheDir.resolve("applicationThemeExports").apply { mkdirs() }
        val exportName = current.name.normalizeFileName().ifBlank { "application_theme" }
        return dir.resolve("$exportName.zip").apply {
            ZipOutputStream(outputStream().buffered()).use { zip ->
                val packagedConfig = current.copy(
                    dayTheme = packageTheme(zip, current.dayTheme, "themes/day"),
                    nightTheme = packageTheme(zip, current.nightTheme, "themes/night")
                )
                val data = PackageData(
                    config = packagedConfig,
                    dayTopBar = packageTopBar(zip, context, false, current.dayTopBarDir, "topbar/day"),
                    nightTopBar = packageTopBar(zip, context, true, current.nightTopBarDir, "topbar/night"),
                    dayBottomBar = packageBottomBar(zip, context, false, current.dayBottomBarId, "bottombar/day"),
                    nightBottomBar = packageBottomBar(zip, context, true, current.nightBottomBarId, "bottombar/night"),
                    dayCover = packageCover(zip, current.dayCoverGroupId, "covers/day"),
                    nightCover = packageCover(zip, current.nightCoverGroupId, "covers/night")
                )
                zip.putNextEntry(ZipEntry("application_theme.json"))
                zip.write(GSON.toJson(data).toByteArray())
                zip.closeEntry()
            }
        }
    }

    suspend fun importFile(file: File): Config {
        val isZip = file.inputStream().use { input ->
            input.read() == 'P'.code && input.read() == 'K'.code
        }
        if (isZip) return importZip(file)
        require(file.length() <= maxManifestBytes) { "应用主题文件过大" }
        val imported = sanitize(
            GSON.fromJson(file.readText(), Config::class.java)
                ?: throw IllegalArgumentException("Invalid application theme")
        )
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

    private suspend fun importZip(file: File): Config {
        val temp = appCtx.cacheDir.resolve("applicationThemeImport/${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            ZipFile(file).use { zip ->
                val manifest = zip.getEntry("application_theme.json")
                    ?: throw IllegalArgumentException("缺少 application_theme.json")
                require(manifest.size in 0..maxManifestBytes) { "应用主题清单过大" }
                val data = zip.getInputStream(manifest).bufferedReader().use {
                    GSON.fromJson(it, PackageData::class.java)
                } ?: throw IllegalArgumentException("应用主题包格式错误")
                require(data.version == 1) { "不支持的应用主题包版本" }
                validatePackage(zip, data)
                val source = sanitize(data.config)
                val dayTheme = restoreThemeAsset(zip, temp, source.dayTheme, false)
                val nightTheme = restoreThemeAsset(zip, temp, source.nightTheme, true)
                val dayTop = restoreTopBar(zip, temp, false, source.dayTopBarDir, data.dayTopBar)
                val nightTop = restoreTopBar(zip, temp, true, source.nightTopBarDir, data.nightTopBar)
                val dayBottom = restoreBottomBar(zip, temp, false, data.dayBottomBar)
                val nightBottom = restoreBottomBar(zip, temp, true, data.nightBottomBar)
                val dayCover = restoreCover(zip, temp, data.dayCover)
                val nightCover = restoreCover(zip, temp, data.nightCover)
                return addImported(
                    source.copy(
                        dayTheme = dayTheme,
                        nightTheme = nightTheme,
                        dayTopBarDir = dayTop,
                        nightTopBarDir = nightTop,
                        dayBottomBarId = dayBottom,
                        nightBottomBarId = nightBottom,
                        dayCoverGroupId = dayCover,
                        nightCoverGroupId = nightCover
                    )
                )
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun addImported(imported: Config): Config {
        val items = load()
        val baseName = imported.name.trim().ifBlank { appCtx.getString(io.legado.app.R.string.application_theme_manage) }
        var name = baseName
        var suffix = 2
        while (items.any { it.name == name }) name = "$baseName ${suffix++}"
        val next = imported.copy(id = UUID.randomUUID().toString(), name = name, updatedAt = System.currentTimeMillis())
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
            (config.dayCoverGroupId == null || selectedCoverGroupId(context, false) == config.dayCoverGroupId) &&
            (config.nightCoverGroupId == null || selectedCoverGroupId(context, true) == config.nightCoverGroupId)
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
        validateForApply(context, config)
        val wasNight = AppConfig.isNightTheme
        config.dayTheme?.let { ThemeConfig.applyConfig(context, it.copy(isNightTheme = false), applyNow = false) }
        config.nightTheme?.let { ThemeConfig.applyConfig(context, it.copy(isNightTheme = true), applyNow = false) }

        applyTopBar(context, false, config.dayTopBarDir)
        applyTopBar(context, true, config.nightTopBarDir)
        applyBottomBar(context, false, config.dayBottomBarId)
        applyBottomBar(context, true, config.nightBottomBarId)

        val coverRepository = CoverGalleryRepository()
        config.dayCoverGroupId?.let { coverRepository.setSelectedGroup(false, it) }
        config.nightCoverGroupId?.let { coverRepository.setSelectedGroup(true, it) }

        if (config.dayTheme != null && context.getPrefString(PreferKey.dThemeName) != config.dayTheme?.themeName) {
            throw IllegalStateException("日间主题应用失败")
        }
        if (config.nightTheme != null && context.getPrefString(PreferKey.dNThemeName) != config.nightTheme?.themeName) {
            throw IllegalStateException("夜间主题应用失败")
        }

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
        val target = FileUtils.createFileIfNotExist(filePath)
        val temp = File("$filePath.tmp")
        temp.writeText(GSON.toJson(items))
        if (target.exists()) target.copyTo(File("$filePath.bak"), overwrite = true)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
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

    private fun packageTheme(zip: ZipOutputStream, theme: ThemeConfig.Config?, prefix: String): ThemeConfig.Config? {
        theme ?: return null
        val path = theme.backgroundImgPath ?: return theme.copy()
        if (path.startsWith("http", true)) return theme.copy()
        val source = File(path).takeIf { it.isFile } ?: appCtx.externalFiles
            .getFile(if (theme.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage)
            .getFile(path)
            .takeIf { it.isFile }
        val entry = source?.let { addZipFile(zip, it, "$prefix/background") }
        return theme.copy(backgroundImgPath = entry)
    }

    private fun packageTopBar(
        zip: ZipOutputStream,
        context: Context,
        isNight: Boolean,
        dirName: String,
        prefix: String
    ): TopBarConfig.Config? {
        if (dirName.isBlank() || dirName == TopBarConfig.DEFAULT_DIR_NAME) return null
        val entry = TopBarConfig.loadEntries(context, isNight).firstOrNull { it.dirName == dirName } ?: return null
        val wallpaper = entry.config.wallpaperPath?.let { path ->
            val file = File(path).takeIf { it.isFile }
                ?: entry.localDir?.resolve(path)?.takeIf { it.isFile }
            file?.let { addZipFile(zip, it, "$prefix/wallpaper") }
        }
        return entry.config.copy(wallpaperPath = wallpaper)
    }

    private fun packageBottomBar(
        zip: ZipOutputStream,
        context: Context,
        isNight: Boolean,
        id: String?,
        prefix: String
    ): NavigationBarConfig? {
        val config = NavigationBarConfig.loadConfigs(context)
            .firstOrNull { it.isNight == isNight && it.id == id } ?: return null
        val icons = config.icons.mapNotNull { (key, path) ->
            File(path).takeIf { it.isFile }?.let { key to addZipFile(zip, it, "$prefix/$key") }
        }.toMap()
        return config.copy(icons = icons)
    }

    private fun packageCover(zip: ZipOutputStream, groupId: Long?, prefix: String): CoverPayload? {
        val group = CoverGalleryRepository().allGroupsWithImages()
            .firstOrNull { it.group.id == groupId } ?: return null
        val images = group.images.mapIndexedNotNull { index, image ->
            File(image.path).takeIf { it.isFile }?.let { addZipFile(zip, it, "$prefix/$index") }
        }
        return CoverPayload(group.group.name, images)
    }

    private fun addZipFile(zip: ZipOutputStream, file: File, basePath: String): String {
        val extension = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        val path = "$basePath$extension"
        zip.putNextEntry(ZipEntry(path))
        file.inputStream().buffered().use { it.copyTo(zip) }
        zip.closeEntry()
        return path
    }

    private fun restoreThemeAsset(
        zip: ZipFile,
        temp: File,
        theme: ThemeConfig.Config?,
        isNight: Boolean
    ): ThemeConfig.Config? {
        theme ?: return null
        val path = theme.backgroundImgPath ?: return theme.copy(isNightTheme = isNight)
        if (path.startsWith("http", true)) return theme.copy(isNightTheme = isNight)
        val extracted = extractAsset(zip, temp, path) ?: return theme.copy(isNightTheme = isNight, backgroundImgPath = null)
        val dir = appCtx.externalFiles.getFile(if (isNight) PreferKey.bgImageN else PreferKey.bgImage).apply { mkdirs() }
        val target = dir.getFile("application_theme_${UUID.randomUUID()}.${extracted.extension.ifBlank { "jpg" }}")
        extracted.copyTo(target, overwrite = true)
        return theme.copy(isNightTheme = isNight, backgroundImgPath = target.absolutePath)
    }

    private fun restoreTopBar(
        zip: ZipFile,
        temp: File,
        isNight: Boolean,
        originalDir: String,
        packaged: TopBarConfig.Config?
    ): String {
        if (originalDir.isBlank()) return ""
        if (originalDir == TopBarConfig.DEFAULT_DIR_NAME) return TopBarConfig.DEFAULT_DIR_NAME
        val source = packaged ?: throw IllegalArgumentException("应用主题包缺少顶栏配置")
        val wallpaper = source.wallpaperPath?.let { extractAsset(zip, temp, it)?.absolutePath }
        val usedNames = TopBarConfig.loadEntries(appCtx, isNight).map { it.config.name }.toSet()
        val name = uniqueName(source.name, usedNames)
        return TopBarConfig.addOrUpdate(
            source.copy(name = name, isNightMode = isNight, wallpaperPath = wallpaper)
        ).dirName
    }

    private fun restoreBottomBar(
        zip: ZipFile,
        temp: File,
        isNight: Boolean,
        packaged: NavigationBarConfig?
    ): String? {
        packaged ?: return null
        if (packaged.isBuiltin) {
            return NavigationBarConfig.loadConfigs(appCtx)
                .firstOrNull { it.isNight == isNight && it.isBuiltin }?.id
        }
        val id = UUID.randomUUID().toString()
        val iconDir = appCtx.externalFiles.getFile("navigationBarIcons", id).apply { mkdirs() }
        val icons = packaged.icons.mapNotNull { (key, path) ->
            extractAsset(zip, temp, path)?.let { source ->
                val target = iconDir.getFile("${key}.${source.extension.ifBlank { "png" }}")
                source.copyTo(target, overwrite = true)
                key to target.absolutePath
            }
        }.toMap()
        val existing = NavigationBarConfig.loadConfigs(appCtx)
        val name = uniqueName(packaged.name, existing.filter { it.isNight == isNight }.map { it.name }.toSet())
        val next = packaged.copy(id = id, name = name, isNight = isNight, isBuiltin = false, icons = icons)
        existing.add(next)
        NavigationBarConfig.saveConfigs(appCtx, existing)
        return id
    }

    private suspend fun restoreCover(zip: ZipFile, temp: File, payload: CoverPayload?): Long? {
        payload ?: return null
        val repository = CoverGalleryRepository()
        val usedNames = repository.allGroupsWithImages().map { it.group.name }.toSet()
        val groupId = repository.addGroup(uniqueName(payload.name, usedNames))
        val uris = payload.images.mapNotNull { extractAsset(zip, temp, it) }.map(Uri::fromFile)
        if (uris.isNotEmpty()) repository.addImages(appCtx, groupId, uris)
        return groupId
    }

    private fun extractAsset(zip: ZipFile, temp: File, path: String): File? {
        val entry = zip.getEntry(path) ?: return null
        require(!entry.isDirectory) { "应用主题资源格式错误" }
        require(entry.size in 0..maxAssetBytes) { "应用主题资源过大" }
        val target = temp.resolve("${UUID.randomUUID()}.${path.substringAfterLast('.', "bin")}")
        zip.getInputStream(entry).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun uniqueName(base: String, used: Set<String>): String {
        val normalized = base.trim().ifBlank { "应用主题组件" }
        if (normalized !in used) return normalized
        var index = 2
        while ("$normalized $index" in used) index++
        return "$normalized $index"
    }

    private fun validatePackage(zip: ZipFile, data: PackageData) {
        val assetPaths = buildList {
            listOfNotNull(data.config.dayTheme, data.config.nightTheme).forEach { theme ->
                theme.backgroundImgPath?.takeUnless { it.startsWith("http", true) }?.let(::add)
            }
            listOfNotNull(data.dayTopBar, data.nightTopBar).forEach { topBar ->
                topBar.wallpaperPath?.let(::add)
            }
            listOfNotNull(data.dayBottomBar, data.nightBottomBar).forEach { bottomBar ->
                addAll(bottomBar.icons.values)
            }
            listOfNotNull(data.dayCover, data.nightCover).forEach { cover ->
                require(cover.images.size <= maxCoverImages) { "封面图集图片数量过多" }
                addAll(cover.images)
            }
        }
        require(assetPaths.distinct().size == assetPaths.size) { "应用主题包包含重复资源" }
        assetPaths.forEach { path ->
            val entry = zip.getEntry(path) ?: throw IllegalArgumentException("应用主题包缺少资源: $path")
            require(!entry.isDirectory && entry.size in 0..maxAssetBytes) { "应用主题资源无效: $path" }
        }
    }

    private fun validateForApply(context: Context, config: Config) {
        listOfNotNull(config.dayTheme, config.nightTheme).forEach { theme ->
            runCatching {
                Color.parseColor(theme.primaryColor)
                Color.parseColor(theme.accentColor)
                Color.parseColor(theme.backgroundColor)
                Color.parseColor(theme.bottomBackground)
            }.getOrElse { throw IllegalArgumentException("主题颜色格式无效: ${theme.themeName}", it) }
            theme.backgroundImgPath?.takeIf { File(it).isAbsolute }?.let { path ->
                require(File(path).isFile) { "主题背景图片不存在: ${theme.themeName}" }
            }
        }
        if (config.dayTopBarDir.isNotBlank()) {
            require(TopBarConfig.loadEntries(context, false).any { it.dirName == config.dayTopBarDir }) { "日间顶栏不存在" }
        }
        if (config.nightTopBarDir.isNotBlank()) {
            require(TopBarConfig.loadEntries(context, true).any { it.dirName == config.nightTopBarDir }) { "夜间顶栏不存在" }
        }
        config.dayBottomBarId?.let { id ->
            require(NavigationBarConfig.loadConfigs(context).any { !it.isNight && it.id == id }) { "日间底栏不存在" }
        }
        config.nightBottomBarId?.let { id ->
            require(NavigationBarConfig.loadConfigs(context).any { it.isNight && it.id == id }) { "夜间底栏不存在" }
        }
        val groupIds = CoverGalleryRepository().allGroupsWithImages().map { it.group.id }.toSet()
        config.dayCoverGroupId?.let { require(it in groupIds) { "日间封面图集不存在" } }
        config.nightCoverGroupId?.let { require(it in groupIds) { "夜间封面图集不存在" } }
    }

    private fun sanitize(source: Config): Config {
        val id = runCatching { source.id }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        val name = runCatching { source.name }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("应用主题名称无效")
        return Config(
            id = id,
            name = name,
            dayTheme = runCatching { source.dayTheme }.getOrNull(),
            nightTheme = runCatching { source.nightTheme }.getOrNull(),
            dayTopBarDir = runCatching { source.dayTopBarDir }.getOrNull().orEmpty(),
            nightTopBarDir = runCatching { source.nightTopBarDir }.getOrNull().orEmpty(),
            dayBottomBarId = runCatching { source.dayBottomBarId }.getOrNull(),
            nightBottomBarId = runCatching { source.nightBottomBarId }.getOrNull(),
            dayCoverGroupId = runCatching { source.dayCoverGroupId }.getOrNull(),
            nightCoverGroupId = runCatching { source.nightCoverGroupId }.getOrNull(),
            updatedAt = runCatching { source.updatedAt }.getOrDefault(System.currentTimeMillis())
        )
    }
}
