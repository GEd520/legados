package io.legado.app.help.config

import android.content.Context
import android.graphics.Color
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefString
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.putPrefString
import splitties.init.appCtx
import java.io.File

object TopBarConfig {

    const val DEFAULT_DIR_NAME = "default"
    const val STYLE_DEFAULT = "default"
    const val STYLE_REGULAR = "regular"

    private const val packageFileName = "top_bar.json"
    private const val activeSnapshotDirName = "_active"
    private const val legacyConfigsKey = "customTopBarConfigs"
    private const val legacyActiveDayKey = "activeDayTopBarId"
    private const val legacyActiveNightKey = "activeNightTopBarId"
    private const val legacyMigratedKey = "topBarLegacyMigrated"

    private const val activeDayKey = PreferKey.topBarPackageDay
    private const val activeNightKey = PreferKey.topBarPackageNight

    val rootDir: File
        get() = appCtx.externalFiles.getFile("topBarPackages")

    private val tempDir: File
        get() = appCtx.externalFiles.getFile("topBarTemp").apply { mkdirs() }

    @Keep
    data class Config(
        var name: String,
        var isNightMode: Boolean,
        var style: String = STYLE_DEFAULT,
        var tagBarColor: Int? = null,
        var tagBarAlpha: Int = 100,
        var tagSelectedColor: Int? = null,
        var tagSelectedAlpha: Int = 100,
        var wallpaperPath: String? = null,
        var wallpaperAlpha: Int = 100,
        var backgroundColor: Int? = null,
        var cornerScale: Float? = null,
        var expandFiltersByDefault: Boolean = false,
        var updatedAt: Long = System.currentTimeMillis()
    ) {
        fun toJson(): String = GSON.toJson(this)
    }

    data class Entry(
        val config: Config,
        val source: Source,
        val dirName: String,
        val localDir: File? = null
    )

    enum class Source { BUILTIN, LOCAL }

    @Keep
    private data class LegacyConfig(
        var id: String = "",
        var name: String = "",
        var isNight: Boolean = false,
        var isBuiltin: Boolean = false,
        var style: String = STYLE_DEFAULT,
        var cornerScale: Float = 1f,
        var backgroundColor: Int? = null,
        var wallpaperPath: String? = null,
        var wallpaperAlpha: Int = 100,
        var tagBarColor: Int? = null,
        var tagBarAlpha: Int = 100,
        var tagSelectedColor: Int? = null,
        var tagSelectedAlpha: Int = 100,
        var expandFiltersByDefault: Boolean = false,
        var updatedAt: Long = System.currentTimeMillis()
    )

    fun defaultConfig(context: Context, isNight: Boolean): Config {
        return Config(
            name = defaultName(isNight),
            isNightMode = isNight,
            tagBarColor = context.primaryColor,
            tagSelectedColor = context.primaryColor,
            backgroundColor = defaultBackgroundColor(isNight),
            cornerScale = 1f,
            updatedAt = 0L
        )
    }

    fun currentConfig(context: Context, isNight: Boolean = AppConfig.isNightTheme): Config {
        return currentEntry(context, isNight).config
    }

    fun activeDirName(isNight: Boolean): String {
        return appCtx.getPrefString(if (isNight) activeNightKey else activeDayKey, DEFAULT_DIR_NAME)
            ?.ifBlank { DEFAULT_DIR_NAME }
            ?: DEFAULT_DIR_NAME
    }

    fun currentSignature(isNight: Boolean): String {
        val dirName = activeDirName(isNight)
        if (dirName == DEFAULT_DIR_NAME) return "$isNight|$DEFAULT_DIR_NAME"
        val configFile = File(activeLocalDir(isNight), packageFileName)
            .takeIf { it.exists() }
            ?: File(localDir(isNight, dirName), packageFileName)
        return "$isNight|$dirName|${configFile.lastModified()}"
    }

    fun currentEntry(context: Context, isNight: Boolean): Entry {
        migrateLegacyIfNeeded(context)
        val dirName = activeDirName(isNight)
        if (dirName == DEFAULT_DIR_NAME) return defaultEntry(context, isNight)
        readEntry(activeLocalDir(isNight))?.let {
            return it.copy(dirName = dirName)
        }
        val entry = readEntry(localDir(isNight, dirName)) ?: return defaultEntry(context, isNight)
        runCatching { writeActiveSnapshot(entry) }
        return readEntry(activeLocalDir(isNight))?.copy(dirName = dirName) ?: entry
    }

    fun currentWallpaperFile(context: Context, isNight: Boolean): File? {
        val entry = currentEntry(context, isNight)
        val path = entry.config.wallpaperPath?.takeIf { it.isNotBlank() } ?: return null
        val file = File(path)
        val resolved = if (file.isAbsolute) {
            file
        } else {
            File(entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName), path)
        }
        return resolved.takeIf { it.exists() && it.isFile }
    }

    fun loadEntries(context: Context, isNight: Boolean): List<Entry> {
        migrateLegacyIfNeeded(context)
        return buildList {
            add(defaultEntry(context, isNight))
            addAll(loadLocal(isNight))
        }.sortedWith(
            compareBy<Entry> { it.dirName != DEFAULT_DIR_NAME }
                .thenByDescending { it.config.updatedAt }
                .thenBy { it.config.name }
                .thenBy { it.dirName }
        )
    }

    fun addOrUpdate(config: Config, oldEntry: Entry? = null): Entry {
        val normalized = normalizeConfig(config)
        val name = normalized.name.trim().ifBlank { defaultName(normalized.isNightMode) }
        val keepOldDir = oldEntry != null &&
            oldEntry.dirName.isNotBlank() &&
            oldEntry.dirName != DEFAULT_DIR_NAME
        val dirName = if (keepOldDir) {
            oldEntry.dirName
        } else {
            name.normalizeFileName().ifBlank { "top_bar_${System.currentTimeMillis()}" }
        }
        if (!keepOldDir && readEntry(localDir(normalized.isNightMode, dirName)) != null) {
            throw IllegalArgumentException(appCtx.getString(R.string.top_bar_name_exists))
        }
        val dir = localDir(normalized.isNightMode, dirName).apply { mkdirs() }
        val next = normalized.copy(
            name = name,
            wallpaperPath = normalizeWallpaperPath(normalized.wallpaperPath, dir),
            updatedAt = System.currentTimeMillis()
        )
        File(dir, packageFileName).writeText(GSON.toJson(next))
        return Entry(next, Source.LOCAL, dirName, localDir = dir)
    }

    fun apply(entry: Entry) {
        if (entry.dirName == DEFAULT_DIR_NAME) {
            FileUtils.delete(activeLocalDir(entry.config.isNightMode), deleteRootDir = true)
            appCtx.putPrefString(
                if (entry.config.isNightMode) activeNightKey else activeDayKey,
                DEFAULT_DIR_NAME
            )
            return
        }
        writeActiveSnapshot(entry)
        appCtx.putPrefString(
            if (entry.config.isNightMode) activeNightKey else activeDayKey,
            entry.dirName
        )
    }

    fun deleteLocal(entry: Entry) {
        if (entry.dirName == DEFAULT_DIR_NAME) return
        FileUtils.delete(entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName), deleteRootDir = true)
        resetActiveIfNeeded(entry)
    }

    fun exportZip(entry: Entry): File {
        val dir = entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName)
        val zipFile = tempDir.getFile("${entry.dirName}.zip")
        if (zipFile.exists()) zipFile.delete()
        ZipUtils.zipFile(dir, zipFile)
        return zipFile
    }

    fun importZip(zipFile: File): Entry {
        val unzipDir = tempDir.getFile("import_${System.currentTimeMillis()}").apply {
            if (exists()) FileUtils.delete(this, deleteRootDir = true)
            mkdirs()
        }
        return try {
            ZipUtils.unZipToPath(zipFile, unzipDir)
            val packageFile = unzipDir.walkTopDown().firstOrNull { it.isFile && it.name == packageFileName }
                ?: throw IllegalArgumentException(appCtx.getString(R.string.top_bar_config_missing))
            val config = normalizeConfig(GSON.fromJsonObject<Config>(packageFile.readText()).getOrThrow())
            config.updatedAt = System.currentTimeMillis()
            val dirName = uniqueDirName(
                config.isNightMode,
                config.name.normalizeFileName().ifBlank { "top_bar_${System.currentTimeMillis()}" }
            )
            val targetDir = localDir(config.isNightMode, dirName)
            targetDir.mkdirs()
            packageFile.parentFile?.copyRecursively(targetDir, overwrite = true)
            val finalConfig = config.copy(wallpaperPath = normalizeWallpaperPath(config.wallpaperPath, targetDir))
            File(targetDir, packageFileName).writeText(GSON.toJson(finalConfig))
            Entry(finalConfig, Source.LOCAL, dirName, localDir = targetDir)
        } finally {
            FileUtils.delete(unzipDir, deleteRootDir = true)
        }
    }

    fun importJson(json: String, isNight: Boolean): Entry {
        val config = GSON.fromJsonObject<Config>(json).getOrNull()
            ?: GSON.fromJsonObject<LegacyConfig>(json).getOrThrow().toConfig()
        config.isNightMode = isNight
        return addOrUpdate(config)
    }

    fun opacityToAlpha(opacity: Int): Int {
        return opacity.coerceIn(0, 100) * 255 / 100
    }

    fun withOpacity(color: Int, opacity: Int): Int {
        val alpha = opacityToAlpha(opacity)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun defaultBackgroundColor(isNight: Boolean): Int {
        return if (isNight) Color.BLACK else Color.WHITE
    }

    fun resolveBackgroundColor(config: Config): Int {
        return config.backgroundColor ?: defaultBackgroundColor(config.isNightMode)
    }

    fun resolveCornerScale(config: Config): Float {
        return config.cornerScale ?: 1f
    }

    private fun defaultEntry(context: Context, isNight: Boolean): Entry {
        return Entry(defaultConfig(context, isNight), Source.BUILTIN, DEFAULT_DIR_NAME)
    }

    private fun loadLocal(isNight: Boolean): List<Entry> {
        return typeDir(isNight).listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { readEntry(it) }
            .orEmpty()
    }

    private fun readEntry(dir: File): Entry? {
        val file = File(dir, packageFileName)
        if (!file.exists()) return null
        val config = GSON.fromJsonObject<Config>(file.readText()).getOrNull()?.let(::normalizeConfig)
            ?: return null
        return Entry(config, Source.LOCAL, dir.name, localDir = dir)
    }

    private fun normalizeConfig(config: Config): Config {
        config.style = when (config.style) {
            STYLE_DEFAULT, STYLE_REGULAR -> config.style
            "immersive", "flow" -> STYLE_REGULAR
            else -> STYLE_DEFAULT
        }
        config.tagBarAlpha = config.tagBarAlpha.coerceIn(0, 100)
        config.tagSelectedAlpha = config.tagSelectedAlpha.coerceIn(0, 100)
        config.wallpaperAlpha = config.wallpaperAlpha.coerceIn(0, 100)
        config.wallpaperPath = config.wallpaperPath?.takeIf { it.isNotBlank() }
        config.cornerScale = config.cornerScale?.coerceIn(0f, 3f)
        return config
    }

    private fun normalizeWallpaperPath(path: String?, dir: File): String? {
        val value = path?.takeIf { it.isNotBlank() } ?: return null
        val source = File(value)
        if (!source.isAbsolute) return value
        if (!source.exists() || !source.isFile) return null
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("top_bar_wallpaper.") }
            ?.forEach { it.delete() }
        val suffix = source.extension.takeIf { it.isNotBlank() } ?: "jpg"
        val target = File(dir, "top_bar_wallpaper.$suffix")
        if (source.absolutePath != target.absolutePath) {
            source.copyTo(target, overwrite = true)
        }
        return target.name
    }

    private fun resetActiveIfNeeded(entry: Entry) {
        if (activeDirName(entry.config.isNightMode) == entry.dirName) {
            FileUtils.delete(activeLocalDir(entry.config.isNightMode), deleteRootDir = true)
            appCtx.putPrefString(
                if (entry.config.isNightMode) activeNightKey else activeDayKey,
                DEFAULT_DIR_NAME
            )
        }
    }

    private fun writeActiveSnapshot(entry: Entry) {
        val sourceDir = entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName)
        val targetDir = activeLocalDir(entry.config.isNightMode)
        if (targetDir.exists()) {
            FileUtils.delete(targetDir, deleteRootDir = true)
        }
        sourceDir.copyRecursively(targetDir, overwrite = true)
    }

    private fun activeLocalDir(isNight: Boolean): File {
        return rootDir.getFile(activeSnapshotDirName)
            .getFile(if (isNight) "night" else "day")
    }

    private fun localDir(isNight: Boolean, dirName: String): File = typeDir(isNight).getFile(dirName)

    private fun uniqueDirName(isNight: Boolean, preferred: String): String {
        val clean = preferred.normalizeFileName().ifBlank { "top_bar_${System.currentTimeMillis()}" }
        var candidate = clean
        var index = 1
        while (localDir(isNight, candidate).exists()) {
            candidate = "${clean}_$index"
            index++
        }
        return candidate
    }

    private fun typeDir(isNight: Boolean): File {
        return rootDir.getFile(if (isNight) "night" else "day").apply { mkdirs() }
    }

    private fun defaultName(isNight: Boolean): String {
        return appCtx.getString(
            if (isNight) R.string.top_bar_night_default_name else R.string.top_bar_day_default_name
        )
    }

    private fun migrateLegacyIfNeeded(context: Context) {
        if (appCtx.getPrefString(legacyMigratedKey, "").orEmpty().isNotBlank()) return
        val legacyJsons = context.getPrefString(legacyConfigsKey)
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            .orEmpty()
        legacyJsons.forEach { json ->
            runCatching {
                val legacy = GSON.fromJsonObject<LegacyConfig>(json).getOrThrow()
                if (!legacy.isBuiltin) addOrUpdate(legacy.toConfig())
            }
        }
        migrateLegacyActive(false)
        migrateLegacyActive(true)
        appCtx.putPrefString(legacyMigratedKey, "1")
    }

    private fun migrateLegacyActive(isNight: Boolean) {
        val legacyId = appCtx.getPrefString(if (isNight) legacyActiveNightKey else legacyActiveDayKey)
            ?.takeIf { it.isNotBlank() }
            ?: return
        val entry = loadLocal(isNight).firstOrNull { it.dirName == legacyId || it.config.name == legacyId }
            ?: return
        appCtx.putPrefString(if (isNight) activeNightKey else activeDayKey, entry.dirName)
    }

    private fun LegacyConfig.toConfig(): Config {
        return Config(
            name = name.ifBlank { defaultName(isNight) },
            isNightMode = isNight,
            style = style,
            tagBarColor = tagBarColor,
            tagBarAlpha = tagBarAlpha,
            tagSelectedColor = tagSelectedColor,
            tagSelectedAlpha = tagSelectedAlpha,
            wallpaperPath = wallpaperPath,
            wallpaperAlpha = wallpaperAlpha,
            backgroundColor = backgroundColor,
            cornerScale = cornerScale,
            expandFiltersByDefault = expandFiltersByDefault,
            updatedAt = updatedAt
        )
    }
}
