package io.legado.app.model.webBook

import android.text.TextUtils
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.debug.FlowLogRecorder
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isWebFile
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setToastRuleType
import io.legado.app.model.debug.DataFlowStage
import io.legado.app.model.debug.FieldFillRecord
import io.legado.app.model.debug.recordField
import io.legado.app.utils.DebugLog
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StringUtils.wordCountFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx


/**
 * 获取书籍详情
 * 书籍详情解析器
 *
 * 负责解析书籍的详情页信息，包括：
 * - 书名、作者
 * - 分类、字数
 * - 最新章节、简介
 * - 封面链接
 * - 目录链接/下载链接
 *
 * 支持详情页初始化规则（init），用于预处理页面结构。
 *
 * @see WebBook.getBookInfo 网络请求入口
 * @see BookInfoRule 详情规则定义
 */
object BookInfo {

    /**
     * 解析书籍详情
     *
     * @param bookSource 书源
     * @param book 书籍对象（需包含bookUrl）
     * @param baseUrl 基础URL
     * @param redirectUrl 重定向后的URL
     * @param body 页面内容
     * @param canReName 是否允许重命名书名和作者
     * @throws NoStackTraceException 当内容为空时抛出
     */
    @Throws(Exception::class)
    suspend fun analyzeBookInfo(
        bookSource: BookSource,
        book: Book,
        baseUrl: String,
        redirectUrl: String,
        body: String?,
        canReName: Boolean,
    ) {
        body ?: throw NoStackTraceException(
            appCtx.getString(R.string.error_get_web_content, baseUrl)
        )
        Debug.log(bookSource.bookSourceUrl, "≡获取成功:${baseUrl}")
        Debug.log(bookSource.bookSourceUrl, body, state = 20)
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(body).setBaseUrl(baseUrl)
        analyzeRule.setRedirectUrl(redirectUrl)
        analyzeRule.setCoroutineContext(currentCoroutineContext())
        analyzeRule.setToastRuleType("BOOK_INFO")
        analyzeBookInfo(book, body, analyzeRule, bookSource, baseUrl, redirectUrl, canReName)
    }

    /**
     * 解析书籍详情（内部方法）
     *
     * 使用已有的AnalyzeRule对象解析详情页，避免重复创建。
     * 支持详情页初始化规则（init），用于预处理页面结构。
     *
     * @param book 书籍对象
     * @param body 页面内容
     * @param analyzeRule 规则解析器
     * @param bookSource 书源
     * @param baseUrl 基础URL
     * @param redirectUrl 重定向后的URL
     * @param canReName 是否允许重命名书名和作者
     */
    suspend fun analyzeBookInfo(
        book: Book,
        body: String,
        analyzeRule: AnalyzeRule,
        bookSource: BookSource,
        baseUrl: String,
        redirectUrl: String,
        canReName: Boolean,
    ) {
        val infoRule = bookSource.getBookInfoRule()
        infoRule.init?.let {
            if (it.isNotBlank()) {
                currentCoroutineContext().ensureActive()
                Debug.log(bookSource.bookSourceUrl, "≡执行详情页初始化规则")
                analyzeRule.setContent(analyzeRule.getElement(it))
            }
        }
        val mCanReName = canReName && !infoRule.canReName.isNullOrBlank()
        
        val debugEnabled = FlowLogRecorder.isEnabled
        val dataFlowFields = if (debugEnabled) mutableListOf<FieldFillRecord>() else null
        
        if (debugEnabled) {
            FlowLogRecorder.logExtract(
                source = bookSource,
                message = "开始提取书籍信息字段",
                book = book,
                bookSource = bookSource
            )
        }
        
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取书名")
        val originalName = book.name
        BookHelp.formatBookName(analyzeRule.getString(infoRule.name)).let {
            if (it.isNotEmpty() && (mCanReName || book.name.isEmpty())) {
                book.name = it
            }
            Debug.log(bookSource.bookSourceUrl, "└${it}")
            
            if (debugEnabled) {
                FlowLogRecorder.logExtract(
                    source = bookSource,
                    message = "提取书名",
                    rule = infoRule.name,
                    result = it,
                    originalValue = originalName.takeIf { it.isNotEmpty() },
                    book = book,
                    bookSource = bookSource
                )
            }
            
            dataFlowFields?.recordField(
                "name",
                rule = infoRule.name,
                result = it,
                original = originalName
            )
        }
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取作者")
        val originalAuthor = book.author
        BookHelp.formatBookAuthor(analyzeRule.getString(infoRule.author)).let {
            if (it.isNotEmpty() && (mCanReName || book.author.isEmpty())) {
                book.author = it
            }
            Debug.log(bookSource.bookSourceUrl, "└${it}")
            
            if (debugEnabled) {
                FlowLogRecorder.logExtract(
                    source = bookSource,
                    message = "提取作者",
                    rule = infoRule.author,
                    result = it,
                    originalValue = originalAuthor.takeIf { it.isNotEmpty() },
                    book = book,
                    bookSource = bookSource
                )
            }
            
            dataFlowFields?.recordField(
                "author",
                rule = infoRule.author,
                result = it,
                original = originalAuthor
            )
        }
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取分类")
        try {
            val originalKind = book.kind
            analyzeRule.getStringList(infoRule.kind)
                ?.joinToString(",")
                ?.let {
                    if (it.isNotEmpty()) book.kind = it
                    Debug.log(bookSource.bookSourceUrl, "└${it}")
                    
                    if (debugEnabled) {
                        FlowLogRecorder.logExtract(
                            source = bookSource,
                            message = "提取分类",
                            rule = infoRule.kind,
                            result = it,
                            originalValue = originalKind,
                            book = book,
                            bookSource = bookSource
                        )
                    }
                    
                    dataFlowFields?.recordField(
                        "kind",
                        rule = infoRule.kind,
                        result = it,
                        original = originalKind
                    )
                } ?: Debug.log(bookSource.bookSourceUrl, "└")
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("获取分类出错", e)
            
            if (debugEnabled) {
                FlowLogRecorder.logExtract(
                    source = bookSource,
                    message = "提取分类",
                    rule = infoRule.kind,
                    error = e,
                    book = book,
                    bookSource = bookSource
                )
            }
            
            dataFlowFields?.recordField(
                "kind",
                rule = infoRule.kind,
                isError = true,
                errorMessage = e.localizedMessage
            )
        }
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取字数")
        try {
            val originalWordCount = book.wordCount
            wordCountFormat(analyzeRule.getString(infoRule.wordCount)).let {
                if (it.isNotEmpty()) book.wordCount = it
                Debug.log(bookSource.bookSourceUrl, "└${it}")
                
                if (debugEnabled) {
                    FlowLogRecorder.logExtract(
                        source = bookSource,
                        message = "提取字数",
                        rule = infoRule.wordCount,
                        result = it,
                        originalValue = originalWordCount?.takeIf { it.isNotEmpty() },
                        book = book,
                        bookSource = bookSource
                    )
                }
                
                dataFlowFields?.recordField(
                    "wordCount",
                    rule = infoRule.wordCount,
                    result = it,
                    original = originalWordCount
                )
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("获取字数出错", e)
            
            if (debugEnabled) {
                FlowLogRecorder.logExtract(
                    source = bookSource,
                    message = "提取字数",
                    rule = infoRule.wordCount,
                    error = e,
                    book = book,
                    bookSource = bookSource
                )
            }
            
            dataFlowFields?.recordField(
                "wordCount",
                rule = infoRule.wordCount,
                isError = true,
                errorMessage = e.localizedMessage
            )
        }
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取最新章节")
        try {
            val originalLatestChapter = book.latestChapterTitle
            analyzeRule.getString(infoRule.lastChapter).let {
                if (it.isNotEmpty()) book.latestChapterTitle = it
                Debug.log(bookSource.bookSourceUrl, "└${it}")
                
                if (debugEnabled) {
                    FlowLogRecorder.logExtract(
                        source = bookSource,
                        message = "提取最新章节",
                        rule = infoRule.lastChapter,
                        result = it,
                        originalValue = originalLatestChapter?.takeIf { it.isNotEmpty() },
                        book = book,
                        bookSource = bookSource
                    )
                }
                
                dataFlowFields?.recordField(
                    "latestChapterTitle",
                    rule = infoRule.lastChapter,
                    result = it,
                    original = originalLatestChapter
                )
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("获取最新章节出错", e)
            
            if (debugEnabled) {
                FlowLogRecorder.logExtract(
                    source = bookSource,
                    message = "提取最新章节",
                    rule = infoRule.lastChapter,
                    error = e,
                    book = book,
                    bookSource = bookSource
                )
            }
            
            dataFlowFields?.recordField(
                "latestChapterTitle",
                rule = infoRule.lastChapter,
                isError = true,
                errorMessage = e.localizedMessage
            )
        }
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取简介")
        try {
            val originalIntro = book.intro
            val intro = analyzeRule.getString(infoRule.intro)
            val introTrimS = intro.trimStart()
            if (introTrimS.startsWith("<usehtml>") || introTrimS.startsWith("<md>") || introTrimS.startsWith("<useweb>")) {
                book.intro = introTrimS
                Debug.log(bookSource.bookSourceUrl, "└${introTrimS}")
                
                if (debugEnabled) {
                    FlowLogRecorder.logExtract(
                        source = bookSource,
                        message = "提取简介",
                        rule = infoRule.intro,
                        result = introTrimS,
                        originalValue = originalIntro,
                        book = book,
                        bookSource = bookSource
                    )
                }
                
                dataFlowFields?.recordField(
                    "intro",
                    rule = infoRule.intro,
                    result = introTrimS,
                    original = originalIntro,
                    truncate = true
                )
            } else {
                HtmlFormatter.format(intro).let {
                    if (it.isNotEmpty()) book.intro = it
                    Debug.log(bookSource.bookSourceUrl, "└${it}")
                    
                    if (debugEnabled) {
                        FlowLogRecorder.logExtract(
                            source = bookSource,
                            message = "提取简介",
                            rule = infoRule.intro,
                            result = it,
                            originalValue = originalIntro,
                            book = book,
                            bookSource = bookSource
                        )
                    }
                    
                    dataFlowFields?.recordField(
                        "intro",
                        rule = infoRule.intro,
                        result = it,
                        original = originalIntro,
                        truncate = true
                    )
                }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("获取简介出错", e)
            
            if (debugEnabled) {
                FlowLogRecorder.logExtract(
                    source = bookSource,
                    message = "提取简介",
                    rule = infoRule.intro,
                    error = e,
                    book = book,
                    bookSource = bookSource
                )
            }
            
            dataFlowFields?.recordField(
                "intro",
                rule = infoRule.intro,
                isError = true,
                errorMessage = e.localizedMessage
            )
        }
        currentCoroutineContext().ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取封面链接")
        try {
            val originalCoverUrl = book.coverUrl
            analyzeRule.getString(infoRule.coverUrl).let {
                if (it.isNotEmpty()) {
                    book.coverUrl =
                        NetworkUtils.getAbsoluteURL(redirectUrl, it)
                }
                Debug.log(bookSource.bookSourceUrl, "└${it}")
                
                if (debugEnabled) {
                    FlowLogRecorder.logExtract(
                        source = bookSource,
                        message = "提取封面链接",
                        rule = infoRule.coverUrl,
                        result = book.coverUrl,
                        originalValue = originalCoverUrl,
                        book = book,
                        bookSource = bookSource
                    )
                }
                
                dataFlowFields?.recordField(
                    "coverUrl",
                    rule = infoRule.coverUrl,
                    result = book.coverUrl,
                    original = originalCoverUrl
                )
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("获取封面出错", e)
            
            if (debugEnabled) {
                FlowLogRecorder.logExtract(
                    source = bookSource,
                    message = "提取封面链接",
                    rule = infoRule.coverUrl,
                    error = e,
                    book = book,
                    bookSource = bookSource
                )
            }
            
            dataFlowFields?.recordField(
                "coverUrl",
                rule = infoRule.coverUrl,
                isError = true,
                errorMessage = e.localizedMessage
            )
        }
        currentCoroutineContext().ensureActive()
        if (!book.isWebFile) {
            Debug.log(bookSource.bookSourceUrl, "┌获取目录链接")
            val originalTocUrl = book.tocUrl
            book.tocUrl = analyzeRule.getString(infoRule.tocUrl, isUrl = true)
            if (book.tocUrl.isEmpty()) book.tocUrl = baseUrl
            if (book.tocUrl == baseUrl) {
                book.tocHtml = body
            }
            Debug.log(bookSource.bookSourceUrl, "└${book.tocUrl}")
            
            if (debugEnabled) {
                FlowLogRecorder.logExtract(
                    source = bookSource,
                    message = "提取目录链接",
                    rule = infoRule.tocUrl,
                    result = book.tocUrl,
                    originalValue = originalTocUrl.takeIf { it.isNotEmpty() },
                    book = book,
                    bookSource = bookSource
                )
            }
            
            dataFlowFields?.recordField(
                "tocUrl",
                rule = infoRule.tocUrl,
                result = book.tocUrl,
                original = originalTocUrl
            )
        } else {
            Debug.log(bookSource.bookSourceUrl, "┌获取文件下载链接")
            val originalDownloadUrls = book.downloadUrls?.joinToString("\n")
            book.downloadUrls = analyzeRule.getStringList(infoRule.downloadUrls, isUrl = true)
            if (book.downloadUrls.isNullOrEmpty()) {
                Debug.log(bookSource.bookSourceUrl, "└")
                throw NoStackTraceException("下载链接为空")
            } else {
                Debug.log(
                    bookSource.bookSourceUrl,
                    "└" + TextUtils.join("，\n", book.downloadUrls!!)
                )
                
                if (debugEnabled) {
                    FlowLogRecorder.logExtract(
                        source = bookSource,
                        message = "提取文件下载链接",
                        rule = infoRule.downloadUrls,
                        result = book.downloadUrls?.joinToString("\n"),
                        originalValue = originalDownloadUrls,
                        book = book,
                        bookSource = bookSource
                    )
                }
                
                dataFlowFields?.recordField(
                    "downloadUrls",
                    rule = infoRule.downloadUrls,
                    result = book.downloadUrls?.joinToString("\n"),
                    original = originalDownloadUrls
                )
            }
        }
        
        if (debugEnabled) {
            FlowLogRecorder.logStageDataFlow(
                source = bookSource,
                stage = DataFlowStage.BOOK_INFO,
                fields = dataFlowFields.orEmpty(),
                message = "详情阶段数据流转",
                bookUrl = book.bookUrl,
                bookName = book.name,
                author = book.author,
                book = book,
                bookSource = bookSource
            )
        }
    }

}
