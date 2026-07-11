package io.legado.app.ui.book.read.config

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.style.ReplacementSpan
import io.legado.app.utils.dpToPx
import kotlin.math.roundToInt

/**
 * 波浪线下划线 Span
 * @param textColor 文字颜色
 * @param underlineColor 下划线颜色
 * @param underlineWidth 下划线粗细(dp)
 * @param underlineOffset 下划线与文字的距离(dp)
 */
class WaveUnderlineSpan(
    private val textColor: Int,
    private val underlineColor: Int,
    private val underlineWidth: Float = 1f,
    private val underlineOffset: Float = 6f,
) : ReplacementSpan() {

    private val preciseOffsetPx = underlineOffset.dpToPx()

    private val waveAmplitude = 3.dpToPx().toFloat()  // 波浪振幅

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
            val preciseExtraSpace = (preciseOffsetPx + waveAmplitude).roundToInt()
            fm.descent = metrics.descent + preciseExtraSpace
            fm.bottom = metrics.bottom + preciseExtraSpace
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
        val waveLength = 12.dpToPx().toFloat()
        val wavePaint = Paint(paint).apply {
            color = underlineColor
            style = Paint.Style.STROKE
            strokeWidth = underlineWidth.dpToPx()
            isAntiAlias = true
        }
        val path = Path().apply { moveTo(x, lineY) }
        var currentX = x
        val endX = x + width
        while (currentX < endX) {
            val nextX = (currentX + waveLength).coerceAtMost(endX)
            val midX = (currentX + nextX) / 2
            path.quadTo(midX, lineY - waveAmplitude, nextX, lineY)
            currentX = nextX
            if (currentX < endX) {
                val nextX2 = (currentX + waveLength).coerceAtMost(endX)
                val midX2 = (currentX + nextX2) / 2
                path.quadTo(midX2, lineY + waveAmplitude, nextX2, lineY)
                currentX = nextX2
            }
        }
        canvas.drawPath(path, wavePaint)
    }
}
