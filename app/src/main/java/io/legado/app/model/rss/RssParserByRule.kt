package io.legado.app.model.rss

import androidx.annotation.Keep
import io.legado.app.R
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setRuleData
import io.legado.app.model.analyzeRule.RuleData
import io.legado.app.utils.NetworkUtils
import kotlinx.coroutines.currentCoroutineContext
import splitties.init.appCtx
import java.util.Locale

/**
 * RSS规则解析器
 *
 * 根据订阅源定义的规则解析文章列表，支持：
 * - 列表规则解析（HTML/XML/JSON）
 * - 标题、时间、描述、图片、链接规则
 * - 下一页规则
 * - 列表反转
 *
 * 当列表规则为空时，回退到 [RssParserDefault] 使用标准XML解析。
 *
 * @see Rss.getArticles 网络请求入口
 * @see RssParserDefault 默认XML解析器
 */
@Keep
object RssParserByRule {

    /**
     * 解析RSS内容
     *
     * @param sortName 分类名称
     * @param sortUrl 分类URL
     * @param redirectUrl 重定向后的URL
     * @param body 页面内容
     * @param rssSource 订阅源
     * @param ruleData 规则数据对象
     * @return 文章列表和下一页URL的Pair
     * @throws NoStackTraceException 当内容为空时抛出
     */
    @Throws(Exception::class)
    suspend fun parseXML(
        sortName: String,
        sortUrl: String,
        redirectUrl: String,
        body: String?,
        rssSource: RssSource,
        ruleData: RuleData
    ): Pair<MutableList<RssArticle>, String?> {
        val sourceUrl = rssSource.sourceUrl
        var nextUrl: String? = null
        if (body.isNullOrBlank()) {
            throw NoStackTraceException(
                appCtx.getString(R.string.error_get_web_content, rssSource.sourceUrl)
            )
        }
        Debug.log(sourceUrl, body, state = 10)
        var ruleArticles = rssSource.ruleArticles
        if (ruleArticles.isNullOrBlank()) {
            Debug.log(sourceUrl, "⇒列表规则为空, 使用默认规则解析")
            return RssParserDefault.parseXML(sortName, body, sourceUrl)
        } else {
            val articleList = mutableListOf<RssArticle>()
            val analyzeRule = AnalyzeRule(ruleData, rssSource)
            analyzeRule.setCoroutineContext(currentCoroutineContext())
            analyzeRule.setContent(body).setBaseUrl(sortUrl)
            analyzeRule.setRedirectUrl(redirectUrl)
            var reverse = false
            if (ruleArticles.startsWith("-")) {
                reverse = true
                ruleArticles = ruleArticles.substring(1)
            }
            Debug.log(sourceUrl, "┌获取列表")
            val collections = analyzeRule.getElements(ruleArticles)
            Debug.log(sourceUrl, "└列表大小:${collections.size}")
            if (!rssSource.ruleNextPage.isNullOrEmpty()) {
                Debug.log(sourceUrl, "┌获取下一页链接")
                if (rssSource.ruleNextPage!!.uppercase(Locale.getDefault()) == "PAGE") {
                    nextUrl = sortUrl
                } else {
                    nextUrl = analyzeRule.getString(rssSource.ruleNextPage)
                    if (nextUrl.isNotEmpty()) {
                        nextUrl = NetworkUtils.getAbsoluteURL(sortUrl, nextUrl)
                    }
                }
                Debug.log(sourceUrl, "└$nextUrl")
            }
            val ruleTitle = analyzeRule.splitSourceRule(rssSource.ruleTitle)
            val rulePubDate = analyzeRule.splitSourceRule(rssSource.rulePubDate)
            val ruleDescription = analyzeRule.splitSourceRule(rssSource.ruleDescription)
            val ruleImage = analyzeRule.splitSourceRule(rssSource.ruleImage)
            val ruleLink = analyzeRule.splitSourceRule(rssSource.ruleLink)
            val variable = ruleData.getVariable()
            for ((index, item) in collections.withIndex()) {
                getItem(
                    sourceUrl, item, analyzeRule, variable,rssSource.type, index == 0,
                    ruleTitle, rulePubDate, ruleDescription, ruleImage, ruleLink
                )?.let {
                    it.sort = sortName
                    it.origin = sourceUrl
                    articleList.add(it)
                }
            }
            if (reverse) {
                articleList.reverse()
            }
            return Pair(articleList, nextUrl)
        }
    }

    /**
     * 解析单个文章项
     *
     * 从列表元素中解析单个文章信息，包括：
     * - 标题、发布时间
     * - 描述、图片链接
     * - 文章链接
     *
     * @param sourceUrl 源URL
     * @param item 列表元素
     * @param analyzeRule 规则解析器
     * @param variable 变量值
     * @param type 文章类型
     * @param log 是否输出调试日志
     * @param ruleTitle 标题规则
     * @param rulePubDate 发布时间规则
     * @param ruleDescription 描述规则
     * @param ruleImage 图片规则
     * @param ruleLink 链接规则
     * @return 文章对象，如果标题为空则返回null
     */
    private fun getItem(
        sourceUrl: String,
        item: Any,
        analyzeRule: AnalyzeRule,
        variable: String?,
        type: Int,
        log: Boolean,
        ruleTitle: List<AnalyzeRule.SourceRule>,
        rulePubDate: List<AnalyzeRule.SourceRule>,
        ruleDescription: List<AnalyzeRule.SourceRule>,
        ruleImage: List<AnalyzeRule.SourceRule>,
        ruleLink: List<AnalyzeRule.SourceRule>
    ): RssArticle? {
        val rssArticle = RssArticle(variable = variable)
        analyzeRule.setRuleData(rssArticle)
        analyzeRule.setContent(item)
        Debug.log(sourceUrl, "┌获取标题", log)
        rssArticle.title = analyzeRule.getString(ruleTitle)
        Debug.log(sourceUrl, "└${rssArticle.title}", log)
        Debug.log(sourceUrl, "┌获取时间", log)
        rssArticle.pubDate = analyzeRule.getString(rulePubDate)
        Debug.log(sourceUrl, "└${rssArticle.pubDate}", log)
        Debug.log(sourceUrl, "┌获取描述", log)
        if (ruleDescription.isEmpty()) {
            rssArticle.description = null
            Debug.log(sourceUrl, "└描述规则为空，将会解析内容页", log)
        } else {
            rssArticle.description = analyzeRule.getString(ruleDescription)
            Debug.log(sourceUrl, "└${rssArticle.description}", log)
        }
        Debug.log(sourceUrl, "┌获取图片url", log)
        try {
            analyzeRule.getString(ruleImage).let {
                if (it.isNotEmpty()) {
                    rssArticle.image = NetworkUtils.getAbsoluteURL(sourceUrl, it)
                }
            }
            Debug.log(sourceUrl, "└${rssArticle.image ?: ""}", log)
        } catch (e: Exception) {
            Debug.log(sourceUrl, "└${e.localizedMessage}", log)
        }
        Debug.log(sourceUrl, "┌获取文章链接", log)
        rssArticle.link = analyzeRule.getString(ruleLink, isUrl = true)
        Debug.log(sourceUrl, "└${rssArticle.link}", log)
        rssArticle.type = type
        if (rssArticle.title.isBlank()) {
            return null
        }
        return rssArticle
    }
}