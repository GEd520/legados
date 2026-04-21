package io.legado.app.help.storage

import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.model.BookCover
import io.legado.app.utils.FileUtils
import io.legado.app.utils.isContentScheme
import splitties.init.appCtx
import java.io.File
import java.util.zip.ZipFile

/**
 * 备份信息解析工具类
 * 用于解析本地备份ZIP文件，获取文件列表和大小信息
 */
object BackupInfoHelper {

    /**
     * 备份文件信息
     */
    data class BackupFileInfo(
        val fileName: String,
        val displayName: String,
        val size: Long
    )

    /**
     * 备份概览信息
     */
    data class BackupOverview(
        val zipFileName: String,
        val zipFileSize: Long,
        val createTime: Long,
        val items: List<BackupFileInfo>
    )

    /**
     * 分类信息
     */
    data class CategoryInfo(
        val name: String,
        val icon: String,
        val items: List<BackupFileInfo>,
        val totalSize: Long
    )

    private val categoryConfig = listOf(
        CategoryDef("书籍相关", "📚", listOf("bookshelf", "bookmark", "bookGroup", "readRecord")),
        CategoryDef("源相关", "📡", listOf("bookSource", "rssSource", "rssStar", "sourceSub")),
        CategoryDef("规则相关", "🔧", listOf("replaceRule", "txtTocRule", "dictRule", "keyboardAssist")),
        CategoryDef("语音相关", "🔊", listOf("httpTTS")),
        CategoryDef("配置相关", "⚙️", listOf("config", "videoConfig", "readConfig", "shareConfig", "themeConfig", "coverConfig", "servers")),
        CategoryDef("其他", "📁", listOf("searchHistory", "DirectLinkUpload"))
    )

    private data class CategoryDef(
        val name: String,
        val icon: String,
        val keywords: List<String>
    )

    private val displayNameMap = mapOf(
        "bookshelf.json" to "书架书籍",
        "bookmark.json" to "书签",
        "bookGroup.json" to "书籍分组",
        "bookSource.json" to "书源",
        "rssSources.json" to "RSS源",
        "rssStar.json" to "RSS收藏",
        "replaceRule.json" to "替换规则",
        "readRecord.json" to "阅读记录",
        "searchHistory.json" to "搜索历史",
        "sourceSub.json" to "订阅源",
        "txtTocRule.json" to "TXT目录规则",
        "httpTTS.json" to "TTS配置",
        "keyboardAssists.json" to "键盘辅助",
        "dictRule.json" to "词典规则",
        "servers.json" to "服务器配置",
        ReadBookConfig.configFileName to "阅读样式配置",
        ReadBookConfig.shareConfigFileName to "共享阅读配置",
        ThemeConfig.configFileName to "主题配置",
        BookCover.configFileName to "封面规则",
        "config.xml" to "应用设置",
        "videoConfig.xml" to "视频配置"
    )

    /**
     * 获取本地备份文件列表
     * @return 备份文件列表，按修改时间倒序
     */
    fun getBackupFiles(): List<File> {
        val files = mutableListOf<File>()

        // 默认备份目录
        appCtx.getExternalFilesDir(null)?.let { dir ->
            if (dir.exists()) {
                dir.listFiles()?.filter {
                    it.name.startsWith("backup") && it.name.endsWith(".zip")
                }?.let { files.addAll(it) }
            }
        }

        // 用户配置的备份路径
        val backupPath = AppConfig.backupPath
        if (!backupPath.isNullOrBlank() && !backupPath.isContentScheme()) {
            val customDir = File(backupPath)
            if (customDir.exists() && customDir.isDirectory) {
                customDir.listFiles()?.filter {
                    it.name.startsWith("backup") && it.name.endsWith(".zip")
                }?.let { files.addAll(it) }
            }
        }

        return files.sortedByDescending { it.lastModified() }
    }

    /**
     * 获取最新备份文件
     */
    fun getLatestBackupFile(): File? {
        return getBackupFiles().firstOrNull()
    }

    /**
     * 解析备份ZIP，获取文件列表
     */
    fun parseBackupZip(zipFile: File): BackupOverview? {
        if (!zipFile.exists()) return null

        val items = mutableListOf<BackupFileInfo>()

        try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory) {
                        val fileName = entry.name
                        val displayName = displayNameMap[fileName] ?: fileName
                        items.add(BackupFileInfo(
                            fileName = fileName,
                            displayName = displayName,
                            size = entry.size
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }

        return BackupOverview(
            zipFileName = zipFile.name,
            zipFileSize = zipFile.length(),
            createTime = zipFile.lastModified(),
            items = items.sortedByDescending { it.size }
        )
    }

    /**
     * 将文件列表按分类分组
     */
    fun categorizeItems(items: List<BackupFileInfo>): List<CategoryInfo> {
        val result = mutableListOf<CategoryInfo>()
        val assigned = mutableSetOf<String>()

        for (cfg in categoryConfig) {
            val matched = items.filter { item ->
                cfg.keywords.any { kw ->
                    item.fileName.lowercase().contains(kw.lowercase())
                } && !assigned.contains(item.fileName)
            }
            if (matched.isNotEmpty()) {
                matched.forEach { assigned.add(it.fileName) }
                result.add(CategoryInfo(
                    name = cfg.name,
                    icon = cfg.icon,
                    items = matched,
                    totalSize = matched.sumOf { it.size }
                ))
            }
        }

        // 未分类的放入其他
        val remaining = items.filter { !assigned.contains(it.fileName) }
        if (remaining.isNotEmpty()) {
            result.add(CategoryInfo(
                name = "其他",
                icon = "📁",
                items = remaining,
                totalSize = remaining.sumOf { it.size }
            ))
        }

        return result
    }

    /**
     * 格式化文件大小
     */
    fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> String.format("%.2f MB", size / (1024.0 * 1024))
        }
    }

    /**
     * 格式化时间
     */
    fun formatTime(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}
