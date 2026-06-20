package io.legado.app.model

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Size
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.utils.SvgUtils
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

object ParagraphBubbleRenderer {

    const val SCHEME_PREFIX = "bubble://paragraph"

    fun isBubbleSrc(src: String): Boolean {
        return src.startsWith(SCHEME_PREFIX)
    }

    fun getSize(src: String): Size {
        val scale = BubblePackageManager.currentEntry().config.sizeScale
            .coerceIn(BubblePackageManager.MIN_SIZE_SCALE, BubblePackageManager.MAX_SIZE_SCALE)
        val side = (64f * scale).roundToInt().coerceAtLeast(1)
        return Size(side, side)
    }

    fun inlineWidth(baseWidth: Float): Float {
        val scale = BubblePackageManager.currentEntry().config.sizeScale
            .coerceIn(BubblePackageManager.MIN_SIZE_SCALE, BubblePackageManager.MAX_SIZE_SCALE)
        return baseWidth * scale
    }

    fun cacheKey(src: String, width: Int, height: Int?): String {
        val config = BubblePackageManager.currentEntry().config
        val color = resolveColor(config, status(src))
        return buildString {
            append(src)
            append("#")
            append(width)
            append("x")
            append(height ?: 0)
            append("#")
            append(BubblePackageManager.activeDirName())
            append("#")
            append(config.updatedAt)
            append("#")
            append(config.sizeScale)
            append("#")
            append(if (AppConfig.isNightTheme) "night" else "day")
            append("#")
            append(color)
        }
    }

    fun render(src: String, width: Int, height: Int?): Bitmap? {
        val config = BubblePackageManager.currentEntry().config
        val color = resolveColor(config, status(src))
        val number = number(src)
        val svg = config.svgTemplate
            .replace("\${color}", color)
            .replace("\${num}", number)
        return SvgUtils.createBitmap(ByteArrayInputStream(svg.toByteArray()), width.coerceAtLeast(1), height)
    }

    private fun resolveColor(config: BubblePackageManager.Config, status: String): String {
        val emphasis = status.equals("emphasis", true)
        val fallback = if (emphasis) {
            BubblePackageManager.DEFAULT_EMPHASIS_COLOR
        } else {
            BubblePackageManager.DEFAULT_NORMAL_COLOR
        }
        val value = if (AppConfig.isNightTheme) {
            if (emphasis) config.nightEmphasisColor else config.nightNormalColor
        } else {
            if (emphasis) config.dayEmphasisColor else config.dayNormalColor
        }?.takeIf { it.isNotBlank() } ?: fallback
        return runCatching {
            val normalized = if (value.startsWith("#")) value else "#$value"
            Color.parseColor(normalized)
            normalized
        }.getOrDefault(fallback)
    }

    private fun number(src: String): String {
        return queryValue(src, "num").ifBlank { "0" }
    }

    private fun status(src: String): String {
        return queryValue(src, "status").ifBlank { "normal" }
    }

    private fun queryValue(src: String, key: String): String {
        val query = src.substringAfter('?', "")
        if (query.isBlank()) return ""
        return query.split('&')
            .firstOrNull { it.substringBefore('=') == key }
            ?.substringAfter('=', "")
            ?.let(Uri::decode)
            .orEmpty()
    }
}
