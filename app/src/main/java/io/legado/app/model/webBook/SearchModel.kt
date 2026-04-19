package io.legado.app.model.webBook

import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.mapParallelSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import splitties.init.appCtx
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * 多源并发搜索模型
 *
 * 管理多书源并发搜索，支持：
 * - 线程池并发搜索
 * - 搜索结果合并与去重
 * - 精确搜索模式
 * - 搜索暂停/恢复/取消
 * - 分页搜索
 *
 * 搜索结果按匹配度排序：
 * 1. 精确匹配（书名或作者完全等于搜索词）
 * 2. 标签匹配（分类包含搜索词）
 * 3. 包含匹配（书名或作者包含搜索词）
 * 4. 其他结果
 *
 * @property scope 协程作用域
 * @property callBack 搜索回调接口
 */
class SearchModel(private val scope: CoroutineScope, private val callBack: CallBack) {
    val threadCount = AppConfig.threadCount
    private var searchPool: ExecutorCoroutineDispatcher? = null
    private var mSearchId = 0L
    private var searchPage = 1
    private var searchKey: String = ""
    private var bookSourceParts = emptyList<BookSourcePart>()
    private var searchBooks = arrayListOf<SearchBook>()
    private var searchJob: Job? = null
    private var workingState = MutableStateFlow(true)


    /**
     * 初始化搜索线程池
     *
     * 创建固定大小的线程池，线程数取配置值和最大线程数的较小值。
     */
    private fun initSearchPool() {
        searchPool?.close()
        searchPool = Executors
            .newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD)).asCoroutineDispatcher()
    }

    /**
     * 执行搜索
     *
     * @param searchId 搜索ID，用于区分不同搜索请求
     * @param key 搜索关键词
     */
    fun search(searchId: Long, key: String) {
        if (searchId != mSearchId) {
            if (key.isEmpty()) {
                return
            }
            searchKey = key
            if (mSearchId != 0L) {
                close()
            }
            searchBooks.clear()
            bookSourceParts = callBack.getSearchScope().getBookSourceParts()
            if (bookSourceParts.isEmpty()) {
                callBack.onSearchCancel(NoStackTraceException("启用书源为空"))
                return
            }
            mSearchId = searchId
            searchPage = 1
            initSearchPool()
        } else {
            searchPage++
        }
        startSearch()
    }

    /**
     * 启动并发搜索
     *
     * 使用Flow并发执行多书源搜索，每个书源搜索超时30秒。
     * 搜索结果实时合并并回调通知UI更新。
     */
    private fun startSearch() {
        val precision = appCtx.getPrefBoolean(PreferKey.precisionSearch)
        var hasMore = false
        searchJob = scope.launch(searchPool!!) {
            flow {
                for (bs in bookSourceParts) {
                    bs.getBookSource()?.let {
                        emit(it)
                    }
                    workingState.first { it }
                }
            }.onStart {
                callBack.onSearchStart()
            }.mapParallelSafe(threadCount) {
                withTimeout(30000L) {
                    WebBook.searchBookAwait(
                        it, searchKey, searchPage,
                        filter = { name, author, kind ->
                            !precision || name.contains(searchKey) ||
                                    author.contains(searchKey) ||
                                    kind?.contains(searchKey) == true
                        })
                }
            }.onEach { items ->
                for (book in items) {
                    book.releaseHtmlData()
                }
                hasMore = hasMore || items.isNotEmpty()
                appDb.searchBookDao.insert(*items.toTypedArray())
                mergeItems(items, precision)
                currentCoroutineContext().ensureActive()
                callBack.onSearchSuccess(searchBooks)
            }.onCompletion {
                if (it == null) callBack.onSearchFinish(searchBooks.isEmpty(), hasMore)
            }.catch {
                AppLog.put("书源搜索出错\n${it.localizedMessage}", it)
            }.collect()
        }
    }

    /**
     * 合并搜索结果
     *
     * 将新搜索结果按匹配度分类并合并到已有结果中：
     * - 精确匹配：书名或作者完全等于搜索词
     * - 标签匹配：分类包含搜索词
     * - 包含匹配：书名或作者包含搜索词
     * - 其他结果：非精确搜索模式下保留
     *
     * 同名同作者的书籍会合并来源信息。
     *
     * @param newDataS 新搜索结果列表
     * @param precision 是否启用精确搜索模式
     */
    private suspend fun mergeItems(newDataS: List<SearchBook>, precision: Boolean) {
        if (newDataS.isNotEmpty()) {
            val copyData = ArrayList(searchBooks)
            val equalData = arrayListOf<SearchBook>()
            val containsData = arrayListOf<SearchBook>()
            val tagsData = arrayListOf<SearchBook>()
            val otherData = arrayListOf<SearchBook>()
            copyData.forEach {
                currentCoroutineContext().ensureActive()
                if (it.name == searchKey || it.author == searchKey) {
                    equalData.add(it)
                } else if (it.kind?.contains(searchKey) == true) {
                    tagsData.add(it)
                } else if (it.name.contains(searchKey) || it.author.contains(searchKey)) {
                    containsData.add(it)
                } else {
                    otherData.add(it)
                }
            }
            newDataS.forEach { nBook ->
                currentCoroutineContext().ensureActive()
                if (nBook.name == searchKey || nBook.author == searchKey) {
                    var hasSame = false
                    equalData.forEach { pBook ->
                        currentCoroutineContext().ensureActive()
                        if (pBook.name == nBook.name && pBook.author == nBook.author) {
                            pBook.addOrigin(nBook.origin)
                            hasSame = true
                        }
                    }
                    if (!hasSame) {
                        equalData.add(nBook)
                    }
                } else if (nBook.kind?.contains(searchKey) == true) {
                    var hasSame = false
                    tagsData.forEach { pBook ->
                        currentCoroutineContext().ensureActive()
                        if (pBook.name == nBook.name && pBook.author == nBook.author) {
                            pBook.addOrigin(nBook.origin)
                            hasSame = true
                        }
                    }
                    if (!hasSame) {
                        tagsData.add(nBook)
                    }
                } else if (nBook.name.contains(searchKey) || nBook.author.contains(searchKey)) {
                    var hasSame = false
                    containsData.forEach { pBook ->
                        currentCoroutineContext().ensureActive()
                        if (pBook.name == nBook.name && pBook.author == nBook.author) {
                            pBook.addOrigin(nBook.origin)
                            hasSame = true
                        }
                    }
                    if (!hasSame) {
                        containsData.add(nBook)
                    }
                } else if (!precision) {
                    var hasSame = false
                    otherData.forEach { pBook ->
                        currentCoroutineContext().ensureActive()
                        if (pBook.name == nBook.name && pBook.author == nBook.author) {
                            pBook.addOrigin(nBook.origin)
                            hasSame = true
                        }
                    }
                    if (!hasSame) {
                        otherData.add(nBook)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            equalData.sortByDescending { it.origins.size }
            equalData.addAll(tagsData.sortedByDescending { it.origins.size })
            equalData.addAll(containsData.sortedByDescending { it.origins.size })
            if (!precision) {
                equalData.addAll(otherData)
            }
            currentCoroutineContext().ensureActive()
            searchBooks = equalData
        }
    }

    /**
     * 暂停搜索
     */
    fun pause() {
        workingState.value = false
    }

    /**
     * 恢复搜索
     */
    fun resume() {
        workingState.value = true
    }

    /**
     * 取消搜索
     */
    fun cancelSearch() {
        close()
        callBack.onSearchCancel()
    }

    /**
     * 关闭搜索资源
     *
     * 取消搜索任务并关闭线程池。
     */
    fun close() {
        searchJob?.cancel()
        searchPool?.close()
        searchPool = null
        mSearchId = 0L
    }

    /**
     * 搜索回调接口
     */
    interface CallBack {
        /** 获取搜索范围（书源列表） */
        fun getSearchScope(): SearchScope
        /** 搜索开始回调 */
        fun onSearchStart()
        /** 搜索成功回调（实时更新） */
        fun onSearchSuccess(searchBooks: List<SearchBook>)
        /** 搜索完成回调 */
        fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean)
        /** 搜索取消回调 */
        fun onSearchCancel(exception: Throwable? = null)
    }

}