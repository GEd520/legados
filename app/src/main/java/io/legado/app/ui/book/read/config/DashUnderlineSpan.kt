package io.legado.app.ui.book.read.config

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.text.style.ReplacementSpan
import io.legado.app.utils.dpToPx
import kotlin.math.roundToInt

/**
 * 虚线下划线 Span
 * @param textColor 文字颜色
 * @param underlineColor 下划线颜色
 * @param underlineWidth 下划线粗细(dp)
 * @param underlineOffset 下划线与文字的距离(dp)
 */
class DashUnderlineSpan(
    private val textColor: Int,
    private val underlineColor: Int,
    private val underlineWidth: Float = 1f,
    private val underlineOffset: Float = 6f,
) : ReplacementSpan() {

    private val preciseOffsetPx = underlineOffset.dpToPx()

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
            fm.descent = metrics.descent + preciseOffsetPx.roundToInt()
            fm.bottom = metrics.bottom + preciseOffsetPx.roundToInt()
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
        val lineY = y + preciseOffsetPx
        val dashPaint = Paint(paint).apply {
            color = underlineColor
            style = Paint.Style.STROKE
            strokeWidth = underlineWidth.dpToPx()
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            isAntiAlias = true
        }
        canvas.drawLine(x, lineY, x + width, lineY, dashPaint)
    }
}
