package io.legado.app.ui.main.bookshelf

import android.app.Application
import androidx.lifecycle.MutableLiveData
import com.google.gson.stream.JsonWriter
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * 书架ViewModel
 * 处理书架相关的业务逻辑，包括书籍的导入、导出等功能
 */
class BookshelfViewModel(application: Application) : BaseViewModel(application) {
    /** 添加书籍进度LiveData，-1表示完成 */
    val addBookProgressLiveData = MutableLiveData(-1)
    /** 添加书籍的协程任务 */
    var addBookJob: Coroutine<*>? = null

    /**
     * 通过URL添加书籍
     * 支持批量添加，每行一个URL
     * 会自动识别书源并获取书籍信息
     * @param bookUrls 书籍URL列表，支持多行
     */
    fun addBookByUrl(bookUrls: String) {
        var successCount = 0
        addBookJob = execute {
            val hasBookUrlPattern: List<BookSourcePart> by lazy {
                appDb.bookSourceDao.hasBookUrlPattern
            }
            val urls = bookUrls.split("\n")
            for (url in urls) {
                val bookUrl = url.trim()
                if (bookUrl.isEmpty()) continue
                if (appDb.bookDao.getBook(bookUrl) != null) {
                    successCount++
                    continue
                }
                val baseUrl = NetworkUtils.getBaseUrl(bookUrl) ?: continue
                var source: BookSource? = null
                val urlMatcher = AnalyzeUrl.paramPattern.matcher(bookUrl)
                if (urlMatcher.find()) { //指定书源
                    val origin = GSON.fromJsonObject<AnalyzeUrl.UrlOption>(
                        bookUrl.substring(urlMatcher.end())
                    ).getOrNull()?.getOrigin()
                    try {
                        origin?.let {
                            appDb.bookSourceDao.getBookSource(it)?.let { bs ->
                                if (bookUrl.matches(bs.bookUrlPattern!!.toRegex())) {
                                    source = bs
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                if (source == null) { //根据域名找书源
                    source = appDb.bookSourceDao.getBookSourceAddBook(baseUrl)
                }
                if (source == null) {
                    for (bookSource in hasBookUrlPattern) { //在所有启用的书源中查找
                        try {
                            val bs = bookSource.getBookSource()!!
                            if (bookUrl.matches(bs.bookUrlPattern!!.toRegex())) {
                                source = bs
                                break
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
                val bookSource = source ?: continue
                val book = Book(
                    bookUrl = bookUrl,
                    origin = bookSource.bookSourceUrl,
                    originName = bookSource.bookSourceName
                )
                kotlin.runCatching {
                    WebBook.getBookInfoAwait(bookSource, book)
                }.onSuccess {
                    val dbBook = appDb.bookDao.getBook(it.name, it.author)
                    if (dbBook != null) {
                        val toc = WebBook.getChapterListAwait(bookSource, it).getOrThrow()
                        dbBook.migrateTo(it, toc)
                        appDb.bookDao.insert(it)
                        appDb.bookChapterDao.insert(*toc.toTypedArray())
                    } else {
                        it.order = appDb.bookDao.minOrder - 1
                        it.save()
                    }
                    successCount++
                    addBookProgressLiveData.postValue(successCount)
                }
            }
        }.onSuccess {
            if (successCount > 0) {
                context.toastOnUi(R.string.success)
            } else {
                context.toastOnUi("添加网址失败")
            }
        }.onError {
            AppLog.put("添加网址出错\n${it.localizedMessage}", it, true)
        }.onFinally {
            addBookProgressLiveData.postValue(-1)
        }
    }

    /**
     * 导出书架书籍到JSON文件
     * 将书籍信息（书名、作者、简介）导出为JSON数组格式的文件
     * @param books 要导出的书籍列表
     * @param success 导出成功回调，返回File对象
     */
    fun exportBookshelf(books: List<Book>?, success: (file: File) -> Unit) {
        execute {
            books?.let {
                val path = "${context.filesDir}/books.json"
                FileUtils.delete(path)
                val file = FileUtils.createFileWithReplace(path)
                FileOutputStream(file).use { out ->
                    val writer = JsonWriter(OutputStreamWriter(out, "UTF-8"))
                    writer.setIndent("  ")
                    writer.beginArray()
                    books.forEach {
                        val bookMap = hashMapOf<String, String?>()
                        bookMap["name"] = it.name
                        bookMap["author"] = it.author
                        bookMap["intro"] = it.getDisplayIntro()
                        GSON.toJson(bookMap, bookMap::class.java, writer)
                    }
                    writer.endArray()
                    writer.close()
                }
                file
            } ?: throw NoStackTraceException("书籍不能为空")
        }.onSuccess {
            success(it)
        }.onError {
            context.toastOnUi("导出书籍出错\n${it.localizedMessage}")
        }
    }

    /**
     * 导入书架书籍
     * 支持从URL导入或直接输入JSON字符串
     * @param str URL地址或JSON字符串
     * @param groupId 导入后添加到指定分组，0表示不分组
     */
    fun importBookshelf(str: String, groupId: Long) {
        execute {
            val text = str.trim()
            when {
                text.isAbsUrl() -> {
                    okHttpClient.newCallResponseBody {
                        url(text)
                    }.decompressed().text().let {
                        importBookshelf(it, groupId)
                    }
                }

                text.isJsonArray() -> {
                    importBookshelfByJson(text, groupId)
                }

                else -> {
                    throw NoStackTraceException("格式不对")
                }
            }
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }

    /**
     * 通过JSON导入书籍到书架
     * 解析JSON数组，根据书名和作者搜索匹配书籍并添加到书架
     * 会跳过已存在的书籍（根据书名和作者判断）
     * @param json JSON格式的书籍列表
     * @param groupId 导入后添加到指定分组，0表示不分组
     */
    private fun importBookshelfByJson(json: String, groupId: Long) {
        execute {
            val bookSourceParts = appDb.bookSourceDao.allEnabledPart
            val semaphore = Semaphore(AppConfig.threadCount)
            GSON.fromJsonArray<Map<String, String?>>(json).getOrThrow().forEach { bookInfo ->
                val name = bookInfo["name"] ?: ""
                val author = bookInfo["author"] ?: ""
                if (name.isEmpty() || appDb.bookDao.has(name, author)) {
                    return@forEach
                }
                semaphore.withPermit {
                    WebBook.preciseSearch(
                        this, bookSourceParts, name, author,
                        semaphore = semaphore
                    ).onSuccess {
                        val book = it.first
                        if (groupId > 0) {
                            book.group = groupId
                        }
                        book.save()
                    }.onError { e ->
                        context.toastOnUi(e.localizedMessage)
                    }
                }
            }
        }.onError {
            it.printOnDebug()
        }.onFinally {
            context.toastOnUi(R.string.success)
        }
    }

}
