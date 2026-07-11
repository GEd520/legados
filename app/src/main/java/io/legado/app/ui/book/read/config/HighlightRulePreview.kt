package io.legado.app.ui.book.read.config

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
object HighlightRulePreview {

    fun build(rule: HighlightRule, defaultTextColor: Int = 0xFF111111.toInt()): CharSequence {
        val text = rule.normalizedSampleText()
        val spannable = SpannableStringBuilder(text)
        val regex = kotlin.runCatching { rule.compilePattern() }.getOrNull() ?: return spannable
        regex.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (start >= end) return@forEach
            val textColor = rule.textColor ?: defaultTextColor
            val accentColor = rule.underlineColor ?: rule.textColor ?: 0xFF63C37D.toInt()
            val underlineWidth = rule.underlineWidth
            val underlineOffset = rule.underlineOffset
            val hasBgImage = !rule.bgImage.isNullOrBlank()
            val backgroundColor = if (hasBgImage) null else rule.backgroundColor

            if (hasBgImage || backgroundColor != null) {
                spannable.setSpan(
                    BgImageSpan(
                        textColor,
                        rule.bgImage.orEmpty(),
                        rule.bgImageFit,
                        rule.bgImageScale,
                        backgroundColor,
                        rule.underlineMode,
                        accentColor,
                        underlineWidth,
                        rule.underlineSvgPath.orEmpty(),
                        underlineOffset
                    ),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                when (rule.underlineMode) {
                    4 -> {
                        spannable.setSpan(
                            DoubleUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    5 -> {
                        val svgPath = rule.underlineSvgPath
                        if (!svgPath.isNullOrBlank()) {
                            spannable.setSpan(
                                SvgUnderlineSpan(textColor, accentColor, underlineWidth, svgPath, underlineOffset),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        } else {
                            rule.textColor?.let {
                                spannable.setSpan(
                                    ForegroundColorSpan(it),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                        }
                    }
                    else -> {
                        when (rule.underlineMode) {
                            1 -> {
                                spannable.setSpan(
                                    SolidUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                            2 -> {
                                spannable.setSpan(
                                    DashUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                            3 -> {
                                spannable.setSpan(
                                    WaveUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                            else -> {
                                rule.textColor?.let {
                                    spannable.setSpan(
                                        ForegroundColorSpan(it),
                                        start,
                                        end,
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return spannable
    }
}
