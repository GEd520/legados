package io.legado.app.ui.book.read.config

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class HighlightRule(
    @SerializedName(value = "id", alternate = ["a"])
    var id: String = System.currentTimeMillis().toString(),
    @SerializedName(value = "name", alternate = ["b"])
    var name: String = "",
    @SerializedName(value = "pattern", alternate = ["c"])
    var pattern: String = "",
    @SerializedName(value = "isRegex", alternate = ["d"])
    var isRegex: Boolean? = null,
    @SerializedName(value = "sampleText", alternate = ["e"])
    var sampleText: String = "",
    @SerializedName(value = "group", alternate = ["f"])
    var group: String = HighlightRuleGroupStore.DEFAULT_GROUP,
    @SerializedName(value = "targetScope", alternate = ["g"])
    var targetScope: Int = TARGET_ALL,
    @SerializedName(value = "enabled", alternate = ["h"])
    var enabled: Boolean = true,
    @SerializedName(value = "textColor", alternate = ["i"])
    var textColor: Int? = null,
    @SerializedName(value = "underlineMode", alternate = ["j"])
    var underlineMode: Int = 0,
    @SerializedName(value = "underlineColor", alternate = ["k"])
    var underlineColor: Int? = null,
    @SerializedName(value = "underlineWidth", alternate = ["l"])
    var underlineWidth: Float = 1f,
    @SerializedName(value = "underlineOffset", alternate = ["m"])
    var underlineOffset: Float = 2f,
    @SerializedName(value = "underlineSvgPath", alternate = ["n"])
    var underlineSvgPath: String? = null,
    @SerializedName(value = "backgroundColor", alternate = ["o"])
    var backgroundColor: Int? = null,
    @SerializedName(value = "bgImage", alternate = ["p"])
    var bgImage: String? = null,
    @SerializedName(value = "bgImageFit", alternate = ["q"])
    var bgImageFit: Int = 0,
    @SerializedName(value = "bgImageScale", alternate = ["r"])
    var bgImageScale: Float = 1f,
) {

    fun compilePattern(): Regex {
        return Regex(if (isRegex == false) Regex.escape(pattern) else pattern)
    }

    fun styleSummary(): String {
        val parts = ArrayList<String>(4)
        parts.add(targetScopeLabel())
        textColor?.let {
            parts.add("字色 ${it.toHexColor()}")
        }
        if (underlineMode != 0) {
            parts.add(
                when (underlineMode) {
                    1 -> "实线下划线"
                    2 -> "虚线下划线"
                    3 -> "波浪下划线"
                    4 -> "双下划线"
                    5 -> "自定义SVG"
                    else -> "下划线"
                } + underlineColor?.let { " ${it.toHexColor()}" }.orEmpty()
            )
        }
        if (!bgImage.isNullOrBlank()) {
            parts.add(
                when (bgImageFit) {
                    1 -> "背景图(拉伸)"
                    2 -> "背景图(裁剪)"
                    else -> "背景图(平铺)"
                }
            )
        }
        if (parts.isEmpty()) {
            parts.add("无样式")
        }
        return parts.joinToString(" / ")
    }

    fun targetScopeLabel(): String {
        return when (targetScope) {
            TARGET_TITLE -> "作用于标题"
            TARGET_BODY -> "作用于正文"
            else -> "作用于全部"
        }
    }

    fun displayPattern(): String {
        return pattern.ifBlank { ".*" }
    }

    fun normalizedSampleText(): String {
        return sampleText.ifBlank {
            "她轻声说：“今晚就出发。”\n最近在重读《百年孤独》（纪念版），节奏依然很稳。"
        }
    }

    fun copyWithNewId(): HighlightRule {
        return copy(id = "${System.currentTimeMillis()}_${name.hashCode()}")
    }

    companion object {
        const val TARGET_ALL = 0
        const val TARGET_TITLE = 1
        const val TARGET_BODY = 2

        fun Int.toHexColor(): String = String.format("#%08X", this)
    }
}
