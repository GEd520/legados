package io.legado.app.help.storage

import android.content.ComponentCallbacks2
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.MemoryPressure
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.BookCover
import io.legado.app.model.upload.DirectLinkUploadRepository
import io.legado.app.help.storage.BackupSelectorConfig
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFolderIfNotExist
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefString
import io.legado.app.utils.getSharedPreferences
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.outputStream
import io.legado.app.utils.writeToOutputStream
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.getFolderNameNoCache
import io.legado.app.model.VideoPlay.VIDEO_PREF_NAME
import io.legado.app.ui.book.read.config.HighlightRuleStore
import io.legado.app.data.repository.CoverGalleryRepository

/**
 * 章节缓存信息
 * 用于记录单个章节的缓存文件信息
 */
data class ChapterCacheInfo(
    val index: Int,           // 章节序号
    val title: String,        // 章节标题
    val titleMD5: String,     // 标题MD5
    val fileName: String      // 原始文件名
)

/**
 * 书籍缓存索引数据类
 * 用于记录备份中每本书的缓存信息，恢复时用于匹配
 */
data class BookCacheIndex(
    val bookUrl: String,
    val bookName: String,
    val author: String,
    val folderName: String,
    val chapters: List<ChapterCacheInfo> = emptyList()  // 章节信息列表
)

/**
 * 备份管理类
 * 
 * 负责应用数据的备份功能，包括：
 * - 书架、书签、书源等数据库数据
 * - 阅读配置、主题配置等SharedPreferences数据
 * - 自定义配置文件（阅读样式、主题、封面规则等）
 * 
 * 备份流程：
 * 1. 将数据库数据导出为JSON文件
 * 2. 将SharedPreferences导出为XML文件
 * 3. 复制自定义配置文件
 * 4. 打包成ZIP文件
 * 5. 保存到本地目录或WebDav云端
 * 
 * 支持的备份内容：
 * - bookshelf.json: 书架书籍列表
 * - bookmark.json: 书签列表
 * - bookGroup.json: 书籍分组
 * - bookSource.json: 书源列表
 * - rssSources.json: 订阅源列表
 * - rssStar.json: 订阅收藏
 * - replaceRule.json: 替换规则
 * - readRecord.json: 阅读记录
 * - searchHistory.json: 搜索历史
 * - sourceSub.json: 订阅源
 * - txtTocRule.json: TXT目录规则
 * - httpTTS.json: TTS配置
 * - keyboardAssists.json: 键盘辅助
 * - dictRule.json: 词典规则
 * - servers.json: 服务器配置（加密存储）
 * - config.xml: 应用配置
 * - videoConfig.xml: 视频播放配置
 */
object Backup {
    private const val runtimeSourceCacheFileName = "runtimeSourceCache.json"
    private const val bookCacheFolderName = "book_cache"
    private const val bookCacheIndexFileName = "bookCacheIndex.json"
    private const val bookChapterFileName = "bookChapter.json"

    /** 备份临时目录路径，用于存放解压/压缩前的文件 */
    val backupPath: String by lazy {
        appCtx.filesDir.getFile("backup").createFolderIfNotExist().absolutePath
    }

    /** 临时ZIP文件路径，备份完成后会删除 */
    val zipFilePath = "${appCtx.externalFiles.absolutePath}${File.separator}tmp_backup.zip"

    /** 选择性恢复预览目录，避免弹出选择框期间占用备份临时目录 */
    val restorePath: String by lazy {
        appCtx.filesDir.getFile("restore").createFolderIfNotExist().absolutePath
    }

    private const val TAG = "Backup"
    private const val READ_BG_DIR = "bg"

    /** 互斥锁，防止备份和恢复共用临时目录时互相覆盖 */
    private val mutex = Mutex()

    internal suspend fun <T> withStorageLock(block: suspend () -> T): T {
        return mutex.withLock {
            block()
        }
    }

    /** 备份文件名列表，定义所有需要备份的文件 */
    private val backupFileNames by lazy {
        arrayOf(
            "bookshelf.json",
            "bookmark.json",
            "bookGroup.json",
            bookChapterFileName,
            "bookSource.json",
            "rssSources.json",
            "rssStar.json",
            "replaceRule.json",
            HighlightRuleStore.backupFileName,
            "readRecord.json",
            "readRecordDetail.json",
            "readRecordSession.json",
            "searchHistory.json",
            "txtTocRule.json",
            "httpTTS.json",
            "keyboardAssists.json",
            "dictRule.json",
            "servers.json",
            DirectLinkUpload.ruleFileName,
            ReadBookConfig.configFileName,
            ReadBookConfig.shareConfigFileName,
            ThemeConfig.configFileName,
            BookCover.configFileName,
            "config.xml",
            "videoConfig.xml",
            CoverGalleryRepository.backupDirName
        )
    }

    /** 获取所有背景图片文件 */
    fun getBackgroundImageFiles(): List<File> {
        val files = mutableListOf<File>()
        
        // 阅读界面背景图片
        val bgStrList = ReadBookConfig.getAllPicBgStr()
        LogUtils.d(TAG, "阅读背景 getAllPicBgStr 返回: $bgStrList")
        bgStrList.mapNotNull { bg ->
            LogUtils.d(TAG, "处理阅读背景: $bg")
            val file = if (bg.contains(File.separator)) {
                File(bg)
            } else {
                appCtx.externalFiles.getFile(READ_BG_DIR, bg)
            }
            LogUtils.d(TAG, "阅读背景文件路径: ${file.absolutePath}, 存在: ${file.exists()}, 是文件: ${file.isFile}")
            file.takeIf { it.exists() && it.isFile }
        }.let { files.addAll(it) }
        
        // 主题背景图片
        val themeBgPath = appCtx.getPrefString(PreferKey.bgImage)
        LogUtils.d(TAG, "主题白天背景路径: $themeBgPath")
        themeBgPath?.let { path ->
            val file = if (path.startsWith("http")) {
                val name = ThemeConfig.getUrlToFile(path)
                appCtx.externalFiles.getFile(PreferKey.bgImage, name)
            } else if (path.contains(File.separator)) {
                File(path)
            } else {
                appCtx.externalFiles.getFile(PreferKey.bgImage, path)
            }
            LogUtils.d(TAG, "主题白天背景文件: ${file.absolutePath}, 存在: ${file.exists()}, 是文件: ${file.isFile}")
            if (file.exists() && file.isFile) {
                files.add(file)
            }
        }
        
        val themeBgNightPath = appCtx.getPrefString(PreferKey.bgImageN)
        LogUtils.d(TAG, "主题夜间背景路径: $themeBgNightPath")
        themeBgNightPath?.let { path ->
            val file = if (path.startsWith("http")) {
                val name = ThemeConfig.getUrlToFile(path)
                appCtx.externalFiles.getFile(PreferKey.bgImageN, name)
            } else if (path.contains(File.separator)) {
                File(path)
            } else {
                appCtx.externalFiles.getFile(PreferKey.bgImageN, path)
            }
            LogUtils.d(TAG, "主题夜间背景文件: ${file.absolutePath}, 存在: ${file.exists()}, 是文件: ${file.isFile}")
            if (file.exists() && file.isFile) {
                files.add(file)
            }
        }
        
        return files.distinctBy { it.absolutePath }
    }

    private fun getReadBackgroundImageFiles(): List<File> {
        return ReadBookConfig.getAllPicBgStr().mapNotNull { bg ->
            val file = if (bg.contains(File.separator)) {
                File(bg)
            } else {
                appCtx.externalFiles.getFile(READ_BG_DIR, bg)
            }
            file.takeIf { it.exists() && it.isFile }
        }.distinctBy { it.absolutePath }
    }

    private fun resolveThemeBackgroundFile(path: String, prefKey: String): File? {
        val file = when {
            path.startsWith("http") -> {
                val name = ThemeConfig.getUrlToFile(path)
                appCtx.externalFiles.getFile(prefKey, name)
            }
            path.contains(File.separator) -> File(path)
            else -> appCtx.externalFiles.getFile(prefKey, path)
        }
        return file.takeIf { it.exists() && it.isFile }
    }

    private fun getThemeConfigBackgroundFiles(): List<Pair<String, File>> {
        return ThemeConfig.configList.mapNotNull { config ->
            val bgPath = config.backgroundImgPath ?: return@mapNotNull null
            val prefKey = if (config.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage
            resolveThemeBackgroundFile(bgPath, prefKey)?.let { prefKey to it }
        }.distinctBy { "${it.first}:${it.second.absolutePath}" }
    }

    private fun getRuntimeSourceCaches(): List<Cache> {
        return appDb.cacheDao.getRuntimeSourceCaches(System.currentTimeMillis())
    }

    private fun stageRuntimeSourceCaches(rootPath: String) {
        val runtimeCaches = getRuntimeSourceCaches()
        writeJsonToFile(runtimeCaches, runtimeSourceCacheFileName, rootPath)
    }

    fun stageBackgroundImageFiles(rootPath: String) {
        getReadBackgroundImageFiles().forEach { bgFile ->
            bgFile.copyTo(File(rootPath, bgFile.name), overwrite = true)
        }
        listOf(PreferKey.bgImage, PreferKey.bgImageN).forEach { prefKey ->
            appCtx.getPrefString(prefKey)?.let { path ->
                resolveThemeBackgroundFile(path, prefKey)
            }?.let { bgFile ->
                val targetDir = File(rootPath, prefKey).createFolderIfNotExist()
                bgFile.copyTo(File(targetDir, bgFile.name), overwrite = true)
            }
        }
        getThemeConfigBackgroundFiles().forEach { (prefKey, bgFile) ->
            val targetDir = File(rootPath, prefKey).createFolderIfNotExist()
            bgFile.copyTo(File(targetDir, bgFile.name), overwrite = true)
        }
    }

    fun stageHighlightRuleBackgroundFiles(rootPath: String) {
        val targetDir = File(rootPath, HighlightRuleStore.backupBgDirName).createFolderIfNotExist()
        HighlightRuleStore.getUsedBgImageFiles(appCtx).forEach { bgFile ->
            bgFile.copyTo(File(targetDir, bgFile.name), overwrite = true)
        }
    }

    private fun stageCoverGallery(rootPath: String) {
        val groups = appDb.coverGalleryDao.allGroups
        if (groups.isEmpty()) return
        val imagesByGroup = appDb.coverGalleryDao.allImages.groupBy { it.groupId }
        val rootDir = File(rootPath, CoverGalleryRepository.backupDirName).createFolderIfNotExist()
        val usedFolderNames = hashSetOf<String>()
        groups.forEachIndexed { index, group ->
            val folderName = uniqueCoverGalleryFolderName(group.name, index, usedFolderNames)
            val groupDir = File(rootDir, folderName).createFolderIfNotExist()
            imagesByGroup[group.id].orEmpty()
                .sortedWith(compareBy({ it.order }, { it.id }))
                .map { File(it.path) }
                .filter { it.exists() && it.isFile }
                .distinctBy { it.absolutePath }
                .forEach { imageFile ->
                    imageFile.copyTo(File(groupDir, imageFile.name), overwrite = true)
                }
        }
    }

    private fun uniqueCoverGalleryFolderName(
        groupName: String,
        index: Int,
        usedFolderNames: MutableSet<String>
    ): String {
        val fallbackName = "group${index + 1}"
        val baseName = groupName.ifBlank { fallbackName }.normalizeFileName().ifBlank {
            fallbackName
        }
        var folderName = baseName
        var suffix = 2
        while (!usedFolderNames.add(folderName)) {
            folderName = "$baseName ($suffix)"
            suffix++
        }
        return folderName
    }

    private fun getBackupPaths(): ArrayList<String> {
        return File(backupPath)
            .listFiles()
            ?.mapTo(arrayListOf()) { it.absolutePath }
            ?: arrayListOf()
        val paths = arrayListOf(*backupFileNames)
        for (i in 0 until paths.size) {
            paths[i] = backupPath + File.separator + paths[i]
        }
        val bgFiles = getBackgroundImageFiles()
        LogUtils.d(TAG, "背景图片文件数量: ${bgFiles.size}")
        bgFiles.forEach {
            LogUtils.d(TAG, "添加背景图片: ${it.absolutePath}")
            paths.add(it.absolutePath)
        }
        return paths
    }

    /**
     * 生成备份ZIP文件名
     * 格式：backup{日期}-{设备名}.zip 或 backup{日期}.zip
     * 
     * @return 格式化的备份文件名
     */
    private fun getNowZipFileName(): String {
        val backupDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        val deviceName = AppConfig.webDavDeviceName
        return if (deviceName?.isNotBlank() == true) {
            "backup${backupDate}-${deviceName}.zip"
        } else {
            "backup${backupDate}.zip"
        }.normalizeFileName()
    }

    /**
     * 判断是否需要执行自动备份
     * 距离上次备份超过24小时才执行
     * 
     * @return true表示需要备份，false表示不需要
     */
    private fun shouldBackup(): Boolean {
        val lastBackup = LocalConfig.lastBackup
        return lastBackup + TimeUnit.DAYS.toMillis(1) < System.currentTimeMillis()
    }

    /**
     * 自动备份入口
     * 在满足条件时自动执行备份，包括：
     * - 距离上次备份超过24小时
     * - WebDav上不存在当日备份文件
     * 
     * @param context Android Context
     */
    fun autoBack(context: Context) {
        if (shouldBackup()) {
            Coroutine.async {
                withStorageLock {
                    if (shouldBackup()) {
                        val backupZipFileName = getNowZipFileName()
                        if (!AppWebDav.hasBackUp(backupZipFileName)) {
                            backup(context, AppConfig.backupPath)
                        } else {
                            LocalConfig.lastBackup = System.currentTimeMillis()
                        }
                    }
                }
            }.onError {
                AppLog.put("自动备份失败\n${it.localizedMessage}")
            }
        }
    }

    /**
     * 带锁的备份方法
     * 使用互斥锁确保同一时间只有一个备份操作在执行
     * 
     * @param context Android Context
     * @param path 备份目标路径，可为null（使用默认路径）
     */
    suspend fun backupLocked(context: Context, path: String?) {
        withStorageLock {
            withContext(IO) {
                backup(context, path)
            }
        }
    }

    /**
     * 核心备份逻辑
     * 
     * 执行步骤：
     * 1. 清理旧的临时文件
     * 2. 导出数据库数据到JSON文件
     * 3. 导出SharedPreferences配置
     * 4. 打包成ZIP文件
     * 5. 复制到目标目录
     * 6. 上传到WebDav（如果配置）
     * 7. 清理临时文件
     * 
     * @param context Android Context
     * @param path 备份目标路径
     */
    @Suppress("DEPRECATION")
    private suspend fun backup(context: Context, path: String?) {
        LogUtils.d(TAG, "开始备份 path:$path")
        val aes = BackupAES()
        MemoryPressure.trimNow(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            waitForCompletion = true
        )
        FileUtils.delete(backupPath)

        val selectedFiles = BackupSelectorConfig.getSelectedFileNames()

        // 导出数据库数据到JSON文件
        if (selectedFiles.contains("bookshelf.json")) {
            writeListToJson(appDb.bookDao.all, "bookshelf.json", backupPath)
        }
        if (selectedFiles.contains("bookmark.json")) {
            writeListToJson(appDb.bookmarkDao.all, "bookmark.json", backupPath)
        }
        if (selectedFiles.contains("bookGroup.json")) {
            writeListToJson(appDb.bookGroupDao.all, "bookGroup.json", backupPath)
        }
        if (selectedFiles.contains(bookChapterFileName)) {
            writeListToJson(appDb.bookChapterDao.getAll(), bookChapterFileName, backupPath)
        }
        if (selectedFiles.contains("bookSource.json")) {
            writeListToJson(appDb.bookSourceDao.all, "bookSource.json", backupPath)
        }
        if (selectedFiles.contains("rssSources.json")) {
            writeListToJson(appDb.rssSourceDao.all, "rssSources.json", backupPath)
        }
        if (selectedFiles.contains("rssStar.json")) {
            writeListToJson(appDb.rssStarDao.all, "rssStar.json", backupPath)
        }
        if (selectedFiles.contains("replaceRule.json")) {
            writeListToJson(appDb.replaceRuleDao.all, "replaceRule.json", backupPath)
        }
        if (selectedFiles.contains(HighlightRuleStore.backupFileName)) {
            writeJsonToFile(
                HighlightRuleStore.createBackupData(appCtx),
                HighlightRuleStore.backupFileName,
                backupPath
            )
        }
        if (selectedFiles.contains("readRecord.json")) {
            writeListToJson(appDb.readRecordDao.all, "readRecord.json", backupPath)
        }
        if (selectedFiles.contains("readRecordDetail.json")) {
            writeListToJson(appDb.readRecordDao.getAllDetailsList(), "readRecordDetail.json", backupPath)
        }
        if (selectedFiles.contains("readRecordSession.json")) {
            writeListToJson(appDb.readRecordDao.getAllSessionsList(), "readRecordSession.json", backupPath)
        }
        if (selectedFiles.contains("searchHistory.json")) {
            writeListToJson(appDb.searchKeywordDao.all, "searchHistory.json", backupPath)
        }
        if (selectedFiles.contains("txtTocRule.json")) {
            writeListToJson(appDb.txtTocRuleDao.all, "txtTocRule.json", backupPath)
        }
        if (selectedFiles.contains("httpTTS.json")) {
            writeListToJson(appDb.httpTTSDao.all, "httpTTS.json", backupPath)
        }
        if (selectedFiles.contains("keyboardAssists.json")) {
            writeListToJson(appDb.keyboardAssistsDao.all, "keyboardAssists.json", backupPath)
        }
        if (selectedFiles.contains("dictRule.json")) {
            writeListToJson(appDb.dictRuleDao.all, "dictRule.json", backupPath)
        }
        if (selectedFiles.contains(CoverGalleryRepository.backupDirName)) {
            stageCoverGallery(backupPath)
        }

        // 服务器配置需要加密存储
        if (selectedFiles.contains("servers.json")) {
            GSON.toJson(appDb.serverDao.all).let { json ->
                aes.runCatching {
                    encryptBase64(json)
                }.getOrDefault(json).let {
                    FileUtils.createFileIfNotExist(backupPath + File.separator + "servers.json")
                        .writeText(it)
                }
            }
        }

        currentCoroutineContext().ensureActive()

        // 导出阅读配置
        if (selectedFiles.contains(ReadBookConfig.configFileName)) {
            writeJsonToFile(ReadBookConfig.getBackupConfigList(), ReadBookConfig.configFileName, backupPath)
        }
        if (selectedFiles.contains(ReadBookConfig.shareConfigFileName)) {
            writeJsonToFile(ReadBookConfig.getBackupShareConfig(), ReadBookConfig.shareConfigFileName, backupPath)
        }

        // 导出主题配置
        if (selectedFiles.contains(ThemeConfig.configFileName)) {
            writeJsonToFile(ThemeConfig.configList, ThemeConfig.configFileName, backupPath)
        }

        // 导出直链上传配置
        if (selectedFiles.contains(DirectLinkUpload.ruleFileName)) {
            DirectLinkUploadRepository().createBackupJson()?.let {
                FileUtils.createFileIfNotExist(backupPath + File.separator + DirectLinkUpload.ruleFileName)
                    .writeText(it)
            }
        }

        // 导出封面规则配置
        if (selectedFiles.contains(BookCover.configFileName)) {
            BookCover.getConfig()?.let {
                writeJsonToFile(it, BookCover.configFileName, backupPath)
            }
        }

        currentCoroutineContext().ensureActive()
        MemoryPressure.trimIfNeeded()

        // 导出SharedPreferences配置（应用主配置）
        if (selectedFiles.contains("config.xml")) {
            appCtx.getSharedPreferences(backupPath, "config")?.let { sp ->
                val edit = sp.edit()
                edit.clear()
                appCtx.defaultSharedPreferences.all.forEach { (key, value) ->
                    if (BackupConfig.keyIsNotIgnore(key)) {
                        when (key) {
                            // WebDav密码需要加密存储
                            PreferKey.webDavPassword -> {
                                edit.putString(key, aes.runCatching {
                                    encryptBase64(value.toString())
                                }.getOrDefault(value.toString()))
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
                edit.commit()
            }
        }

        currentCoroutineContext().ensureActive()

        // 导出视频播放配置
        if (selectedFiles.contains("videoConfig.xml")) {
            appCtx.getSharedPreferences(backupPath, "videoConfig")?.let { sp ->
                sp.edit(commit = true) {
                    clear()
                    appCtx.getSharedPreferences(VIDEO_PREF_NAME, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                        when (value) {
                            is Int -> putInt(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is String -> putString(key, value)
                        }
                    }
                }
            }
        }

        currentCoroutineContext().ensureActive()

        // 打包成ZIP文件
        if (selectedFiles.contains("bg")) {
            stageBackgroundImageFiles(backupPath)
        }
        if (selectedFiles.contains(HighlightRuleStore.backupFileName)) {
            stageHighlightRuleBackgroundFiles(backupPath)
        }
        if (selectedFiles.contains(runtimeSourceCacheFileName)) {
            stageRuntimeSourceCaches(backupPath)
        }
        if (selectedFiles.contains(bookCacheFolderName)) {
            stageBookCache(backupPath)
            // 备份书籍缓存时，同时备份对应书籍的章节目录
            stageBookChapterForCache(backupPath)
        }

        currentCoroutineContext().ensureActive()
        MemoryPressure.trimIfNeeded()

        val zipFileName = getNowZipFileName()
        val paths = getBackupPaths()
        FileUtils.delete(zipFilePath)
        FileUtils.delete(zipFilePath.replace("tmp_", ""))

        // 根据配置决定使用固定文件名还是带日期的文件名
        val backupFileName = if (AppConfig.onlyLatestBackup) {
            "backup.zip"
        } else {
            zipFileName
        }

        if (ZipUtils.zipFiles(paths, zipFilePath)) {
            MemoryPressure.trimIfNeeded()
            // 复制到目标目录
            when {
                path.isNullOrBlank() -> {
                    copyBackup(context.getExternalFilesDir(null)!!, backupFileName)
                }

                path.isContentScheme() -> {
                    copyBackup(context, path.toUri(), backupFileName)
                }

                else -> {
                    copyBackup(File(path), backupFileName)
                }
            }

            // 上传到WebDav云端
            LocalConfig.lastBackup = System.currentTimeMillis()

            try {
                AppWebDav.backUpWebDav(zipFileName)
            } catch (e: Exception) {
                AppLog.put("上传备份至webdav失败\n$e", e)
            }
        }

        // 清理临时文件
        FileUtils.delete(backupPath)
        FileUtils.delete(zipFilePath)

        currentCoroutineContext().ensureActive()
        MemoryPressure.trimNow(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)

        // 上传背景图片到WebDav
        AppWebDav.upBgs(getBackgroundImageFiles().toTypedArray())
    }

    /**
     * 将列表数据写入JSON文件
     * 
     * @param list 要写入的数据列表
     * @param fileName 目标文件名
     * @param path 目标目录路径
     */
    private suspend fun writeListToJson(list: List<Any>, fileName: String, path: String) {
        currentCoroutineContext().ensureActive()
        withContext(IO) {
            LogUtils.d(TAG, "阅读备份 $fileName 列表大小 ${list.size}")
            val file = writeJsonToFile(list, fileName, path)
            LogUtils.d(TAG, "阅读备份 $fileName 写入大小 ${file.length()}")
        }
    }

    private fun writeJsonToFile(any: Any, fileName: String, path: String): File {
        val file = FileUtils.createFileIfNotExist(path + File.separator + fileName)
        file.outputStream().buffered().use {
            GSON.writeToOutputStream(it, any)
        }
        MemoryPressure.trimIfNeeded()
        return file
    }

    /**
     * 复制备份文件到SAF（Storage Access Framework）目录
     * 用于Android 10+的分区存储
     * 
     * @param context Android Context
     * @param uri 目标目录URI
     * @param fileName 备份文件名
     * @throws Exception 创建文件或写入失败时抛出异常
     */
    @Throws(Exception::class)
    @Suppress("SameParameterValue")
    private fun copyBackup(context: Context, uri: Uri, fileName: String) {
        val treeDoc = DocumentFile.fromTreeUri(context, uri)!!
        treeDoc.findFile(fileName)?.delete()
        val fileDoc = treeDoc.createFile("", fileName)
            ?: throw NoStackTraceException("创建文件失败")
        val outputS = fileDoc.openOutputStream()
            ?: throw NoStackTraceException("打开OutputStream失败")
        outputS.use {
            FileInputStream(zipFilePath).use { inputS ->
                inputS.copyTo(outputS)
            }
        }
    }

    /**
     * 复制备份文件到普通文件目录
     * 
     * @param rootFile 目标目录
     * @param fileName 备份文件名
     * @throws Exception 写入失败时抛出异常
     */
    @Throws(Exception::class)
    @Suppress("SameParameterValue")
    private fun copyBackup(rootFile: File, fileName: String) {
        FileInputStream(File(zipFilePath)).use { inputS ->
            val file = FileUtils.createFileIfNotExist(rootFile, fileName)
            FileOutputStream(file).use { outputS ->
                inputS.copyTo(outputS)
            }
        }
    }

    /**
     * 备份书籍缓存到临时目录
     * 
     * 流程：
     * 1. 获取用户选择要备份的书籍列表
     * 2. 为每本书创建索引信息（bookUrl、书名、作者、文件夹名、章节列表）
     * 3. 复制缓存文件到备份目录
     * 4. 保存索引文件
     * 
     * @param rootPath 备份临时目录路径
     */
    internal fun stageBookCache(rootPath: String) {
        val selectedBooks = BookCacheSelectorConfig.getSelectedBooks()
        if (selectedBooks.isEmpty()) {
            LogUtils.d(TAG, "没有选中要备份缓存的书籍")
            return
        }
        
        val cacheDir = File(BookHelp.cachePath)
        if (!cacheDir.exists() || !cacheDir.isDirectory) {
            LogUtils.d(TAG, "书籍缓存目录不存在")
            return
        }
        
        val bookCacheIndexList = mutableListOf<BookCacheIndex>()
        val targetCacheDir = File(rootPath, bookCacheFolderName).createFolderIfNotExist()
        
        selectedBooks.forEach { book ->
            val folderName = book.getFolderNameNoCache()
            val bookFolder = File(cacheDir, folderName)
            
            if (!bookFolder.exists() || !bookFolder.isDirectory) {
                LogUtils.d(TAG, "书籍缓存文件夹不存在: ${book.name}")
                return@forEach
            }
            
            // 获取书籍的章节列表
            val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
            val chapterMap = chapterList.associateBy { it.index }
            
            // 收集章节缓存信息
            val chapterCacheInfos = mutableListOf<ChapterCacheInfo>()
            bookFolder.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".nb")) {
                    val chapterInfo = parseChapterFileName(file.name, chapterMap)
                    if (chapterInfo != null) {
                        chapterCacheInfos.add(chapterInfo)
                    }
                }
            }
            
            bookCacheIndexList.add(BookCacheIndex(
                bookUrl = book.bookUrl,
                bookName = book.name,
                author = book.author,
                folderName = folderName,
                chapters = chapterCacheInfos.sortedBy { it.index }
            ))
            
            val targetBookDir = File(targetCacheDir, folderName).createFolderIfNotExist()
            bookFolder.copyRecursively(targetBookDir, overwrite = true)
            LogUtils.d(TAG, "备份书籍缓存: ${book.name} -> $folderName, 章节数: ${chapterCacheInfos.size}")
        }
        
        if (bookCacheIndexList.isNotEmpty()) {
            writeJsonToFile(bookCacheIndexList, bookCacheIndexFileName, rootPath)
            LogUtils.d(TAG, "书籍缓存索引已保存，共 ${bookCacheIndexList.size} 本书")
        }
    }
    
    /**
     * 解析章节文件名，获取章节缓存信息
     * 
     * 文件名格式：{章节序号(5位)}-{标题MD5}.nb
     * 例如：00001-abc123def456.nb
     * 
     * @param fileName 文件名
     * @param chapterMap 章节序号 -> 章节对象 的映射
     * @return 章节缓存信息，解析失败返回null
     */
    private fun parseChapterFileName(
        fileName: String,
        chapterMap: Map<Int, BookChapter>
    ): ChapterCacheInfo? {
        if (!fileName.endsWith(".nb")) return null
        
        val nameWithoutExt = fileName.removeSuffix(".nb")
        val parts = nameWithoutExt.split("-")
        if (parts.size != 2) return null
        
        val index = parts[0].toIntOrNull() ?: return null
        val titleMD5 = parts[1]
        
        val chapter = chapterMap[index] ?: return null
        
        return ChapterCacheInfo(
            index = index,
            title = chapter.title,
            titleMD5 = titleMD5,
            fileName = fileName
        )
    }
    
    /**
     * 备份选中书籍的章节目录
     * 与书籍缓存一起备份，确保恢复后能直接阅读
     * 
     * @param rootPath 备份临时目录路径
     */
    internal suspend fun stageBookChapterForCache(rootPath: String) {
        val selectedBooks = BookCacheSelectorConfig.getSelectedBooks()
        if (selectedBooks.isEmpty()) {
            return
        }
        
        val allChapters = mutableListOf<BookChapter>()
        selectedBooks.forEach { book ->
            val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
            allChapters.addAll(chapters)
        }
        
        if (allChapters.isNotEmpty()) {
            val fileName = "bookChapterCache.json"
            writeListToJson(allChapters, fileName, rootPath)
            LogUtils.d(TAG, "章节目录已备份，共 ${allChapters.size} 章")
        }
    }

    /**
     * 清理备份缓存
     * 删除临时目录和临时ZIP文件
     */
    fun clearCache() {
        FileUtils.delete(backupPath)
        FileUtils.delete(restorePath)
        FileUtils.delete(zipFilePath)
    }
}
