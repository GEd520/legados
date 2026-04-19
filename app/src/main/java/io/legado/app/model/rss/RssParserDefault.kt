package io.legado.app.model.rss

import io.legado.app.data.entities.RssArticle
import io.legado.app.model.Debug
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

/**
 * RSS默认XML解析器
 *
 * 当订阅源没有定义列表规则时，使用标准XML Pull Parser解析RSS/Atom格式。
 * 支持解析以下字段：
 * - title: 文章标题
 * - link: 文章链接
 * - description: 文章描述
 * - pubDate: 发布时间
 * - media:thumbnail / enclosure: 图片
 * - content:encoded: 文章内容
 *
 * 自动从描述或内容中提取img标签的src作为封面图片。
 *
 * @see RssParserByRule 规则解析器
 */
@Suppress("unused")
object RssParserDefault {

    /**
     * 解析RSS XML内容
     *
     * @param sortName 分类名称
     * @param xml XML内容
     * @param sourceUrl 源URL
     * @return 文章列表和下一页URL的Pair（默认解析器不返回下一页URL）
     * @throws XmlPullParserException XML解析异常
     * @throws IOException IO异常
     */
    @Throws(XmlPullParserException::class, IOException::class)
    fun parseXML(
        sortName: String,
        xml: String,
        sourceUrl: String
    ): Pair<MutableList<RssArticle>, String?> {

        val articleList = mutableListOf<RssArticle>()
        var currentArticle = RssArticle()

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false

        val xmlPullParser = factory.newPullParser()
        xmlPullParser.setInput(StringReader(xml))

        // A flag just to be sure of the correct parsing
        var insideItem = false

        var eventType = xmlPullParser.eventType

        // Start parsing the xml
        loop@ while (eventType != XmlPullParser.END_DOCUMENT) {

            // Start parsing the item
            if (eventType == XmlPullParser.START_TAG) {
                when {
                    xmlPullParser.name.equals(RSS_ITEM, true) ->
                        insideItem = true
                    xmlPullParser.name.equals(RSS_ITEM_TITLE, true) ->
                        if (insideItem) currentArticle.title = xmlPullParser.nextText().trim()
                    xmlPullParser.name.equals(RSS_ITEM_LINK, true) ->
                        if (insideItem) currentArticle.link = xmlPullParser.nextText().trim()
                    xmlPullParser.name.equals(RSS_ITEM_THUMBNAIL, true) ->
                        if (insideItem) currentArticle.image =
                            xmlPullParser.getAttributeValue(null, RSS_ITEM_URL)
                    xmlPullParser.name.equals(RSS_ITEM_ENCLOSURE, true) ->
                        if (insideItem) {
                            val type =
                                xmlPullParser.getAttributeValue(null, RSS_ITEM_TYPE)
                            if (type != null && type.contains("image/")) {
                                currentArticle.image =
                                    xmlPullParser.getAttributeValue(null, RSS_ITEM_URL)
                            }
                        }
                    xmlPullParser.name
                        .equals(RSS_ITEM_DESCRIPTION, true) ->
                        if (insideItem) {
                            val description = xmlPullParser.nextText()
                            currentArticle.description = description.trim()
                            if (currentArticle.image == null) {
                                currentArticle.image = getImageUrl(description)
                            }
                        }
                    xmlPullParser.name.equals(RSS_ITEM_CONTENT, true) ->
                        if (insideItem) {
                            val content = xmlPullParser.nextText().trim()
                            currentArticle.content = content
                            if (currentArticle.image == null) {
                                currentArticle.image = getImageUrl(content)
                            }
                        }
                    xmlPullParser.name
                        .equals(RSS_ITEM_PUB_DATE, true) ->
                        if (insideItem) {
                            val nextTokenType = xmlPullParser.next()
                            if (nextTokenType == XmlPullParser.TEXT) {
                                currentArticle.pubDate = xmlPullParser.text.trim()
                            }
                            // Skip to be able to find date inside 'tag' tag
                            continue@loop
                        }
                    xmlPullParser.name.equals(RSS_ITEM_TIME, true) ->
                        if (insideItem) currentArticle.pubDate = xmlPullParser.nextText()
                }
            } else if (eventType == XmlPullParser.END_TAG
                && xmlPullParser.name.equals("item", true)
            ) {
                // The item is correctly parsed
                insideItem = false
                currentArticle.origin = sourceUrl
                currentArticle.sort = sortName
                articleList.add(currentArticle)
                currentArticle = RssArticle()
            }
            eventType = xmlPullParser.next()
        }
        articleList.firstOrNull()?.let {
            Debug.log(sourceUrl, "┌获取标题")
            Debug.log(sourceUrl, "└${it.title}")
            Debug.log(sourceUrl, "┌获取时间")
            Debug.log(sourceUrl, "└${it.pubDate}")
            Debug.log(sourceUrl, "┌获取描述")
            Debug.log(sourceUrl, "└${it.description}")
            Debug.log(sourceUrl, "┌获取图片url")
            Debug.log(sourceUrl, "└${it.image}")
            Debug.log(sourceUrl, "┌获取文章链接")
            Debug.log(sourceUrl, "└${it.link}")
        }
        return Pair(articleList, null)
    }

    /**
     * 从HTML内容中提取图片URL
     *
     * 查找第一个img标签并提取其src属性值作为封面图片。
     *
     * @param input HTML内容
     * @return 图片URL，如果未找到则返回null
     */
    private fun getImageUrl(input: String): String? {

        var url: String? = null
        val patternImg = "(<img [^>]*>)".toPattern()
        val matcherImg = patternImg.matcher(input)
        if (matcherImg.find()) {
            val imgTag = matcherImg.group(1)
            val patternLink = "src\\s*=\\s*\"([^\"]+)\"".toPattern()
            val matcherLink = patternLink.matcher(imgTag!!)
            if (matcherLink.find()) {
                url = matcherLink.group(1)!!.trim()
            }
        }
        return url
    }

    private const val RSS_ITEM = "item"
    private const val RSS_ITEM_TITLE = "title"
    private const val RSS_ITEM_LINK = "link"
    private const val RSS_ITEM_CATEGORY = "category"
    private const val RSS_ITEM_THUMBNAIL = "media:thumbnail"
    private const val RSS_ITEM_ENCLOSURE = "enclosure"
    private const val RSS_ITEM_DESCRIPTION = "description"
    private const val RSS_ITEM_CONTENT = "content:encoded"
    private const val RSS_ITEM_PUB_DATE = "pubDate"
    private const val RSS_ITEM_TIME = "time"
    private const val RSS_ITEM_URL = "url"
    private const val RSS_ITEM_TYPE = "type"
}