package io.legado.app.ui.book.info

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.NoBooksDirException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.config.AppConfig
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.addType
import io.legado.app.help.book.getExportFileName
import io.legado.app.help.book.getRemoteUrl
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.isSameNameAuthor
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.removeType
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.book.sync
import io.legado.app.help.book.updateTo
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.webdav.ObjectNotFoundException
import io.legado.app.model.AudioPlay
import io.legado.app.model.BookCover
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadManga
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.LogUtils
import io.legado.app.utils.UrlUtil
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO

class BookInfoViewModel(application: Application) : BaseViewModel(application) {
    val bookData = MutableLiveData<Book>()
    val chapterListData = MutableLiveData<List<BookChapter>>()
    val webFiles = mutableListOf<WebFile>()
    var inBookshelf = false
    var hasCustomBtn = false
    var bookSource: BookSource? = null
    private var changeSourceCoroutine: Coroutine<*>? = null
    val waitDialogData = MutableLiveData<Boolean>()
    val actionLive = MutableLiveData<String>()
    val tocLoading = MutableLiveData<Boolean>()

    private fun logToc(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            LogUtils.d(TOC_LOG_TAG, message)
        } else {
            LogUtils.d(TOC_LOG_TAG, "$message\n${throwable.stackTraceToString()}")
        }
    }

    fun initData(intent: Intent) {
        execute {
            val name = intent.getStringExtra("name") ?: ""
            val author = intent.getStringExtra("author") ?: ""
            val bookUrl = intent.getStringExtra("bookUrl") ?: ""
            appDb.bookDao.getBook(name, author)?.let {
                inBookshelf = !it.isNotShelf
                upBook(it)
                return@execute
            }
            if (bookUrl.isNotBlank()) {
                appDb.bookDao.getBook(bookUrl)?.let {
                    inBookshelf = !it.isNotShelf
                    upBook(it)
                    return@execute
                }
                appDb.searchBookDao.getSearchBook(bookUrl)?.toBook()?.let {
                    upBook(it)
                    return@execute
                }
                intent.getSearchBookExtra()?.takeIf { it.bookUrl == bookUrl }?.toBook()?.let {
                    upBook(it)
                    return@execute
                }
            }
            appDb.searchBookDao.getFirstByNameAuthor(name, author)?.toBook()?.let {
                upBook(it)
                return@execute
            }
            throw NoStackTraceException("未找到书籍")
        }.onError {
            AppLog.put(it.localizedMessage, it)
            context.toastOnUi(it.localizedMessage)
        }
    }

    private fun Intent.getSearchBookExtra(): SearchBook? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BookInfoActivity.EXTRA_SEARCH_BOOK, SearchBook::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BookInfoActivity.EXTRA_SEARCH_BOOK)
        }
    }

    fun upBook(intent: Intent) {
        execute {
            val name = intent.getStringExtra("name") ?: ""
            val author = intent.getStringExtra("author") ?: ""
            appDb.bookDao.getBook(name, author)?.let { book ->
                upBook(book)
            }
        }
    }

    private fun upBook(book: Book) {
        execute {
            bookSource = if (book.isLocal) null else
                appDb.bookSourceDao.getBookSource(book.origin)?.also {
                    hasCustomBtn = it.customButton
                }
            bookData.postValue(book)
            upCoverByRule(book)
            if (book.tocUrl.isEmpty() && !book.isLocal) {
                logToc("upBook: tocUrl is empty, load book info")
                loadBookInfo(book, runPreUpdateJs = inBookshelf)
            } else {
                val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
                logToc("upBook: db chapters=${chapterList.size}, isTocPartialLoad=${AppConfig.isTocPartialLoad}")
                if (chapterList.isNotEmpty()) {
                    chapterListData.postValue(chapterList)
                } else {
                    loadChapter(book, isFromBookInfo = true)
                }
            }
        }
    }

    private fun upCoverByRule(book: Book) {
        execute {
            if (book.coverUrl.isNullOrBlank() && book.customCoverUrl.isNullOrBlank()) {
                val coverUrl = BookCover.searchCover(book)
                if (coverUrl.isNullOrBlank()) {
                    return@execute
                }
                book.customCoverUrl = coverUrl
                bookData.postValue(book)
                if (inBookshelf) {
                    saveBook(book)
                }
            }
        }
    }

    fun refreshBook(book: Book) {
        executeLazy(executeContext = IO) {
            if (book.isLocal) {
                book.tocUrl = ""
                book.getRemoteUrl()?.let {
                    val bookWebDav = AppWebDav.defaultBookWebDav
                        ?: throw NoStackTraceException("webDav没有配置")
                    val remoteBook = bookWebDav.getRemoteBook(it)
                    if (remoteBook == null) {
                        book.origin = BookType.localTag
                    } else if (remoteBook.lastModify > book.lastCheckTime) {
                        val uri = bookWebDav.downloadRemoteBook(remoteBook)
                        book.bookUrl = if (uri.isContentScheme()) uri.toString() else uri.path!!
                        book.lastCheckTime = remoteBook.lastModify
                    }
                }
            } else {
                val bs = bookSource ?: return@executeLazy
                if (book.originName != bs.bookSourceName) {
                    book.originName = bs.bookSourceName
                }
            }
        }.onError {
            when (it) {
                is ObjectNotFoundException -> {
                    book.origin = BookType.localTag
                }

                else -> {
                    AppLog.put("下载远程书籍<${book.name}>失败", it)
                }
            }
        }.onFinally {
            loadBookInfo(book, false)
        }.start()
    }

    fun loadBookInfo(
        book: Book,
        canReName: Boolean = true,
        runPreUpdateJs: Boolean = true,
        scope: CoroutineScope = viewModelScope
    ) {
        if (book.isLocal) {
            LocalBook.upBookInfo(book)
            bookData.postValue(book)
            loadChapter(book)
        } else {
            val bookSource = bookSource ?: let {
                chapterListData.postValue(emptyList())
                context.toastOnUi(R.string.error_no_source)
                return
            }
            logToc("loadBookInfo start: bookUrl=${book.bookUrl}")
            WebBook.getBookInfo(scope, bookSource, book, canReName = canReName)
                .onSuccess(IO) {
                    try {
                        logToc("loadBookInfo success: bookUrl=${book.bookUrl}, isWebFile=${it.isWebFile}, tocUrl=${it.tocUrl}")
                        val dbBook = appDb.bookDao.getBook(book.name, book.author)
                        if (!inBookshelf && dbBook != null && !dbBook.isNotShelf && dbBook.origin == book.origin) {
                            dbBook.updateTo(it)
                            inBookshelf = true
                        }
                        bookData.postValue(it)
                        if (inBookshelf) {
                            it.save()
                        }
                        if (it.isWebFile) {
                            logToc("loadBookInfo: isWebFile=true, load web file")
                            loadWebFile(it)
                        } else {
                            logToc("loadBookInfo: load chapter, isLocal=${it.isLocal}")
                            try {
                                loadChapter(it, runPreUpdateJs, isFromBookInfo = true)
                                logToc("loadChapter completed")
                            } catch (e: Throwable) {
                                logToc("loadChapter error: ${e.localizedMessage}", e)
                            }
                        }
                    } catch (e: Throwable) {
                        logToc("loadBookInfo onSuccess error: ${e.localizedMessage}", e)
                    }
                }.onError {
                    logToc("loadBookInfo failed: bookUrl=${book.bookUrl}, error=${it.localizedMessage}", it)
                    context.toastOnUi(R.string.error_get_book_info)
                }
        }
    }

    fun loadChapter(
        book: Book,
        runPreUpdateJs: Boolean = true,
        scope: CoroutineScope = viewModelScope,
        isFromBookInfo: Boolean = false
    ) {
        logToc("loadChapter start: bookUrl=${book.bookUrl}, isLocal=${book.isLocal}")
        if (book.isLocal) {
            execute(scope) {
                LocalBook.getChapterList(book).let {
                    appDb.bookDao.update(book)
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*it.toTypedArray())
                    ReadBook.onChapterListUpdated(book)
                    chapterListData.postValue(it)
                }
            }.onError {
                context.toastOnUi("LoadTocError:${it.localizedMessage}")
            }
        } else {
            val bookSource = bookSource ?: let {
                chapterListData.postValue(emptyList())
                context.toastOnUi(R.string.error_no_source)
                return
            }
            val oldBook = book.copy()
            if (AppConfig.isTocPartialLoad) {
                tocLoading.postValue(true)
                logToc("partial toc load start: bookUrl=${book.bookUrl}")
                execute(scope) {
                    var firstEmitChapterSize: Int? = null
                    var hasShownNextTocLazyLoadToast = false
                    WebBook.getChapterListFlow(bookSource, book, runPreUpdateJs, isFromBookInfo = isFromBookInfo)
                        .collect { partial ->
                            val chapters = partial.chapters
                            logToc("partial toc emit: count=${chapters.size}, isComplete=${partial.isComplete}, bookUrl=${book.bookUrl}")
                            if (chapters.isEmpty()) return@collect
                            val firstSize = firstEmitChapterSize
                            if (firstSize == null) {
                                firstEmitChapterSize = chapters.size
                            } else if (!hasShownNextTocLazyLoadToast && chapters.size > firstSize) {
                                hasShownNextTocLazyLoadToast = true
                                context.toastOnUi("下一页目录懒加载已成功执行")
                            }
                            if (partial.isComplete) {
                                updatePartialBookChapterSummary(book, chapters, oldBook.totalChapterNum)
                                // 目录全部加载完成
                                if (inBookshelf) {
                                    saveShelfBook(oldBook, book, removeUpdateError = true)
                                } else {
                                    book.addType(BookType.notShelf)
                                    book.save()
                                }
                                replaceBookChapters(oldBook, chapters)
                                if (inBookshelf) {
                                    ReadBook.onChapterListUpdated(book)
                                }
                                chapterListData.postValue(chapters)
                            } else {
                                // 中间过程：增量保存到数据库，通知目录视图刷新
                                updatePartialBookChapterSummary(book, chapters, oldBook.totalChapterNum)
                                if (inBookshelf) {
                                    saveShelfBook(oldBook, book)
                                } else {
                                    book.addType(BookType.notShelf)
                                    book.save()
                                }
                                savePartialBookChapters(chapters)
                                chapterListData.postValue(chapters)
                                ReadBook.onChapterListUpdated(book, loadContent = false, isIncremental = true)
                            }
                            postEvent(EventBus.TOC_PARTIAL_LOADED, book.bookUrl)
                        }
                }.onError {
                    chapterListData.postValue(emptyList())
                    AppLog.put("获取目录失败\n${it.localizedMessage}", it)
                    context.toastOnUi(R.string.error_get_chapter_list)
                }.onFinally {
                    tocLoading.postValue(false)
                    postEvent(EventBus.TOC_LOAD_COMPLETE, book.bookUrl)
                }
            } else {
                WebBook.getChapterList(scope, bookSource, book, runPreUpdateJs, isFromBookInfo = isFromBookInfo)
                    .onSuccess(IO) {
                        if (inBookshelf) {
                            saveShelfBook(oldBook, book, removeUpdateError = true)
                            replaceBookChapters(oldBook, it)
                            ReadBook.onChapterListUpdated(book)
                        }
                        chapterListData.postValue(it)
                    }.onError {
                        chapterListData.postValue(emptyList())
                        AppLog.put("获取目录失败\n${it.localizedMessage}", it)
                        context.toastOnUi(R.string.error_get_chapter_list)
                    }
            }
        }
    }

    private fun updatePartialBookChapterSummary(
        book: Book,
        chapters: List<BookChapter>,
        baseTotalChapterNum: Int
    ) {
        val replaceRules = ContentProcessor.get(book).getTitleReplaceRules()
        val replaceBook = book.toReplaceBook()
        val currentChapter = chapters.getOrNull(book.durChapterIndex) ?: chapters.firstOrNull()
        if (book.durChapterTitle.isNullOrBlank()) {
            book.durChapterTitle = currentChapter?.getDisplayTitle(
                replaceRules,
                book.getUseReplaceRule(),
                replaceBook = replaceBook
            )
        }
        if (baseTotalChapterNum < chapters.size) {
            book.lastCheckCount = chapters.size - baseTotalChapterNum
            book.latestChapterTime = System.currentTimeMillis()
        }
        book.lastCheckTime = System.currentTimeMillis()
        book.totalChapterNum = chapters.size
        book.latestChapterTitle = chapters.getOrElse(book.simulatedTotalChapterNum() - 1) { chapters.last() }
            .getDisplayTitle(
                replaceRules,
                book.getUseReplaceRule(),
                replaceBook = replaceBook
            )
    }

    private fun saveShelfBook(
        oldBook: Book,
        book: Book,
        removeUpdateError: Boolean = false
    ) {
        book.sync(oldBook)
        if (removeUpdateError) {
            book.removeType(BookType.updateError)
        }
        if (oldBook.bookUrl == book.bookUrl) {
            appDb.bookDao.update(book)
        } else {
            appDb.bookDao.replace(oldBook, book)
            BookHelp.updateCacheFolder(oldBook, book)
        }
    }

    private fun replaceBookChapters(oldBook: Book, chapters: List<BookChapter>) {
        appDb.bookChapterDao.delByBook(oldBook.bookUrl)
        appDb.bookChapterDao.insert(*chapters.toTypedArray())
    }

    private fun savePartialBookChapters(chapters: List<BookChapter>) {
        appDb.bookChapterDao.insert(*chapters.toTypedArray())
    }


    fun loadGroup(groupId: Long, success: ((groupNames: String?) -> Unit)) {
        execute {
            appDb.bookGroupDao.getGroupNames(groupId).joinToString(",")
        }.onSuccess {
            success.invoke(it)
        }
    }

    private fun loadWebFile(book: Book) {
        execute {
            webFiles.clear()
            val fileNameNoExtension = if (book.author.isBlank()) book.name
            else "${book.name} 作者：${book.author}"
            book.downloadUrls!!.map {
                val analyzeUrl = AnalyzeUrl(
                    it, source = bookSource,
                    coroutineContext = coroutineContext
                )
                var mFileName = UrlUtil.getFileName(analyzeUrl)
                    ?: fileNameNoExtension
                analyzeUrl.type?.let { suffix ->
                    mFileName += ".${suffix}"
                }
                WebFile(it, mFileName)
            }
        }.onError {
            context.toastOnUi("LoadWebFileError\n${it.localizedMessage}")
        }.onSuccess {
            webFiles.addAll(it)
            book.latestChapterTitle = "已下载"
            bookData.postValue(book)
            chapterListData.postValue(emptyList())
        }
    }

    /* 导入或者下载在线文件 */
    fun <T> importOrDownloadWebFile(webFile: WebFile, success: ((T) -> Unit)?) {
        bookSource ?: return
        execute {
            waitDialogData.postValue(true)
            if (webFile.isSupported) {
                val book = LocalBook.importFileOnLine(
                    webFile.url,
                    bookData.value!!.getExportFileName(webFile.suffix),
                    bookSource
                )
                changeToLocalBook(book)
            } else {
                LocalBook.saveBookFile(
                    webFile.url,
                    bookData.value!!.getExportFileName(webFile.suffix),
                    bookSource
                )
            }
        }.onSuccess {
            @Suppress("unchecked_cast")
            success?.invoke(it as T)
        }.onError {
            when (it) {
                is NoBooksDirException -> actionLive.postValue("selectBooksDir")
                else -> {
                    AppLog.put("ImportWebFileError\n${it.localizedMessage}", it)
                    context.toastOnUi("ImportWebFileError\n${it.localizedMessage}")
                    webFiles.remove(webFile)
                }
            }
        }.onFinally {
            waitDialogData.postValue(false)
        }
    }

    fun getArchiveFilesName(archiveFileUri: Uri, onSuccess: (List<String>) -> Unit) {
        execute {
            ArchiveUtils.getArchiveFilesName(archiveFileUri) {
                AppPattern.bookFileRegex.matches(it)
            }
        }.onError {
            AppLog.put("getArchiveEntriesName Error:\n${it.localizedMessage}", it)
            context.toastOnUi("getArchiveEntriesName Error:\n${it.localizedMessage}")
        }.onSuccess {
            onSuccess.invoke(it)
        }
    }

    fun importArchiveBook(
        archiveFileUri: Uri,
        archiveEntryName: String,
        success: ((Book) -> Unit)? = null
    ) {
        execute {
            val suffix = archiveEntryName.substringAfterLast(".")
            LocalBook.importArchiveFile(
                archiveFileUri,
                bookData.value!!.getExportFileName(suffix)
            ) {
                it.contains(archiveEntryName)
            }.first()
        }.onSuccess {
            val book = changeToLocalBook(it)
            success?.invoke(book)
        }.onError {
            AppLog.put("importArchiveBook Error:\n${it.localizedMessage}", it)
            context.toastOnUi("importArchiveBook Error:\n${it.localizedMessage}")
        }
    }

    fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = execute {
            bookSource = source.also {
                hasCustomBtn = it.customButton
            }
            bookData.value?.migrateTo(book, toc)
            if (book.isWebFile) {
                loadWebFile(book)
            }
            if (inBookshelf) {
                book.removeType(BookType.updateError)
                bookData.value?.delete()
                appDb.bookDao.insert(book)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
            }
            bookData.postValue(book)
            chapterListData.postValue(toc)
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }
    }

    fun topBook() {
        execute {
            bookData.value?.let { book ->
                val minOrder = appDb.bookDao.minOrder
                book.order = minOrder - 1
                book.durChapterTime = System.currentTimeMillis()
                appDb.bookDao.update(book)
            }
        }
    }

    fun saveBook(book: Book?, success: (() -> Unit)? = null) {
        book ?: return
        execute {
            if (book.order == 0) {
                book.order = appDb.bookDao.minOrder - 1
            }
            appDb.bookDao.getBook(book.name, book.author)?.let {
                book.durChapterIndex = it.durChapterIndex
                book.durChapterPos = it.durChapterPos
                book.durChapterTitle = it.durChapterTitle
            }
            book.save()
            if (ReadBook.book?.isSameNameAuthor(book) == true) {
                ReadBook.book = book
            } else if (AudioPlay.book?.isSameNameAuthor(book) == true) {
                AudioPlay.book = book
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun saveChapterList(success: (() -> Unit)?) {
        execute {
            chapterListData.value?.let {
                appDb.bookChapterDao.insert(*it.toTypedArray())
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun addToBookshelf(success: (() -> Unit)?) { //点击书架按钮或在加分组时触发
        execute {
            bookData.value?.let { book ->
                book.removeType(BookType.notShelf)
                if (book.order == 0) {
                    book.order = appDb.bookDao.minOrder - 1
                }
                appDb.bookDao.getBook(book.name, book.author)?.let {
                    book.durChapterIndex = it.durChapterIndex
                    book.durChapterPos = it.durChapterPos
                    book.durChapterTitle = it.durChapterTitle
                }
                if (ReadBook.book?.isSameNameAuthor(book) == true) {
                    ReadBook.book = book
                } else if (AudioPlay.book?.isSameNameAuthor(book) == true) {
                    AudioPlay.book = book
                }
                book.save()
                SourceCallBack.callBackBook(SourceCallBack.ADD_BOOK_SHELF, bookSource, book)
            }
            chapterListData.value?.let {
                appDb.bookChapterDao.insert(*it.toTypedArray())
            }
            inBookshelf = true
        }.onSuccess {
            success?.invoke()
        }
    }

    fun getBook(toastNull: Boolean = true): Book? {
        val book = bookData.value
        if (toastNull && book == null) {
            context.toastOnUi("book is null")
        }
        return book
    }

    fun delBook(deleteOriginal: Boolean = false, success: (() -> Unit)? = null) {
        execute {
            bookData.value?.let {
                it.delete()
                inBookshelf = false
                if (it.isLocal) {
                    LocalBook.deleteBook(it, deleteOriginal)
                }
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun clearCache(book: Book) {
        execute {
            BookHelp.clearCache(book)
            if (ReadBook.book?.bookUrl == book.bookUrl) {
                ReadBook.clearTextChapter()
            }
            if (ReadManga.book?.bookUrl == book.bookUrl) {
                ReadManga.clearMangaChapter()
            }
        }.onSuccess {
            context.toastOnUi(R.string.clear_cache_success)
        }.onError {
            context.toastOnUi("清理缓存出错\n${it.localizedMessage}")
        }
    }

    fun upEditBook() {
        bookData.value?.let {
            appDb.bookDao.getBook(it.bookUrl)?.let { book ->
                bookData.postValue(book)
            }
        }
    }

    private fun changeToLocalBook(localBook: Book): Book {
        return LocalBook.mergeBook(localBook, bookData.value).let {
            bookData.postValue(it)
            loadChapter(it)
            inBookshelf = true
            it
        }
    }

    fun onButtonClick(activity: AppCompatActivity, name: String, click: String) {
        val source = bookSource ?: return
        val book = bookData.value ?: return
        execute {
            val java = SourceLoginJsExtensions(activity, source)
            runScriptWithContext {
                source.evalJS(click) {
                    put("result", null)
                    put("java", java)
                    put("book", book)
                }
            }
        }.onError {
            AppLog.put("${source.bookSourceName}: ${it.localizedMessage}", it)
            context.toastOnUi("$name click error\n${it.localizedMessage}")
        }
    }

    private companion object {
        const val TOC_LOG_TAG = "BookInfoTOC"
    }

    data class WebFile(
        val url: String,
        val name: String,
    ) {

        override fun toString(): String {
            return name
        }

        // 后缀
        val suffix: String = UrlUtil.getSuffix(name)

        // txt epub umd pdf等文件
        val isSupported: Boolean = AppPattern.bookFileRegex.matches(name)

        // 压缩包形式的txt epub umd pdf文件
        val isSupportDecompress: Boolean = AppPattern.archiveFileRegex.matches(name)

    }

}
