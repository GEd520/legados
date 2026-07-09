package io.legado.app.ui.book.read.config

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
object HighlightRulePreview {

    fun build(rule: HighlightRule): CharSequence {
        val text = rule.normalizedSampleText()
        val spannable = SpannableStringBuilder(text)
        val regex = kotlin.runCatching { Regex(rule.pattern) }.getOrNull() ?: return spannable
        regex.findAll(text).forEachIndexed { index, match ->
            val start = match.range.first
            val end = match.range.last + 1
            val textColor = rule.textColor ?: 0xFF111111.toInt()
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
                                SvgUnderlineSpan(textColor, accentColor, underlineWidth, svgPath),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        } else {
                            spannable.setSpan(
                                ForegroundColorSpan(textColor),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
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
                                spannable.setSpan(
                                    ForegroundColorSpan(textColor),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                        }
                    }
                }
                if (index == 0 && rule.underlineMode != 4 && rule.underlineMode != 5) {
                    val baseColor = rule.textColor ?: rule.underlineColor ?: 0xFF63C37D.toInt()
                    spannable.setSpan(
                        BackgroundColorSpan((0x33 shl 24) or (baseColor and 0x00FFFFFF)),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
        return spannable
    }
}
