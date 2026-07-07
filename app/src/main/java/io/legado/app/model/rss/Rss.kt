package io.legado.app.model.rss

import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.data.repository.debug.RssExecutionRecorder
import io.legado.app.data.repository.debug.FlowLogRecorder
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.StrResponse
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setToastRuleType
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.RuleData
import io.legado.app.model.debug.DebugCategory
import io.legado.app.model.debug.RssExecutionStep
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.isAbsUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

/**
 * RSS订阅源网络请求核心入口
 *
 * 提供订阅源相关的所有网络请求操作，包括：
 * - 获取文章列表 [getArticles]
 * - 获取文章正文 [getContent]
 *
 * 所有请求方法都支持：
 * - 登录检测 JS 脚本执行
 * - 重定向检测与日志记录
 * - 协程上下文切换
 *
 * @see RssParserByRule 规则解析器
 * @see RssParserDefault 默认XML解析器
 */
@Suppress("MemberVisibilityCanBePrivate")
object Rss {

    /**
     * 获取文章列表（异步回调方式）
     *
     * @param scope 协程作用域
     * @param sortName 分类名称
     * @param sortUrl 分类URL
     * @param rssSource 订阅源
     * @param page 页码
     * @param key 搜索关键词
     * @param context 协程上下文
     * @return Coroutine 包装的文章列表和下一页URL
     */
    fun getArticles(
        scope: CoroutineScope,
        sortName: String,
        sortUrl: String,
        rssSource: RssSource,
        page: Int,
        key: String? = null,
        context: CoroutineContext = Dispatchers.IO
    ): Coroutine<Pair<MutableList<RssArticle>, String?>> {
        return Coroutine.async(scope, context) {
            getArticlesAwait(sortName, sortUrl, rssSource, page, key)
        }
    }

    /**
     * 获取文章列表（挂起函数方式）
     *
     * 请求订阅源URL，解析返回的文章列表。
     * 支持登录检测JS脚本，用于验证源登录状态。
     *
     * @param sortName 分类名称
     * @param sortUrl 分类URL
     * @param rssSource 订阅源
     * @param page 页码
     * @param key 搜索关键词
     * @return 文章列表和下一页URL的Pair
     */
    suspend fun getArticlesAwait(
        sortName: String,
        sortUrl: String,
        rssSource: RssSource,
        page: Int,
        key: String? = null
    ): Pair<MutableList<RssArticle>, String?> {
        val recorder = RssExecutionRecorder

        // 开始执行会话
        recorder.startSession(rssSource.sourceUrl, rssSource.sourceName)

        // 配置检查阶段
        recorder.check(RssExecutionStep.SOURCE_NAME, rssSource.sourceName)
        recorder.checkWithValidation(
            RssExecutionStep.SOURCE_URL,
            rssSource.sourceUrl,
            if (rssSource.sourceUrl.isAbsUrl()) true to ""
            else false to "不是合法的 URL（缺少 http:// 或 https://）"
        )
        recorder.check(RssExecutionStep.SOURCE_ICON, rssSource.sourceIcon)
        recorder.check(RssExecutionStep.SOURCE_GROUP, rssSource.sourceGroup)
        recorder.check(RssExecutionStep.SORT_URL, rssSource.sortUrl)
        recorder.check(RssExecutionStep.RULE_ARTICLES, rssSource.ruleArticles)
        recorder.check(RssExecutionStep.RULE_NEXT_PAGE, rssSource.ruleNextPage)
        recorder.check(RssExecutionStep.RULE_TITLE, rssSource.ruleTitle)
        recorder.check(RssExecutionStep.RULE_PUB_DATE, rssSource.rulePubDate)
        recorder.check(RssExecutionStep.RULE_DESCRIPTION, rssSource.ruleDescription)
        recorder.check(RssExecutionStep.RULE_IMAGE, rssSource.ruleImage)
        recorder.check(RssExecutionStep.RULE_LINK, rssSource.ruleLink)
        recorder.check(RssExecutionStep.RULE_CONTENT, rssSource.ruleContent)
        recorder.check(RssExecutionStep.SHOULD_OVERRIDE_URL, rssSource.shouldOverrideUrlLoading)

        // 网络请求阶段
        val netStart = System.currentTimeMillis()
        val ruleData = RuleData()
        val analyzeUrl = AnalyzeUrl(
            sortUrl,
            page = page,
            key = key,
            baseUrl = rssSource.sourceUrl,
            source = rssSource,
            ruleData = ruleData,
            coroutineContext = currentCoroutineContext(),
            hasLoginHeader = false
        )
        val checkJs = rssSource.loginCheckJs
        val res = kotlin.runCatching {
            analyzeUrl.getStrResponseAwait().let {
                if (!checkJs.isNullOrBlank()) { //检测源是否已登录
                    analyzeUrl.evalJS(checkJs, it) as StrResponse
                } else {
                    it
                }
            }
        }.getOrElse { throwable ->
            if (!checkJs.isNullOrBlank()) {
                val errResponse = analyzeUrl.getErrStrResponse(throwable)
                try {
                    (analyzeUrl.evalJS(checkJs, errResponse) as StrResponse).also {
                        if (it.code() == 500) {
                            recorder.failed(RssExecutionStep.NETWORK_REQUEST, throwable.message ?: "网络请求失败",
                                System.currentTimeMillis() - netStart)
                            recorder.endSession()
                            throw throwable
                        }
                    }
                } catch (e: Throwable) {
                    recorder.failed(RssExecutionStep.NETWORK_REQUEST, e.message ?: "登录检测异常",
                        System.currentTimeMillis() - netStart)
                    recorder.endSession()
                    throw throwable
                }
            } else {
                recorder.failed(RssExecutionStep.NETWORK_REQUEST, throwable.message ?: "网络请求失败",
                    System.currentTimeMillis() - netStart)
                recorder.endSession()
                throw throwable
            }
        }
        recorder.success(RssExecutionStep.NETWORK_REQUEST,
            detail = analyzeUrl.ruleUrl, duration = System.currentTimeMillis() - netStart)
        val netDuration = System.currentTimeMillis() - netStart
        // 记录网络请求详情到 FlowLogRecorder，用于调试日志展示
        if (FlowLogRecorder.isEnabled) {
            FlowLogRecorder.logNetwork(
                source = rssSource,
                message = "获取文章列表成功",
                url = res.url,
                method = "GET",
                statusCode = res.code(),
                duration = netDuration,
                detail = "响应大小: ${res.body?.length ?: 0} 字节",
                requestHeaders = analyzeUrl.headerMap,
                cookies = analyzeUrl.headerMap["Cookie"]
            )
        }
        checkRedirect(rssSource, res)
        Debug.log(rssSource.sourceUrl, "≡获取成功:${analyzeUrl.ruleUrl}", category = DebugCategory.RSS)
        if (!res.body.isNullOrBlank()) {
            recorder.success(RssExecutionStep.RESPONSE_BODY, detail = "内容长度: ${res.body!!.length}")
        } else {
            recorder.failed(RssExecutionStep.RESPONSE_BODY, "响应内容为空")
        }
        // 结束执行会话
        recorder.endSession()
        return RssParserByRule.parseXML(sortName, sortUrl, res.url, res.body, rssSource, ruleData)
    }

    /**
     * 获取文章正文（异步回调方式）
     *
     * @param scope 协程作用域
     * @param rssArticle 文章对象
     * @param ruleContent 正文规则
     * @param rssSource 订阅源
     * @param context 协程上下文
     * @return Coroutine 包装的正文内容
     */
    fun getContent(
        scope: CoroutineScope,
        rssArticle: RssArticle,
        ruleContent: String,
        rssSource: RssSource,
        context: CoroutineContext = Dispatchers.IO
    ): Coroutine<String> {
        return Coroutine.async(scope, context) {
            getContentAwait(rssArticle, ruleContent, rssSource)
        }
    }

    /**
     * 获取文章正文（挂起函数方式）
     *
     * 请求文章链接，使用规则解析正文内容。
     *
     * @param rssArticle 文章对象
     * @param ruleContent 正文规则
     * @param rssSource 订阅源
     * @return 正文内容字符串
     */
    suspend fun getContentAwait(
        rssArticle: RssArticle,
        ruleContent: String,
        rssSource: RssSource,
    ): String {
        val netStart = System.currentTimeMillis()
        val analyzeUrl = AnalyzeUrl(
            rssArticle.link,
            baseUrl = rssArticle.origin,
            source = rssSource,
            ruleData = rssArticle,
            coroutineContext = currentCoroutineContext(),
            hasLoginHeader = false
        )
        val checkJs = rssSource.loginCheckJs
        val res = kotlin.runCatching {
            analyzeUrl.getStrResponseAwait().let {
                if (!checkJs.isNullOrBlank()) {
                    analyzeUrl.evalJS(checkJs, it) as StrResponse
                } else {
                    it
                }
            }
        }.getOrElse { throwable ->
            if (FlowLogRecorder.isEnabled) {
                val netDuration = System.currentTimeMillis() - netStart
                FlowLogRecorder.logNetwork(
                    source = rssSource,
                    message = "获取文章正文失败",
                    url = rssArticle.link,
                    method = "GET",
                    duration = netDuration,
                    error = throwable,
                    requestHeaders = analyzeUrl.headerMap,
                    cookies = analyzeUrl.headerMap["Cookie"]
                )
            }
            if (!checkJs.isNullOrBlank()) {
                val errResponse = analyzeUrl.getErrStrResponse(throwable)
                try {
                    (analyzeUrl.evalJS(checkJs, errResponse) as StrResponse).also {
                        if (it.code() == 500) {
                            throw throwable
                        }
                    }
                } catch (_: Throwable) {
                    throw throwable
                }
            } else {
                throw throwable
            }
        }
        val netDuration = System.currentTimeMillis() - netStart
        // 记录网络请求成功详情到 FlowLogRecorder
        if (FlowLogRecorder.isEnabled) {
            FlowLogRecorder.logNetwork(
                source = rssSource,
                message = "获取文章正文成功",
                url = res.url,
                method = "GET",
                statusCode = res.code(),
                duration = netDuration,
                detail = "响应大小: ${res.body?.length ?: 0} 字节",
                requestHeaders = analyzeUrl.headerMap,
                cookies = analyzeUrl.headerMap["Cookie"]
            )
        }
        checkRedirect(rssSource, res)
        Debug.log(rssSource.sourceUrl, "≡获取成功:${rssSource.sourceUrl}", category = DebugCategory.RSS)
        Debug.log(rssSource.sourceUrl, res.body ?: "", state = 20, category = DebugCategory.RSS)
        val analyzeRule = AnalyzeRule(rssArticle, rssSource)
        analyzeRule.setContent(res.body)
            .setBaseUrl(NetworkUtils.getAbsoluteURL(rssArticle.origin, rssArticle.link))
            .setCoroutineContext(currentCoroutineContext())
        analyzeRule.setRedirectUrl(res.url)
        analyzeRule.setToastRuleType("RSS_CONTENT")
        return analyzeRule.getString(ruleContent)
    }

    /**
     * 检测重定向
     *
     * 检查响应是否发生重定向，并记录调试日志。
     *
     * @param rssSource 订阅源（用于日志记录）
     * @param response 响应对象
     */
    private fun checkRedirect(rssSource: RssSource, response: StrResponse) {
        response.raw.priorResponse?.let {
            if (it.isRedirect) {
                Debug.log(rssSource.sourceUrl, "≡检测到重定向(${it.code})", category = DebugCategory.RSS)
                Debug.log(rssSource.sourceUrl, "┌重定向后地址", category = DebugCategory.RSS)
                Debug.log(rssSource.sourceUrl, "└${response.url}", category = DebugCategory.RSS)
            }
        }
    }
}
