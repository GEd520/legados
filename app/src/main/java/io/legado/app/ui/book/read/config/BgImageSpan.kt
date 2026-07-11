package io.legado.app.ui.book.read.config

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.style.ReplacementSpan
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.utils.dpToPx
import kotlin.math.roundToInt

/**
 * 背景图+下划线 Span，用于高亮规则匹配区域
 * @param textColor 文字颜色
 * @param bgImagePath 背景图路径
 * @param bgImageFit 背景图适配方式：0=平铺, 1=拉伸, 2=裁剪
 * @param bgImageScale 背景图缩放比例
 * @param backgroundColor 背景色，背景图为空或不可用时生效
 * @param underlineMode 下划线样式：0=无, 1=实线, 2=虚线, 3=波浪, 4=双线
 * @param underlineColor 下划线颜色
 * @param underlineWidth 下划线粗细(dp)
 * @param underlineSvgPath SVG路径（用于自定义下划线）
 * @param underlineOffset 下划线与文字的距离(dp)
 */
class BgImageSpan(
    private val textColor: Int,
    private val bgImagePath: String,
    private val bgImageFit: Int = 0,
    private val bgImageScale: Float = 1f,
    private val backgroundColor: Int? = null,
    private val underlineMode: Int = 0,
    private val underlineColor: Int = 0,
    private val underlineWidth: Float = 1f,
    private val underlineSvgPath: String = "",
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
            val extraSpace = if (underlineMode != 0) preciseOffsetPx.roundToInt() else 0
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
        val width = paint.measureText(text, start, end)
        val rectWidth = width
        val rectHeight = (bottom - top).toFloat()
        val scale = bgImageScale.coerceIn(0.1f, 5f)

        val bitmap = if (bgImagePath.isNotBlank()) TextLine.getBgBitmap(bgImagePath) else null
        if (bitmap != null) {
            val bgPaint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
                isFilterBitmap = true
            }
            when (bgImageFit) {
                1 -> {
                    val sw = rectWidth * scale
                    val sh = rectHeight * scale
                    val dx = x + (rectWidth - sw) / 2f
                    val dy = top + (rectHeight - sh) / 2f
                    canvas.save()
                    canvas.clipRect(x, top.toFloat(), x + width, bottom.toFloat())
                    canvas.drawBitmap(bitmap, null, RectF(dx, dy, dx + sw, dy + sh), bgPaint)
                    canvas.restore()
                }
                2 -> {
                    val bw = bitmap.width.toFloat()
                    val bh = bitmap.height.toFloat()
                    val fitScale = (rectWidth / bw).coerceAtLeast(rectHeight / bh) * scale
                    val scaledW = bw * fitScale
                    val scaledH = bh * fitScale
                    val dx = x + (rectWidth - scaledW) / 2f
                    val dy = top + (rectHeight - scaledH) / 2f
                    canvas.save()
                    canvas.clipRect(x, top.toFloat(), x + width, bottom.toFloat())
                    canvas.drawBitmap(bitmap, null, RectF(dx, dy, dx + scaledW, dy + scaledH), bgPaint)
                    canvas.restore()
                }
                else -> {
                    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                    val matrix = Matrix()
                    matrix.setScale(scale, scale)
                    matrix.postTranslate(x, top.toFloat())
                    shader.setLocalMatrix(matrix)
                    bgPaint.shader = shader
                    canvas.drawRect(x, top.toFloat(), x + width, bottom.toFloat(), bgPaint)
                }
            }
        } else if (backgroundColor != null) {
            val bgPaint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
                color = backgroundColor
            }
            canvas.drawRect(x, top.toFloat(), x + width, bottom.toFloat(), bgPaint)
        }

        paint.color = textColor
        paint.shader = null
        canvas.drawText(text, start, end, x, y.toFloat(), paint)

        if (underlineMode != 0) {
            drawUnderline(canvas, x, x + width, y + preciseOffsetPx, paint)
        }
    }

    private fun drawUnderline(canvas: Canvas, startX: Float, endX: Float, lineY: Float, paint: Paint) {
        val ulPaint = Paint(paint).apply {
            color = underlineColor
            style = Paint.Style.STROKE
            strokeWidth = underlineWidth.dpToPx()
            isAntiAlias = true
        }
        when (underlineMode) {
            1 -> canvas.drawLine(startX, lineY, endX, lineY, ulPaint)
            2 -> {
                ulPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                canvas.drawLine(startX, lineY, endX, lineY, ulPaint)
            }
            3 -> {
                val path = android.graphics.Path()
                val waveAmplitude = 3.dpToPx().toFloat()
                val waveLength = 12.dpToPx().toFloat()
                path.moveTo(startX, lineY)
                var currentX = startX
                val endY = lineY
                while (currentX < endX) {
                    val nextX = (currentX + waveLength).coerceAtMost(endX)
                    val midX = (currentX + nextX) / 2
                    path.quadTo(midX, endY - waveAmplitude, nextX, endY)
                    currentX = nextX
                    if (currentX < endX) {
                        val nextX2 = (currentX + waveLength).coerceAtMost(endX)
                        val midX2 = (currentX + nextX2) / 2
                        path.quadTo(midX2, endY + waveAmplitude, nextX2, endY)
                        currentX = nextX2
                    }
                }
                canvas.drawPath(path, ulPaint)
            }
            4 -> {
                val lineGap = 3.dpToPx()
                val line2Y = lineY + lineGap + underlineWidth.dpToPx()
                canvas.drawLine(startX, lineY, endX, lineY, ulPaint)
                canvas.drawLine(startX, line2Y, endX, line2Y, ulPaint)
            }
        }
    }
}
