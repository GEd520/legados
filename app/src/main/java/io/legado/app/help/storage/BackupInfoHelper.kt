package io.legado.app.help.storage

import io.legado.app.data.appDb
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.getFolderNameNoCache
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.model.BookCover
import io.legado.app.model.upload.DirectLinkUploadRepository
import splitties.init.appCtx
import java.io.File

/**
 * 备份信息工具类
 * 直接统计当前会备份的数据，不需要解析 ZIP 文件。
 */
object BackupInfoHelper {

    data class BackupFileInfo(
        val fileName: String,
        val displayName: String,
        val size: Long,
        val selected: Boolean = true
    )

    data class BackupOverview(
        val items: List<BackupFileInfo>,
        val totalSize: Long,
        val selectedSize: Long
    )

    data class CategoryInfo(
        val name: String,
        val icon: String,
        val items: List<BackupFileInfo>,
        val totalSize: Long
    )

    private data class CategoryDef(
        val name: String,
        val icon: String,
        val keywords: List<String>
    )

    private val categoryConfig = listOf(
        CategoryDef("书籍相关", "📚", listOf("bookshelf", "bookmark", "bookGroup", "bookChapter", "readRecord", "bookCache", "bookChapterCache")),
        CategoryDef("源相关", "📡", listOf("bookSource", "rssSource", "rssStar", "sourceSub", "runtimeSourceCache")),
        CategoryDef("规则相关", "🔧", listOf("replaceRule", "txtTocRule", "dictRule", "keyboardAssist")),
        CategoryDef("语音相关", "🔊", listOf("httpTTS")),
        CategoryDef("配置相关", "⚙️", listOf("config", "videoConfig", "readConfig", "shareConfig", "coverConfig", "servers"))
    )

    val displayNameMap = mapOf(
        CoverGalleryRepository.backupDirName to "封面图集",
        "bookshelf.json" to "书架书籍",
        "bookmark.json" to "书签",
        "bookGroup.json" to "书籍分组",
        "bookChapter.json" to "章节目录",
        "bookSource.json" to "书源",
        "rssSources.json" to "订阅源",
        "rssStar.json" to "订阅收藏",
        "replaceRule.json" to "替换规则",
        "readRecord.json" to "阅读记录",
        "readRecordDetail.json" to "阅读详情",
        "readRecordSession.json" to "阅读时间线",
        "searchHistory.json" to "搜索历史",
        "txtTocRule.json" to "TXT 目录规则",
        "httpTTS.json" to "TTS 配置",
        "keyboardAssists.json" to "键盘辅助",
        "dictRule.json" to "词典规则",
        "servers.json" to "服务器配置",
        "runtimeSourceCache.json" to "书源运行数据",
        "bookCacheIndex.json" to "书籍缓存索引",
        "bookChapterCache.json" to "书籍章节目录",
        ReadBookConfig.configFileName to "阅读样式配置",
        ReadBookConfig.shareConfigFileName to "共享阅读配置",
        ThemeConfig.configFileName to "主题配置",
        BookCover.configFileName to "封面规则",
        "config.xml" to "应用设置",
        "videoConfig.xml" to "视频配置"
    )

    private fun isFileSelected(fileName: String): Boolean {
        if (fileName == DirectLinkUpload.ruleFileName) {
            return BackupSelectorConfig.isSelected("directLinkRule")
        }
        val key = BackupSelectorConfig.allItems.find { it.fileName == fileName }?.key
            ?: return true
        return BackupSelectorConfig.isSelected(key)
    }

    fun getBackupOverview(): BackupOverview {
        val items = mutableListOf<BackupFileInfo>()
        var totalSize = 0L
        var selectedSize = 0L

        val dbItems = listOf(
            Pair("bookshelf.json") { appDb.bookDao.all.size },
            Pair("bookmark.json") { appDb.bookmarkDao.all.size },
            Pair("bookGroup.json") { appDb.bookGroupDao.all.size },
            Pair("bookChapter.json") { appDb.bookChapterDao.getAll().size },
            Pair("bookSource.json") { appDb.bookSourceDao.all.size },
            Pair("rssSources.json") { appDb.rssSourceDao.all.size },
            Pair("rssStar.json") { appDb.rssStarDao.all.size },
            Pair("replaceRule.json") { appDb.replaceRuleDao.all.size },
            Pair("readRecord.json") { appDb.readRecordDao.all.size },
            Pair("readRecordDetail.json") { appDb.readRecordDao.getDetailsCount() },
            Pair("readRecordSession.json") { appDb.readRecordDao.getSessionsCount() },
            Pair("searchHistory.json") { appDb.searchKeywordDao.all.size },
            Pair("txtTocRule.json") { appDb.txtTocRuleDao.all.size },
            Pair("httpTTS.json") { appDb.httpTTSDao.all.size },
            Pair("keyboardAssists.json") { appDb.keyboardAssistsDao.all.size },
            Pair("dictRule.json") { appDb.dictRuleDao.all.size },
            Pair("servers.json") { appDb.serverDao.all.size }
        )

        dbItems.forEach { (fileName, countProvider) ->
            val count = countProvider()
            val displayName = displayNameMap[fileName] ?: fileName
            val estimatedSize = count * 200L
            val selected = isFileSelected(fileName)
            totalSize += estimatedSize
            if (selected) selectedSize += estimatedSize
            items.add(BackupFileInfo(fileName, displayName, estimatedSize, selected))
        }

        val runtimeCacheCount = appDb.cacheDao.getRuntimeSourceCaches(System.currentTimeMillis()).size
        run {
            val fileName = "runtimeSourceCache.json"
            val displayName = displayNameMap[fileName] ?: fileName
            val estimatedSize = runtimeCacheCount * 500L
            val selected = isFileSelected(fileName)
            totalSize += estimatedSize
            if (selected) selectedSize += estimatedSize
            items.add(BackupFileInfo(fileName, displayName, estimatedSize, selected))
        }

        // 书籍缓存
        run {
            val selectedBooks = BookCacheSelectorConfig.getSelectedBooks()
            val cacheDir = File(BookHelp.cachePath)
            var bookCacheSize = 0L
            var chapterCount = 0
            if (cacheDir.exists()) {
                selectedBooks.forEach { book ->
                    val folderName = book.getFolderNameNoCache()
                    val bookFolder = File(cacheDir, folderName)
                    if (bookFolder.exists()) {
                        bookCacheSize += bookFolder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        chapterCount += appDb.bookChapterDao.getChapterList(book.bookUrl).size
                    }
                }
            }
            val bookCacheSelected = isFileSelected("book_cache")
            val indexEstimatedSize = selectedBooks.size * 300L
            val chapterEstimatedSize = chapterCount * 200L
            totalSize += bookCacheSize + indexEstimatedSize + chapterEstimatedSize
            if (bookCacheSelected) selectedSize += bookCacheSize + indexEstimatedSize + chapterEstimatedSize
            items.add(BackupFileInfo("book_cache", "书籍缓存", bookCacheSize, bookCacheSelected))
            items.add(BackupFileInfo("bookChapterCache.json", "书籍章节目录", chapterEstimatedSize, bookCacheSelected))
            items.add(BackupFileInfo("bookCacheIndex.json", "书籍缓存索引", indexEstimatedSize, bookCacheSelected))
        }

        val configFiles = listOf(
            ReadBookConfig.configFileName,
            ReadBookConfig.shareConfigFileName,
            ThemeConfig.configFileName,
            BookCover.configFileName,
            "config.xml",
            "videoConfig.xml"
        )

        configFiles.forEach { fileName ->
            val file = File(appCtx.filesDir, fileName)
            val size = if (file.exists()) file.length() else 0L
            val selected = isFileSelected(fileName)
            totalSize += size
            if (selected) selectedSize += size
            val displayName = displayNameMap[fileName] ?: fileName
            items.add(BackupFileInfo(fileName, displayName, size, selected))
        }

        run {
            val fileName = DirectLinkUpload.ruleFileName
            val size = DirectLinkUploadRepository().createBackupJsonSync()?.length?.toLong() ?: 0L
            val selected = isFileSelected(fileName)
            totalSize += size
            if (selected) selectedSize += size
            items.add(BackupFileInfo(fileName, "直链上传配置", size, selected))
        }

        run {
            val bgSelected = BackupSelectorConfig.isSelected("backgroundImages")
            val bgFiles = Backup.getBackgroundImageFiles()
            val totalBgSize = bgFiles.sumOf { it.length() }
            totalSize += totalBgSize
            if (bgSelected) selectedSize += totalBgSize
            items.add(BackupFileInfo("backgroundImages", "阅读背景", totalBgSize, bgSelected))
        }

        run {
            val selected = BackupSelectorConfig.isSelected("coverGallery")
            val imageSize = appDb.coverGalleryDao.allImages.asSequence()
                .map { File(it.path) }
                .filter { it.exists() && it.isFile }
                .distinctBy { it.absolutePath }
                .sumOf { it.length() }
            val estimatedDataSize = appDb.coverGalleryDao.allGroups.size * 100L
            totalSize += imageSize
            totalSize += estimatedDataSize
            if (selected) selectedSize += imageSize + estimatedDataSize
            items.add(
                BackupFileInfo(
                    CoverGalleryRepository.backupDirName,
                    "封面图集",
                    imageSize + estimatedDataSize,
                    selected
                )
            )
        }

        return BackupOverview(items, totalSize, selectedSize)
    }

    fun categorizeItems(items: List<BackupFileInfo>, onlySelected: Boolean = false): List<CategoryInfo> {
        val filteredItems = if (onlySelected) items.filter { it.selected } else items
        val result = mutableListOf<CategoryInfo>()
        val assigned = mutableSetOf<String>()

        for (cfg in categoryConfig) {
            val matched = filteredItems.filter { item ->
                cfg.keywords.any { kw ->
                    item.fileName.lowercase().contains(kw.lowercase())
                } && !assigned.contains(item.fileName)
            }
            if (matched.isNotEmpty()) {
                matched.forEach { assigned.add(it.fileName) }
                result.add(
                    CategoryInfo(
                        name = cfg.name,
                        icon = cfg.icon,
                        items = matched,
                        totalSize = matched.sumOf { it.size }
                    )
                )
            }
        }

        val themeItems = filteredItems.filter {
            !assigned.contains(it.fileName) &&
                (it.fileName == "backgroundImages" || it.fileName == ThemeConfig.configFileName)
        }
        if (themeItems.isNotEmpty()) {
            result.add(
                CategoryInfo(
                    name = "主题",
                    icon = "🎨",
                    items = themeItems,
                    totalSize = themeItems.sumOf { it.size }
                )
            )
            themeItems.forEach { assigned.add(it.fileName) }
        }

        val remaining = filteredItems.filter { !assigned.contains(it.fileName) }
        if (remaining.isNotEmpty()) {
            result.add(
                CategoryInfo(
                    name = "其他",
                    icon = "📁",
                    items = remaining,
                    totalSize = remaining.sumOf { it.size }
                )
            )
        }

        return result
    }

    fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> String.format("%.2f MB", size / (1024.0 * 1024))
        }
    }
}
