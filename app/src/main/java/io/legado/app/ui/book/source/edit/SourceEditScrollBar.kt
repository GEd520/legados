package io.legado.app.ui.book.source.edit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.ColorUtils

class SourceEditScrollBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val scrollBarWidth = dpToPx(6)
    private val scrollBarMinHeight = dpToPx(40)
    private val scrollBarMarginEnd = dpToPx(4)
    private val touchAreaPadding = dpToPx(12)

    @ColorInt
    private val scrollBarColor: Int = ColorUtils.adjustAlpha(context.accentColor, 0.5f)

    @ColorInt
    private val scrollBarColorPressed: Int = context.accentColor

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = scrollBarColor
        style = Paint.Style.FILL
    }

    private val scrollBarRect = RectF()
    private var recyclerView: RecyclerView? = null
    private var isDragging = false
    private var dragStartY = 0f
    private var scrollStartOffset = 0

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            invalidate()
        }
    }

    fun attachRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        recyclerView.addOnScrollListener(scrollListener)
        post { invalidate() }
    }

    fun detachRecyclerView() {
        recyclerView?.removeOnScrollListener(scrollListener)
        recyclerView = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val recyclerView = recyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val adapter = recyclerView.adapter ?: return

        val itemCount = adapter.itemCount
        if (itemCount == 0) return

        val verticalScrollRange = recyclerView.computeVerticalScrollRange()
        val verticalScrollExtent = recyclerView.computeVerticalScrollExtent()
        val verticalScrollOffset = recyclerView.computeVerticalScrollOffset()

        if (verticalScrollRange <= verticalScrollExtent) return

        val scrollBarHeight = (verticalScrollExtent.toFloat() / verticalScrollRange * height)
            .coerceAtLeast(scrollBarMinHeight.toFloat())

        val maxScroll = verticalScrollRange - verticalScrollExtent
        val scrollProportion = verticalScrollOffset.toFloat() / maxScroll

        val availableHeight = height - scrollBarHeight
        val scrollBarTop = scrollProportion * availableHeight
        val scrollBarBottom = scrollBarTop + scrollBarHeight

        val right = width - scrollBarMarginEnd
        val left = right - scrollBarWidth

        scrollBarRect.set(left, scrollBarTop, right, scrollBarBottom)

        paint.color = if (isDragging) scrollBarColorPressed else scrollBarColor
        canvas.drawRect(scrollBarRect, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val recyclerView = recyclerView ?: return false
        val touchX = event.x
        val touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val scrollBarLeft = scrollBarRect.left - touchAreaPadding
                val scrollBarRight = scrollBarRect.right + touchAreaPadding

                if (touchY < scrollBarRect.top || touchY > scrollBarRect.bottom ||
                    touchX < scrollBarLeft || touchX > scrollBarRight
                ) {
                    return false
                }

                isDragging = true
                dragStartY = touchY
                scrollStartOffset = recyclerView.computeVerticalScrollOffset()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false

                val deltaY = touchY - dragStartY
                val recyclerView_height = recyclerView.height
                val verticalScrollRange = recyclerView.computeVerticalScrollRange()
                val verticalScrollExtent = recyclerView.computeVerticalScrollExtent()
                val maxScroll = verticalScrollRange - verticalScrollExtent

                if (maxScroll <= 0) return true

                val scrollDelta = (deltaY / recyclerView_height * maxScroll).toInt()
                val targetScroll = (scrollStartOffset + scrollDelta).coerceIn(0, maxScroll)

                recyclerView.scrollBy(0, targetScroll - scrollStartOffset)
                scrollStartOffset = targetScroll
                dragStartY = touchY
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dpToPx(dp: Int): Float {
        return dp * resources.displayMetrics.density
    }
}
