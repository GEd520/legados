package io.legado.app.help.storage

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.CoverGalleryGroup
import io.legado.app.data.entities.CoverGalleryImage
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.AppCacheManager
import io.legado.app.help.CacheManager
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.upType
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.getFolderNameNoCache
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.model.VideoPlay.VIDEO_PREF_NAME
import io.legado.app.model.BookCover
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.upload.DirectLinkUploadRepository
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.ui.book.read.config.HighlightRuleStore
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFolderIfNotExist
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.openInputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream

/**
 * 恢复管理类
 * 
 * 负责从备份文件恢复应用数据，包括：
 * - 解压备份ZIP文件
 * - 恢复数据库数据（书籍、书签、书源等）
 * - 恢复SharedPreferences配置
 * - 恢复自定义配置文件
 * 
 * 恢复流程：
 * 1. 解压ZIP文件到临时目录
 * 2. 读取JSON文件并导入数据库
 * 3. 恢复SharedPreferences配置
 * 4. 应用主题和阅读配置
 * 5. 清理临时文件
 * 
 * 特殊处理：
 * - 书籍数据：支持忽略本地书籍，更新已存在书籍
 * - 阅读记录：恢复前清空本地记录，再导入备份记录
 * - 服务器配置：需要解密
 * - WebDav密码：需要解密
 */
object Restore {
    private const val runtimeSourceCacheFileName = "runtimeSourceCache.json"
    private const val bookCacheFolderName = "book_cache"
    private const val bookCacheIndexFileName = "bookCacheIndex.json"
    private const val bookChapterFileName = "bookChapter.json"
    private const val legacyDirectLinkRuleFileName = "directLinkRule.json"

    private const val TAG = "Restore"
    private val themeRestorePrefKeys = arrayOf(
        PreferKey.dThemeName,
        PreferKey.dNThemeName,
        PreferKey.cPrimary,
        PreferKey.cAccent,
        PreferKey.cBackground,
        PreferKey.cBBackground,
        PreferKey.bgImage,
        PreferKey.bgImageBlurring,
        PreferKey.tNavBar,
        PreferKey.cNPrimary,
        PreferKey.cNAccent,
        PreferKey.cNBackground,
        PreferKey.cNBBackground,
        PreferKey.bgImageN,
        PreferKey.bgImageNBlurring,
        PreferKey.tNavBarN
    )

    /**
     * 从URI恢复备份
     * 支持SAF（Storage Access Framework）和普通文件路径
     * 
     * @param context Android Context
     * @param uri 备份文件URI
     */
    suspend fun restore(context: Context, uri: Uri) {
        LogUtils.d(TAG, "开始恢复备份 uri:$uri")
        var restoreStarted = false
        kotlin.runCatching {
            Backup.withStorageLock {
                FileUtils.delete(Backup.backupPath)
                if (uri.isContentScheme()) {
                    DocumentFile.fromSingleUri(context, uri)!!.openInputStream()!!.use {
                        ZipUtils.unZipToPath(it, Backup.backupPath)
                    }
                } else {
                    ZipUtils.unZipToPath(File(uri.path!!), Backup.backupPath)
                }
                restoreStarted = true
                restore(Backup.backupPath)
                LocalConfig.lastBackup = System.currentTimeMillis()
            }
        }.onFailure {
            if (restoreStarted) {
                appCtx.toastOnUi("恢复备份出错\n${it.localizedMessage}")
                AppLog.put("恢复备份出错\n${it.localizedMessage}", it)
            } else {
                AppLog.put("复制解压文件出错\n${it.localizedMessage}", it)
            }
        }
    }

    /**
     * 带锁的恢复方法
     * 使用互斥锁确保同一时间只有一个恢复操作在执行
     * 
     * @param path 备份文件解压后的目录路径
     */
    suspend fun restoreLocked(path: String) {
        Backup.withStorageLock {
            restore(path)
        }
    }

    /**
     * 调用方必须已经持有 Backup.withStorageLock。
     */
    internal suspend fun restorePreparedBackup(path: String) {
        restore(path)
    }

    /**
     * 选择性恢复方法
     * 只恢复用户选中的文件
     * 
     * @param context Android Context
     * @param path 已解压的备份目录路径
     * @param selectedFiles 选中的文件名列表
     */
    suspend fun restoreSelected(context: Context, path: String, selectedFiles: List<String>) {
        LogUtils.d(TAG, "开始选择性恢复备份 path:$path, files:${selectedFiles.joinToString()}")
        Backup.withStorageLock {
            try {
                restoreSelectedFiles(path, selectedFiles)
                LocalConfig.lastBackup = System.currentTimeMillis()
            } catch (e: Exception) {
                appCtx.toastOnUi("恢复备份出错\n${e.localizedMessage}")
                AppLog.put("选择性恢复备份出错\n${e.localizedMessage}", e)
            }
        }
    }

    /**
     * 核心选择性恢复逻辑
     * 
     * @param path 备份文件解压后的目录路径
     * @param selectedFiles 选中的文件名列表
     */
    private suspend fun restoreSelectedFiles(path: String, selectedFiles: List<String>) {
        val aes = BackupAES()
        val selectedSet = selectedFiles.toSet()

        // 恢复书架数据
        if ("bookshelf.json" in selectedSet) {
            appDb.bookDao.deleteAll()
            fileToListT<Book>(path, "bookshelf.json")?.let {
                it.forEach { book -> book.upType() }
                it.filter { book -> book.isLocal }
                    .forEach { book -> book.coverUrl = LocalBook.getCoverPath(book) }
                val ignoreLocalBook = BackupConfig.ignoreLocalBook
                val books = it.filterNot { book -> ignoreLocalBook && book.isLocal }
                appDb.bookDao.insert(*books.toTypedArray())
            }
        }

        // 恢复书签
        if ("bookmark.json" in selectedSet) {
            appDb.bookmarkDao.deleteAll()
            fileToListT<Bookmark>(path, "bookmark.json")?.let {
                appDb.bookmarkDao.insert(*it.toTypedArray())
            }
        }

        // 恢复书籍分组
        if ("bookGroup.json" in selectedSet) {
            appDb.bookGroupDao.deleteAll()
            fileToListT<BookGroup>(path, "bookGroup.json")?.let {
                appDb.bookGroupDao.insert(*it.toTypedArray())
            }
        }

        // 恢复书源
        if (bookChapterFileName in selectedSet) {
            restoreBookChapters(path)
        }

        if ("bookSource.json" in selectedSet) {
            appDb.bookSourceDao.deleteAll()
            fileToListT<BookSource>(path, "bookSource.json")?.let {
                appDb.bookSourceDao.insert(*it.toTypedArray())
            } ?: run {
                val bookSourceFile = File(path, "bookSource.json")
                if (bookSourceFile.exists()) {
                    val json = bookSourceFile.readText()
                    ImportOldData.importOldSource(json)
                }
            }
        }

        // 恢复RSS源
        if ("rssSources.json" in selectedSet) {
            appDb.rssSourceDao.deleteAll()
            fileToListT<RssSource>(path, "rssSources.json")?.let {
                appDb.rssSourceDao.insert(*it.toTypedArray())
            }
        }

        // 恢复RSS收藏
        if ("rssStar.json" in selectedSet) {
            appDb.rssStarDao.deleteAll()
            fileToListT<RssStar>(path, "rssStar.json")?.let {
                appDb.rssStarDao.insert(*it.toTypedArray())
            }
        }

        // 恢复替换规则
        if ("replaceRule.json" in selectedSet) {
            appDb.replaceRuleDao.deleteAll()
            fileToListT<ReplaceRule>(path, "replaceRule.json")?.let {
                appDb.replaceRuleDao.insert(*it.toTypedArray())
            }
        }

        // 恢复搜索历史
        if (HighlightRuleStore.backupFileName in selectedSet) {
            File(path, HighlightRuleStore.backupFileName).takeIf { it.exists() }?.runCatching {
                GSON.fromJsonObject<HighlightRuleStore.BackupData>(readText()).getOrNull()?.let {
                    HighlightRuleStore.restoreBackupData(appCtx, it, path)
                }
            }?.onFailure {
                AppLog.put("鎭㈠楂樹寒瑙勫垯鍑洪敊\n${it.localizedMessage}", it)
            }
        }
        if ("searchHistory.json" in selectedSet) {
            appDb.searchKeywordDao.deleteAll()
            fileToListT<SearchKeyword>(path, "searchHistory.json")?.let {
                appDb.searchKeywordDao.insert(*it.toTypedArray())
            }
        }

        // 恢复TXT目录规则
        if ("txtTocRule.json" in selectedSet) {
            appDb.txtTocRuleDao.deleteAll()
            fileToListT<TxtTocRule>(path, "txtTocRule.json")?.let {
                appDb.txtTocRuleDao.insert(*it.toTypedArray())
            }
        }

        // 恢复HTTP TTS配置
        if ("httpTTS.json" in selectedSet) {
            appDb.httpTTSDao.deleteAll()
            fileToListT<HttpTTS>(path, "httpTTS.json")?.let {
                appDb.httpTTSDao.insert(*it.toTypedArray())
            }
        }

        // 恢复词典规则
        if ("dictRule.json" in selectedSet) {
            appDb.dictRuleDao.deleteAll()
            fileToListT<DictRule>(path, "dictRule.json")?.let {
                appDb.dictRuleDao.insert(*it.toTypedArray())
            }
        }

        // 恢复键盘辅助
        if ("keyboardAssists.json" in selectedSet) {
            appDb.keyboardAssistsDao.deleteAll()
            fileToListT<KeyboardAssist>(path, "keyboardAssists.json")?.let {
                appDb.keyboardAssistsDao.insert(*it.toTypedArray())
            }
        }

        if (CoverGalleryRepository.backupDirName in selectedSet) {
            restoreCoverGallery(path)
        }

        // 恢复阅读记录
        if ("readRecord.json" in selectedSet || "readRecordDetail.json" in selectedSet || "readRecordSession.json" in selectedSet) {
            appDb.readRecordDao.clear()
            appDb.readRecordDao.clearDetails()
            appDb.readRecordDao.clearSessions()
            val readRecords = if ("readRecord.json" in selectedSet) fileToListT<ReadRecord>(path, "readRecord.json").orEmpty() else emptyList()
            val readRecordDetails = if ("readRecordDetail.json" in selectedSet) fileToListT<ReadRecordDetail>(path, "readRecordDetail.json").orEmpty() else emptyList()
            val readRecordSessions = if ("readRecordSession.json" in selectedSet) fileToListT<ReadRecordSession>(path, "readRecordSession.json").orEmpty() else emptyList()
            if (readRecords.isNotEmpty() || readRecordDetails.isNotEmpty() || readRecordSessions.isNotEmpty()) {
                ReadRecordRepository(appDb.readRecordDao).apply {
                    importRecords(readRecords, readRecordDetails, readRecordSessions)
                    repairRecords { bookName -> appDb.bookDao.getBookByName(bookName)?.author?.trim()?.ifBlank { null } }
                }
                appCtx.putPrefInt(PreferKey.readRecordRepairVersion, ReadRecordRepository.CURRENT_REPAIR_VERSION)
            }
        }

        // 恢复服务器配置
        if ("servers.json" in selectedSet) {
            File(path, "servers.json").takeIf { it.exists() }?.runCatching {
                var json = readText()
                if (!json.isJsonArray()) { json = aes.decryptStr(json) }
                GSON.fromJsonArray<Server>(json).getOrThrow().let {
                    appDb.serverDao.deleteAll()
                    appDb.serverDao.insert(*it.toTypedArray())
                }
            }?.onFailure { AppLog.put("恢复服务器配置出错\n${it.localizedMessage}", it) }
        }

        // 恢复直链上传配置
        if (DirectLinkUpload.ruleFileName in selectedSet || legacyDirectLinkRuleFileName in selectedSet) {
            getDirectLinkRuleBackupFile(path)?.runCatching {
                DirectLinkUploadRepository().restoreBackupJson(readText())
            }?.onFailure { AppLog.put("恢复直链上传出错\n${it.localizedMessage}", it) }
        }

        // 恢复主题配置
        if (ThemeConfig.configFileName in selectedSet) {
            File(path, ThemeConfig.configFileName).takeIf { it.exists() }?.runCatching {
                val configs = GSON.fromJsonArray<ThemeConfig.Config>(readText()).getOrNull()
                FileUtils.delete(ThemeConfig.configFilePath)
                copyTo(File(ThemeConfig.configFilePath))
                ThemeConfig.replaceConfigs(configs)
            }?.onFailure { AppLog.put("恢复主题出错\n${it.localizedMessage}", it) }
        }

        // 恢复封面规则配置
        if (BookCover.configFileName in selectedSet) {
            File(path, BookCover.configFileName).takeIf { it.exists() }?.runCatching {
                val json = readText()
                BookCover.saveCoverRule(json)
            }?.onFailure { AppLog.put("恢复封面规则出错\n${it.localizedMessage}", it) }
        }

        // 恢复阅读界面配置
        if (!BackupConfig.ignoreReadConfig && (ReadBookConfig.configFileName in selectedSet || ReadBookConfig.shareConfigFileName in selectedSet)) {
            restoreReadConfigBackgrounds(path)
            if (ReadBookConfig.configFileName in selectedSet) {
                File(path, ReadBookConfig.configFileName).takeIf { it.exists() }?.runCatching {
                    FileUtils.delete(ReadBookConfig.configFilePath)
                    copyTo(File(ReadBookConfig.configFilePath))
                    ReadBookConfig.initConfigs()
                }?.onFailure { AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it) }
            }
            if (ReadBookConfig.shareConfigFileName in selectedSet) {
                File(path, ReadBookConfig.shareConfigFileName).takeIf { it.exists() }?.runCatching {
                    FileUtils.delete(ReadBookConfig.shareConfigFilePath)
                    copyTo(File(ReadBookConfig.shareConfigFilePath))
                    ReadBookConfig.initShareConfig()
                }?.onFailure { AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it) }
            }
        }

        // 恢复主题背景图片
        fixReadConfigBackgroundPaths()

        // 恢复SharedPreferences配置
        if ("config.xml" in selectedSet) {
            readBackupPrefs(path, "config")?.let { map ->
                clearThemeRestorePrefs()
                val edit = appCtx.defaultSharedPreferences.edit()
                map.forEach { (key, value) ->
                    if (BackupConfig.keyIsNotIgnore(key) || key in themeRestorePrefKeys) {
                        when (key) {
                            PreferKey.webDavPassword -> {
                                kotlin.runCatching { aes.decryptStr(value.toString()) }.getOrNull()?.let {
                                    edit.putString(key, it)
                                } ?: let {
                                    if (appCtx.getPrefString(PreferKey.webDavPassword).isNullOrBlank()) {
                                        edit.putString(key, value.toString())
                                    }
                                }
                            }
                            else -> when (value) {
                                is Int -> edit.putInt(key, value)
                                is Boolean -> edit.putBoolean(key, value)
                                is Long -> edit.putLong(key, value)
                                is Float -> edit.putFloat(key, value)
                                is String -> edit.putString(key, value)
                            }
                        }
                    }
                }
                edit.apply()
            }
        }

        // 修正主题背景图片路径
        restoreThemeBackgrounds(
            backupPath = path,
            clearExisting = "config.xml" in selectedSet || ThemeConfig.configFileName in selectedSet
        )
        fixThemeBackgroundPaths()
        fixThemeConfigBackgroundPaths()

        // 恢复视频播放配置
        if ("videoConfig.xml" in selectedSet) {
            readBackupPrefs(path, "videoConfig")?.let { map ->
                appCtx.getSharedPreferences(VIDEO_PREF_NAME, Context.MODE_PRIVATE).edit().apply {
                    clear()
                    map.forEach { (key, value) ->
                        when (value) {
                            is Int -> putInt(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is String -> putString(key, value)
                        }
                    }
                    apply()
                }
            }
        }

        // 应用阅读配置
        if (runtimeSourceCacheFileName in selectedSet) {
            restoreRuntimeSourceCaches(path)
        }

        // 恢复书籍缓存和章节目录
        if (bookCacheIndexFileName in selectedSet || "bookChapterCache.json" in selectedSet) {
            restoreBookCache(path)
        }

        ReadBookConfig.apply {
            comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect)
            readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
            shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout)
            hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
            hideNavigationBar = appCtx.getPrefBoolean(PreferKey.hideNavigationBar)
            autoReadSpeed = appCtx.getPrefInt(PreferKey.autoReadSpeed, 46)
        }

        appCtx.toastOnUi(R.string.restore_success)

        // 应用主题和图标变更
        withContext(Main) {
            delay(100)
            if (!BuildConfig.DEBUG) {
                LauncherIconHelp.changeIcon(appCtx.getPrefString(PreferKey.launcherIcon))
            }
            ThemeConfig.applyDayNight(appCtx)
        }
    }

    /**
     * 核心恢复逻辑
     * 
     * 执行步骤：
     * 1. 恢复数据库数据（书籍、书签、书源等）
     * 2. 恢复自定义配置文件（主题、阅读样式等）
     * 3. 恢复SharedPreferences配置
     * 4. 应用配置变更
     * 
     * @param path 备份文件解压后的目录路径
     */
    private suspend fun restore(path: String) {
        val aes = BackupAES()

        // 恢复书架数据
        appDb.bookDao.deleteAll()
        fileToListT<Book>(path, "bookshelf.json")?.let {
            it.forEach { book ->
                book.upType()
            }
            it.filter { book -> book.isLocal }
                .forEach { book ->
                    book.coverUrl = LocalBook.getCoverPath(book)
                }
            val ignoreLocalBook = BackupConfig.ignoreLocalBook
            val books = it.filterNot { book -> ignoreLocalBook && book.isLocal }
            appDb.bookDao.insert(*books.toTypedArray())
        }

        // 恢复书签
        restoreBookChapters(path)

        appDb.bookmarkDao.deleteAll()
        fileToListT<Bookmark>(path, "bookmark.json")?.let {
            appDb.bookmarkDao.insert(*it.toTypedArray())
        }

        // 恢复书籍分组
        appDb.bookGroupDao.deleteAll()
        fileToListT<BookGroup>(path, "bookGroup.json")?.let {
            appDb.bookGroupDao.insert(*it.toTypedArray())
        }

        // 恢复书源（兼容旧版本格式）
        appDb.bookSourceDao.deleteAll()
        fileToListT<BookSource>(path, "bookSource.json")?.let {
            appDb.bookSourceDao.insert(*it.toTypedArray())
        } ?: run {
            val bookSourceFile = File(path, "bookSource.json")
            if (bookSourceFile.exists()) {
                val json = bookSourceFile.readText()
                ImportOldData.importOldSource(json)
            }
        }

        // 恢复RSS源
        appDb.rssSourceDao.deleteAll()
        fileToListT<RssSource>(path, "rssSources.json")?.let {
            appDb.rssSourceDao.insert(*it.toTypedArray())
        }

        // 恢复RSS收藏
        appDb.rssStarDao.deleteAll()
        fileToListT<RssStar>(path, "rssStar.json")?.let {
            appDb.rssStarDao.insert(*it.toTypedArray())
        }

        // 恢复替换规则
        appDb.replaceRuleDao.deleteAll()
        fileToListT<ReplaceRule>(path, "replaceRule.json")?.let {
            appDb.replaceRuleDao.insert(*it.toTypedArray())
        }

        // 恢复搜索历史
        File(path, HighlightRuleStore.backupFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonObject<HighlightRuleStore.BackupData>(readText()).getOrNull()?.let {
                HighlightRuleStore.restoreBackupData(appCtx, it, path)
            }
        }?.onFailure {
            AppLog.put("鎭㈠楂樹寒瑙勫垯鍑洪敊\n${it.localizedMessage}", it)
        }
        appDb.searchKeywordDao.deleteAll()
        fileToListT<SearchKeyword>(path, "searchHistory.json")?.let {
            appDb.searchKeywordDao.insert(*it.toTypedArray())
        }

        // 恢复TXT目录规则
        appDb.txtTocRuleDao.deleteAll()
        fileToListT<TxtTocRule>(path, "txtTocRule.json")?.let {
            appDb.txtTocRuleDao.insert(*it.toTypedArray())
        }

        // 恢复HTTP TTS配置
        appDb.httpTTSDao.deleteAll()
        fileToListT<HttpTTS>(path, "httpTTS.json")?.let {
            appDb.httpTTSDao.insert(*it.toTypedArray())
        }

        // 恢复词典规则
        appDb.dictRuleDao.deleteAll()
        fileToListT<DictRule>(path, "dictRule.json")?.let {
            appDb.dictRuleDao.insert(*it.toTypedArray())
        }

        // 恢复键盘辅助（先删除再插入，保证与备份数据一致）
        appDb.keyboardAssistsDao.deleteAll()
        fileToListT<KeyboardAssist>(path, "keyboardAssists.json")?.let {
            appDb.keyboardAssistsDao.insert(*it.toTypedArray())
        }

        restoreCoverGallery(path)

        // 恢复阅读记录（先清空再导入）
        appDb.readRecordDao.clear()
        appDb.readRecordDao.clearDetails()
        appDb.readRecordDao.clearSessions()
        val readRecords = fileToListT<ReadRecord>(path, "readRecord.json").orEmpty()
        val readRecordDetails = fileToListT<ReadRecordDetail>(path, "readRecordDetail.json").orEmpty()
        val readRecordSessions = fileToListT<ReadRecordSession>(path, "readRecordSession.json").orEmpty()
        if (readRecords.isNotEmpty() || readRecordDetails.isNotEmpty() || readRecordSessions.isNotEmpty()) {
            ReadRecordRepository(appDb.readRecordDao).apply {
                importRecords(
                    readRecords,
                    readRecordDetails,
                    readRecordSessions
                )
                repairRecords { bookName ->
                    appDb.bookDao.getBookByName(bookName)?.author?.trim()?.ifBlank { null }
                }
            }
            appCtx.putPrefInt(
                PreferKey.readRecordRepairVersion,
                ReadRecordRepository.CURRENT_REPAIR_VERSION
            )
        }

        // 恢复服务器配置（需要解密）
        File(path, "servers.json").takeIf {
            it.exists()
        }?.runCatching {
            var json = readText()
            if (!json.isJsonArray()) {
                json = aes.decryptStr(json)
            }
            GSON.fromJsonArray<Server>(json).getOrThrow().let {
                appDb.serverDao.deleteAll()
                appDb.serverDao.insert(*it.toTypedArray())
            }
        }?.onFailure {
            AppLog.put("恢复服务器配置出错\n${it.localizedMessage}", it)
        }

        // 恢复直链上传配置
        DirectLinkUpload.delConfig()
        getDirectLinkRuleBackupFile(path)?.runCatching {
            DirectLinkUploadRepository().restoreBackupJson(readText())
        }?.onFailure {
            AppLog.put("恢复直链上传出错\n${it.localizedMessage}", it)
        }

        // 恢复主题配置
        ThemeConfig.replaceConfigs(emptyList())
        File(path, ThemeConfig.configFileName).takeIf {
            it.exists()
        }?.runCatching {
            val configs = GSON.fromJsonArray<ThemeConfig.Config>(readText()).getOrNull()
            FileUtils.delete(ThemeConfig.configFilePath)
            copyTo(File(ThemeConfig.configFilePath))
            ThemeConfig.replaceConfigs(configs)
        }?.onFailure {
            AppLog.put("恢复主题出错\n${it.localizedMessage}", it)
        }

        // 恢复封面规则配置
        BookCover.delCoverRule()
        File(path, BookCover.configFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            BookCover.saveCoverRule(json)
        }?.onFailure {
            AppLog.put("恢复封面规则出错\n${it.localizedMessage}", it)
        }

        // 恢复阅读界面配置（可配置忽略）
        if (!BackupConfig.ignoreReadConfig) {
            restoreReadConfigBackgrounds(path)
            //恢复阅读界面配置
            File(path, ReadBookConfig.configFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.configFilePath)
                copyTo(File(ReadBookConfig.configFilePath))
                ReadBookConfig.initConfigs()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
            File(path, ReadBookConfig.shareConfigFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.shareConfigFilePath)
                copyTo(File(ReadBookConfig.shareConfigFilePath))
                ReadBookConfig.initShareConfig()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
        }

        // 恢复主题背景图片
        fixReadConfigBackgroundPaths()

        // 恢复SharedPreferences配置（应用主配置）
        readBackupPrefs(path, "config")?.let { map ->
            clearThemeRestorePrefs()
            val edit = appCtx.defaultSharedPreferences.edit()

            map.forEach { (key, value) ->
                if (BackupConfig.keyIsNotIgnore(key) || key in themeRestorePrefKeys) {
                    when (key) {
                        // WebDav密码需要解密
                        PreferKey.webDavPassword -> {
                            kotlin.runCatching {
                                aes.decryptStr(value.toString())
                            }.getOrNull()?.let {
                                edit.putString(key, it)
                            } ?: let {
                                // 解密失败时，如果本地密码为空则使用备份中的值
                                if (appCtx.getPrefString(PreferKey.webDavPassword)
                                        .isNullOrBlank()
                                ) {
                                    edit.putString(key, value.toString())
                                }
                            }
                        }

                        else -> when (value) {
                            is Int -> edit.putInt(key, value)
                            is Boolean -> edit.putBoolean(key, value)
                            is Long -> edit.putLong(key, value)
                            is Float -> edit.putFloat(key, value)
                            is String -> edit.putString(key, value)
                        }
                    }
                }
            }
            edit.apply()
        }

        // 修正主题背景图片路径
        restoreThemeBackgrounds(path, clearExisting = true)
        restoreRuntimeSourceCaches(path)
        restoreBookCache(path)
        fixThemeBackgroundPaths()
        fixThemeConfigBackgroundPaths()

        // 恢复视频播放配置
        readBackupPrefs(path, "videoConfig")?.let { map ->
            appCtx.getSharedPreferences(VIDEO_PREF_NAME, Context.MODE_PRIVATE).edit().apply {
                clear()
                map.forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                    }
                }
                apply()
            }
        }

        // 应用阅读配置
        ReadBookConfig.apply {
            comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect)
            readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
            shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout)
            hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
            hideNavigationBar = appCtx.getPrefBoolean(PreferKey.hideNavigationBar)
            autoReadSpeed = appCtx.getPrefInt(PreferKey.autoReadSpeed, 46)
        }

        appCtx.toastOnUi(R.string.restore_success)

        // 应用主题和图标变更
        withContext(Main) {
            delay(100)
            if (!BuildConfig.DEBUG) {
                LauncherIconHelp.changeIcon(appCtx.getPrefString(PreferKey.launcherIcon))
            }
            ThemeConfig.applyDayNight(appCtx)
        }
    }

    /**
     * 从JSON文件读取列表数据
     * 
     * @param T 数据类型
     * @param path 备份目录路径
     * @param fileName JSON文件名
     * @return 解析后的列表，文件不存在或解析失败返回null
     */
    private inline fun <reified T> fileToListT(path: String, fileName: String): List<T>? {
        try {
            val file = File(path, fileName)
            if (file.exists()) {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件大小 ${file.length()}")
                FileInputStream(file).use {
                    return GSON.fromJsonArray<T>(it).getOrThrow().also { list ->
                        LogUtils.d(TAG, "阅读恢复备份 $fileName 列表大小 ${list.size}")
                    }
                }
            } else {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件不存在")
            }
        } catch (e: Exception) {
            AppLog.put("$fileName\n读取解析出错\n${e.localizedMessage}", e)
            appCtx.toastOnUi("$fileName\n读取文件出错\n${e.localizedMessage}")
        }
        return null
    }

    private fun restoreRuntimeSourceCaches(path: String) {
        val runtimeCacheFile = File(path, runtimeSourceCacheFileName)
        if (!runtimeCacheFile.exists()) return
        val caches = fileToListT<Cache>(path, runtimeSourceCacheFileName).orEmpty()
        appDb.cacheDao.deleteAllRuntimeSourceCaches()
        AppCacheManager.clearSourceVariables()
        if (caches.isNotEmpty()) {
            appDb.cacheDao.insert(*caches.toTypedArray())
        }
    }

    private fun restoreBookChapters(path: String) {
        val chapterFile = File(path, bookChapterFileName)
        if (!chapterFile.exists()) return
        val chapters = fileToListT<BookChapter>(path, bookChapterFileName).orEmpty()
        if (chapters.isEmpty()) return
        val bookUrls = appDb.bookDao.all.mapTo(hashSetOf()) { it.bookUrl }
        chapters
            .filter { it.bookUrl in bookUrls }
            .groupBy { it.bookUrl }
            .forEach { (bookUrl, chapterList) ->
                appDb.bookChapterDao.delByBook(bookUrl)
                appDb.bookChapterDao.insert(*chapterList.toTypedArray())
            }
    }

    private suspend fun restoreCoverGallery(path: String) {
        val galleryDir = File(path, CoverGalleryRepository.backupDirName)
        if (!galleryDir.exists() || !galleryDir.isDirectory) return
        val oldGroupIds = appDb.coverGalleryDao.allGroups.map { it.id }

        appDb.coverGalleryDao.deleteAllImages()
        appDb.coverGalleryDao.deleteAllGroups()

        appDb.cacheDao.deleteRuntimeSourceCachesByPrefix(CoverGalleryRepository.randomSeedKeyPrefix)
        oldGroupIds.forEach {
            CacheManager.deleteMemory(CoverGalleryRepository.randomSeedKeyPrefix + it)
        }

        val targetDir = appCtx.externalFiles.getFile("covers").createFolderIfNotExist()
        val usedImageNames = hashSetOf<String>()
        galleryDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.forEachIndexed { groupIndex, groupDir ->
                val groupId = appDb.coverGalleryDao.insertGroup(
                    CoverGalleryGroup(
                        name = groupDir.name,
                        order = groupIndex
                    )
                )
                val images = groupDir.listFiles()
                    ?.filter { it.isFile && it.isCoverGalleryImageFile() }
                    ?.sortedBy { it.name }
                    ?.mapIndexed { imageIndex, imageFile ->
                        val targetFile = File(
                            targetDir,
                            uniqueCoverGalleryImageName(imageFile.name, usedImageNames)
                        )
                        imageFile.copyTo(targetFile, overwrite = true)
                        CoverGalleryImage(
                            groupId = groupId,
                            path = targetFile.absolutePath,
                            order = imageIndex
                        )
                    }
                    .orEmpty()
                if (images.isNotEmpty()) {
                    appDb.coverGalleryDao.insertImages(*images.toTypedArray())
                }
            }

        BookCover.upDefaultCover()
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

    private fun File.isCoverGalleryImageFile(): Boolean {
        return extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    }

    private fun uniqueCoverGalleryImageName(
        fileName: String,
        usedImageNames: MutableSet<String>
    ): String {
        val nameWithoutExtension = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var candidate = fileName
        var suffix = 2
        while (!usedImageNames.add(candidate)) {
            candidate = if (extension.isBlank()) {
                "$nameWithoutExtension-$suffix"
            } else {
                "$nameWithoutExtension-$suffix.$extension"
            }
            suffix++
        }
        return candidate
    }

    private fun readBackupPrefs(path: String, fileName: String): Map<String, Any>? {
        val file = File(path, "$fileName.xml")
        if (!file.exists()) return null
        return runCatching {
            val map = linkedMapOf<String, Any>()
            file.inputStream().use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, "utf-8")
                var event = parser.eventType
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        val name = parser.getAttributeValue(null, "name")
                        if (!name.isNullOrBlank()) {
                            when (parser.name) {
                                "string" -> map[name] = parser.nextText()
                                "int" -> parser.getAttributeValue(null, "value")?.toIntOrNull()
                                    ?.let { map[name] = it }
                                "long" -> parser.getAttributeValue(null, "value")?.toLongOrNull()
                                    ?.let { map[name] = it }
                                "float" -> parser.getAttributeValue(null, "value")?.toFloatOrNull()
                                    ?.let { map[name] = it }
                                "boolean" -> parser.getAttributeValue(null, "value")?.toBooleanStrictOrNull()
                                    ?.let { map[name] = it }
                            }
                        }
                    }
                    event = parser.next()
                }
            }
            map
        }.onFailure {
            AppLog.put("$fileName.xml\n璇诲彇閰嶇疆鍑洪敊\n${it.localizedMessage}", it)
        }.getOrNull()
    }

    private fun restoreReadConfigBackgrounds(path: String) {
        val bgNames = linkedSetOf<String>()
        File(path, ReadBookConfig.configFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonArray<ReadBookConfig.Config>(readText()).getOrThrow()
        }?.getOrNull()?.forEach { config ->
            collectBgNames(config, bgNames)
        }
        File(path, ReadBookConfig.shareConfigFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonObject<ReadBookConfig.Config>(readText()).getOrThrow()
        }?.getOrNull()?.let { config ->
            collectBgNames(config, bgNames)
        }
        clearReadConfigBackgrounds()
        if (bgNames.isEmpty()) return
        val bgDir = appCtx.externalFiles.getFile("bg")
        if (!bgDir.exists()) {
            bgDir.mkdirs()
        }
        bgNames.forEach { bgName ->
            val backupFile = File(path, "bg${File.separator}$bgName")
                .takeIf { it.exists() && it.isFile }
                ?: File(path, bgName).takeIf { it.exists() && it.isFile }
            backupFile?.copyTo(
                File(bgDir, bgName),
                overwrite = true
            )
        }
    }

    private fun collectBgNames(
        config: ReadBookConfig.Config,
        bgNames: MutableSet<String>
    ) {
        if (config.bgType == 2) {
            bgNames.add(File(config.bgStr).name)
        }
        if (config.bgTypeNight == 2) {
            bgNames.add(File(config.bgStrNight).name)
        }
        if (config.bgTypeEInk == 2) {
            bgNames.add(File(config.bgStrEInk).name)
        }
    }

    private fun clearReadConfigBackgrounds() {
        val bgDir = appCtx.externalFiles.getFile("bg")
        FileUtils.delete(bgDir)
        bgDir.mkdirs()
    }

    private fun clearThemeBackgrounds() {
        listOf(PreferKey.bgImage, PreferKey.bgImageN).forEach { prefKey ->
            val bgDir = appCtx.externalFiles.getFile(prefKey)
            FileUtils.delete(bgDir)
            bgDir.mkdirs()
        }
    }

    private fun fixReadConfigBackgroundPaths() {
        var updated = false
        ReadBookConfig.configList.forEach { config ->
            if (fixReadConfigBackgroundPath(config)) {
                updated = true
            }
        }
        runCatching { ReadBookConfig.shareConfig }.getOrNull()?.let { shareConfig ->
            if (fixReadConfigBackgroundPath(shareConfig)) {
                updated = true
            }
        }
        if (updated) {
            ReadBookConfig.save()
        }
    }

    private fun fixReadConfigBackgroundPath(config: ReadBookConfig.Config): Boolean {
        var updated = false
        if (config.bgType == 2) {
            val fixedPath = fixReadBgPath(config.bgStr)
            if (fixedPath != config.bgStr) {
                config.bgStr = fixedPath
                updated = true
            }
        }
        if (config.bgTypeNight == 2) {
            val fixedPath = fixReadBgPath(config.bgStrNight)
            if (fixedPath != config.bgStrNight) {
                config.bgStrNight = fixedPath
                updated = true
            }
        }
        if (config.bgTypeEInk == 2) {
            val fixedPath = fixReadBgPath(config.bgStrEInk)
            if (fixedPath != config.bgStrEInk) {
                config.bgStrEInk = fixedPath
                updated = true
            }
        }
        return updated
    }

    private fun fixReadBgPath(bgPath: String): String {
        if (bgPath.isBlank()) return bgPath
        val bgName = File(bgPath).name
        val localFile = appCtx.externalFiles.getFile("bg", bgName)
        return if (localFile.exists()) {
            localFile.absolutePath
        } else {
            bgPath
        }
    }

    private fun restoreThemeBackgrounds(backupPath: String, clearExisting: Boolean) {
        if (clearExisting) {
            clearThemeBackgrounds()
        }
        // 从 config.xml 中读取主题背景图片路径
        val configPrefs = readBackupPrefs(backupPath, "config")
        
        // 恢复白天主题背景
        (configPrefs?.get(PreferKey.bgImage) as? String)?.let { bgPath ->
            restoreThemeBgFile(backupPath, bgPath, PreferKey.bgImage)
        }
        
        // 恢复夜间主题背景
        (configPrefs?.get(PreferKey.bgImageN) as? String)?.let { bgPath ->
            restoreThemeBgFile(backupPath, bgPath, PreferKey.bgImageN)
        }
        File(backupPath, ThemeConfig.configFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonArray<ThemeConfig.Config>(readText()).getOrThrow()
        }?.getOrNull()?.forEach { config ->
            val bgPath = config.backgroundImgPath ?: return@forEach
            val prefKey = if (config.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage
            restoreThemeBgFile(backupPath, bgPath, prefKey)
        }
    }
    
    private fun restoreThemeBgFile(backupPath: String, bgPath: String, prefKey: String) {
        if (bgPath.isBlank()) return
        
        val bgFile = if (bgPath.startsWith("http")) {
            // 在线图片，文件名从 URL 计算
            val name = ThemeConfig.getUrlToFile(bgPath)
            appCtx.externalFiles.getFile(prefKey, name)
        } else if (bgPath.contains(File.separator)) {
            // 本地路径，提取文件名
            val name = File(bgPath).name
            appCtx.externalFiles.getFile(prefKey, name)
        } else {
            // 已经是文件名
            appCtx.externalFiles.getFile(prefKey, bgPath)
        }
        
        // 从备份目录复制文件
        val bgName = if (bgPath.startsWith("http")) {
            ThemeConfig.getUrlToFile(bgPath)
        } else {
            File(bgPath).name
        }
        val backupFile = File(backupPath, "$prefKey${File.separator}$bgName")
            .takeIf { it.exists() && it.isFile }
            ?: File(backupPath, bgName).takeIf { it.exists() && it.isFile }
        if (backupFile != null) {
            val targetDir = appCtx.externalFiles.getFile(prefKey)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            backupFile.copyTo(File(targetDir, bgName), overwrite = true)
            LogUtils.d(TAG, "恢复主题背景: $bgName -> ${bgFile.absolutePath}")
        }
    }

    private fun clearThemeRestorePrefs() {
        appCtx.defaultSharedPreferences.edit {
            themeRestorePrefKeys.forEach(::remove)
        }
    }

    private fun fixThemeBackgroundPaths() {
        // 修正白天主题背景路径
        appCtx.getPrefString(PreferKey.bgImage)?.let { bgPath ->
            val fixedPath = fixThemeBgPath(bgPath, PreferKey.bgImage)
            if (fixedPath != bgPath) {
                appCtx.putPrefString(PreferKey.bgImage, fixedPath)
                LogUtils.d(TAG, "修正白天主题背景路径: $bgPath -> $fixedPath")
            }
        }
        
        // 修正夜间主题背景路径
        appCtx.getPrefString(PreferKey.bgImageN)?.let { bgPath ->
            val fixedPath = fixThemeBgPath(bgPath, PreferKey.bgImageN)
            if (fixedPath != bgPath) {
                appCtx.putPrefString(PreferKey.bgImageN, fixedPath)
                LogUtils.d(TAG, "修正夜间主题背景路径: $bgPath -> $fixedPath")
            }
        }
    }

    private fun fixThemeConfigBackgroundPaths() {
        var updated = false
        ThemeConfig.configList.forEachIndexed { index, config ->
            val bgPath = config.backgroundImgPath ?: return@forEachIndexed
            val prefKey = if (config.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage
            val fixedPath = fixThemeBgPath(bgPath, prefKey)
            if (fixedPath != bgPath) {
                ThemeConfig.configList[index] = config.copy(backgroundImgPath = fixedPath)
                updated = true
                LogUtils.d(TAG, "淇涓婚閰嶇疆鑳屾櫙璺緞: $bgPath -> $fixedPath")
            }
        }
        if (updated) {
            ThemeConfig.save()
        }
    }
    
    private fun fixThemeBgPath(bgPath: String, prefKey: String): String {
        if (bgPath.isBlank()) return bgPath
        // 在线图片路径不需要修正
        if (bgPath.startsWith("http")) return bgPath
        // 已经是文件名，不需要修正
        if (!bgPath.contains(File.separator)) return bgPath
        
        // 提取文件名，拼接新设备路径
        val bgName = File(bgPath).name
        val newFile = appCtx.externalFiles.getFile(prefKey, bgName)
        if (newFile.exists()) {
            return newFile.absolutePath
        }
        // 如果新路径不存在，返回文件名（ThemeConfig.getBgImage 会自动处理）
        return bgName
    }

    /**
     * 恢复书籍缓存
     * 
     * 流程：
     * 1. 恢复章节目录（如果有）
     * 2. 读取缓存索引文件
     * 3. 遍历索引，匹配当前设备上的书籍
     * 4. 获取当前书籍的章节列表
     * 5. 根据章节标题匹配，重命名章节文件
     * 6. 复制缓存文件到对应位置
     * 
     * 匹配策略：
     * 1. 优先按章节序号精确匹配
     * 2. 其次按章节标题匹配
     */
    private fun restoreBookCache(path: String) {
        if (BackupConfig.ignoreBookCache) {
            LogUtils.d(TAG, "忽略书籍缓存恢复")
            return
        }
        
        // 先恢复章节目录
        restoreBookChapterCache(path)
        
        val indexFile = File(path, bookCacheIndexFileName)
        if (!indexFile.exists()) {
            LogUtils.d(TAG, "书籍缓存索引文件不存在")
            return
        }
        
        val cacheIndexList = runCatching {
            GSON.fromJsonArray<BookCacheIndex>(indexFile.readText()).getOrNull()
        }.getOrNull() ?: run {
            LogUtils.d(TAG, "解析书籍缓存索引失败")
            return
        }
        
        if (cacheIndexList.isEmpty()) {
            LogUtils.d(TAG, "书籍缓存索引为空")
            return
        }
        
        val backupCacheDir = File(path, bookCacheFolderName)
        if (!backupCacheDir.exists()) {
            LogUtils.d(TAG, "备份缓存目录不存在")
            return
        }
        
        val targetCacheDir = File(BookHelp.cachePath)
        if (!targetCacheDir.exists()) {
            targetCacheDir.mkdirs()
        }
        
        val allBooks = appDb.bookDao.all
        var restoredCount = 0
        var chapterRestoredCount = 0
        
        cacheIndexList.forEach { cacheIndex ->
            val matchedBook = findMatchingBook(cacheIndex, allBooks)
            if (matchedBook == null) {
                LogUtils.d(TAG, "未找到匹配书籍: ${cacheIndex.bookName}")
                return@forEach
            }
            
            val sourceCacheDir = File(backupCacheDir, cacheIndex.folderName)
            if (!sourceCacheDir.exists()) {
                LogUtils.d(TAG, "备份缓存目录不存在: ${cacheIndex.folderName}")
                return@forEach
            }
            
            val targetFolderName = matchedBook.getFolderNameNoCache()
            val targetBookDir = File(targetCacheDir, targetFolderName)
            if (!targetBookDir.exists()) {
                targetBookDir.mkdirs()
            }
            
            // 获取当前书籍的章节列表
            val currentChapters = appDb.bookChapterDao.getChapterList(matchedBook.bookUrl)
            val currentChapterByIndex = currentChapters.associateBy { it.index }
            val currentChapterByTitle = currentChapters.associateBy { it.title }
            
            // 恢复章节文件，根据需要重命名
            cacheIndex.chapters.forEach { chapterInfo ->
                val sourceFile = File(sourceCacheDir, chapterInfo.fileName)
                if (!sourceFile.exists()) {
                    return@forEach
                }
                
                // 查找匹配的当前章节
                val targetChapter = currentChapterByIndex[chapterInfo.index]
                    ?: currentChapterByTitle[chapterInfo.title]
                
                if (targetChapter == null) {
                    LogUtils.d(TAG, "未找到匹配章节: ${chapterInfo.title}")
                    return@forEach
                }
                
                // 计算目标文件名
                val targetFileName = targetChapter.getFileName()
                val targetFile = File(targetBookDir, targetFileName)
                
                // 复制文件（如果文件名不同则重命名）
                sourceFile.copyTo(targetFile, overwrite = true)
                chapterRestoredCount++
            }
            
            // 复制图片文件夹（如果有）
            val sourceImageDir = File(sourceCacheDir, "images")
            if (sourceImageDir.exists()) {
                val targetImageDir = File(targetBookDir, "images")
                sourceImageDir.copyRecursively(targetImageDir, overwrite = true)
            }
            
            restoredCount++
            LogUtils.d(TAG, "恢复书籍缓存: ${matchedBook.name} -> $targetFolderName")
        }
        
        LogUtils.d(TAG, "书籍缓存恢复完成，共恢复 $restoredCount 本书，$chapterRestoredCount 个章节")
    }
    
    /**
     * 恢复章节目录
     * 从 bookChapterCache.json 恢复章节目录数据
     * 
     * @param path 备份文件解压后的目录路径
     */
    private fun restoreBookChapterCache(path: String) {
        val chapterFile = File(path, "bookChapterCache.json")
        if (!chapterFile.exists()) {
            LogUtils.d(TAG, "章节目录文件不存在")
            return
        }
        
        val chapters = fileToListT<BookChapter>(path, "bookChapterCache.json")
        if (chapters.isNullOrEmpty()) {
            LogUtils.d(TAG, "章节目录为空")
            return
        }
        
        // 按 bookUrl 分组
        val chaptersByBook = chapters.groupBy { it.bookUrl }
        var restoredBookCount = 0
        var restoredChapterCount = 0
        
        chaptersByBook.forEach { (bookUrl, chapterList) ->
            // 检查书籍是否存在
            val book = appDb.bookDao.getBook(bookUrl)
            if (book == null) {
                // 尝试通过缓存索引中的书名匹配
                val cacheIndexFile = File(path, bookCacheIndexFileName)
                if (cacheIndexFile.exists()) {
                    val cacheIndexList = runCatching {
                        GSON.fromJsonArray<BookCacheIndex>(cacheIndexFile.readText()).getOrNull()
                    }.getOrNull()
                    
                    val cacheIndex = cacheIndexList?.find { it.bookUrl == bookUrl }
                    if (cacheIndex != null) {
                        val matchedBook = appDb.bookDao.all.find { it.name == cacheIndex.bookName }
                        if (matchedBook != null) {
                            // 更新章节的 bookUrl
                            val updatedChapters = chapterList.map { chapter ->
                                chapter.copy(bookUrl = matchedBook.bookUrl)
                            }
                            // 删除旧的章节，插入新的
                            appDb.bookChapterDao.delByBook(matchedBook.bookUrl)
                            appDb.bookChapterDao.insert(*updatedChapters.toTypedArray())
                            restoredBookCount++
                            restoredChapterCount += updatedChapters.size
                            LogUtils.d(TAG, "恢复章节目录: ${matchedBook.name}, ${updatedChapters.size} 章")
                        }
                    }
                }
            } else {
                // 书籍存在，直接恢复章节
                appDb.bookChapterDao.delByBook(bookUrl)
                appDb.bookChapterDao.insert(*chapterList.toTypedArray())
                restoredBookCount++
                restoredChapterCount += chapterList.size
                LogUtils.d(TAG, "恢复章节目录: ${book.name}, ${chapterList.size} 章")
            }
        }
        
        LogUtils.d(TAG, "章节目录恢复完成，共 $restoredBookCount 本书，$restoredChapterCount 章")
    }
    
    /**
     * 查找匹配的书籍
     * 
     * @param cacheIndex 缓存索引信息
     * @param allBooks 所有书籍列表
     * @return 匹配的书籍，未找到返回null
     */
    private fun findMatchingBook(
        cacheIndex: BookCacheIndex,
        allBooks: List<Book>
    ): Book? {
        // 优先按 bookUrl 精确匹配
        allBooks.find { it.bookUrl == cacheIndex.bookUrl }?.let { return it }
        
        // 其次按 书名+作者 匹配
        val normalizedAuthor = cacheIndex.author.trim()
        allBooks.filter { 
            it.name == cacheIndex.bookName && 
            it.author.trim() == normalizedAuthor
        }.firstOrNull()?.let { return it }
        
        // 最后按书名模糊匹配（作者可能为空或不一致）
        allBooks.filter { it.name == cacheIndex.bookName }.firstOrNull()?.let { return it }
        
        return null
    }

    private fun getDirectLinkRuleBackupFile(path: String): File? {
        return File(path, DirectLinkUpload.ruleFileName).takeIf { it.exists() }
            ?: File(path, legacyDirectLinkRuleFileName).takeIf { it.exists() }
    }

}
