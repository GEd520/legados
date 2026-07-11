package io.legado.app.ui.book.read.config

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import io.legado.app.utils.dpToPx
import kotlin.math.roundToInt

/**
 * 双线下划线 Span
 * @param textColor 文字颜色
 * @param underlineColor 下划线颜色
 * @param underlineWidth 下划线粗细(dp)
 * @param underlineOffset 下划线与文字的距离(dp)
 */
class DoubleUnderlineSpan(
    private val textColor: Int,
    private val underlineColor: Int,
    private val underlineWidth: Float = 1f,
    private val underlineOffset: Float = 6f,
) : ReplacementSpan() {

    private val preciseOffsetPx = underlineOffset.dpToPx()
    private val preciseWidthPx = underlineWidth.dpToPx()

    private val lineGap = 3.dpToPx()  // 双线间距

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            fm.top = metrics.top
            fm.ascent = metrics.ascent
            val extraSpace = (preciseOffsetPx + lineGap + preciseWidthPx).roundToInt()
            fm.descent = metrics.descent + extraSpace
            fm.bottom = metrics.bottom + extraSpace
        }
        return paint.measureText(text, start, end).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val textStr = text.subSequence(start, end).toString()
        paint.color = textColor
        canvas.drawText(textStr, x, y.toFloat(), paint)

        val width = paint.measureText(text, start, end)
        val line1Y = y + preciseOffsetPx
        val line2Y = line1Y + lineGap + preciseWidthPx
        val linePaint = Paint(paint).apply {
            color = underlineColor
            style = Paint.Style.STROKE
            strokeWidth = underlineWidth.dpToPx()
            isAntiAlias = true
        }
        canvas.drawLine(x, line1Y, x + width, line1Y, linePaint)
        canvas.drawLine(x, line2Y, x + width, line2Y, linePaint)
    }
}
