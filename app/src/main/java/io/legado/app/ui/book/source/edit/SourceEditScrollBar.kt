package io.legado.app.ui.book.source.edit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
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

    private val scrollBarWidth = dpToPx(8)
    private val scrollBarMinHeight = dpToPx(40)
    private val scrollBarMarginEnd = dpToPx(8)
    private val touchAreaWidth = dpToPx(32)

    @ColorInt
    private val scrollBarColor: Int = ColorUtils.adjustAlpha(context.accentColor, 0.6f)

    @ColorInt
    private val scrollBarColorPressed: Int = context.accentColor

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = scrollBarColor
        style = Paint.Style.FILL
    }

    private val scrollBarRect = RectF()
    private var recyclerView: RecyclerView? = null
    private var isDragging = false
    private var isScrollBarVisible = false
    private var fadeAnimator: ValueAnimator? = null
    private var currentAlpha = 0f

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            updateScrollBarPosition()
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING,
                RecyclerView.SCROLL_STATE_SETTLING -> {
                    if (!isDragging) {
                        showScrollBar()
                    }
                }
                RecyclerView.SCROLL_STATE_IDLE -> {
                    if (!isDragging) {
                        hideScrollBar()
                    }
                }
            }
        }
    }

    fun attachRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        recyclerView.addOnScrollListener(scrollListener)
        post { updateScrollBarPosition() }
    }

    fun detachRecyclerView() {
        fadeAnimator?.cancel()
        recyclerView?.removeOnScrollListener(scrollListener)
        recyclerView = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isScrollBarVisible) return

        paint.alpha = (currentAlpha * 255).toInt()
        paint.color = if (isDragging) scrollBarColorPressed else scrollBarColor

        val right = width.toFloat() - scrollBarMarginEnd
        val left = right - scrollBarWidth

        scrollBarRect.set(
            left,
            scrollBarRect.top,
            right,
            scrollBarRect.bottom
        )

        canvas.drawRect(scrollBarRect, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val touchX = event.x
        val touchY = event.y

        val scrollBarLeft = width - scrollBarMarginEnd - scrollBarWidth - touchAreaWidth / 2
        val scrollBarRight = width - scrollBarMarginEnd + touchAreaWidth / 2

        if (touchX < scrollBarLeft || touchX > scrollBarRight) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isScrollBarVisible) return false
                isDragging = true
                fadeAnimator?.cancel()
                parent?.requestDisallowInterceptTouchEvent(true)
                scrollToPosition(touchY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    scrollToPosition(touchY)
                    return true
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    hideScrollBar()
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scrollToPosition(y: Float) {
        val recyclerView = recyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val adapter = recyclerView.adapter ?: return

        val itemCount = adapter.itemCount
        if (itemCount == 0) return

        val proportion = (y / height).coerceIn(0f, 1f)
        val targetPos = (proportion * (itemCount - 1)).toInt()

        layoutManager.scrollToPositionWithOffset(targetPos, 0)
    }

    private fun updateScrollBarPosition() {
        val recyclerView = recyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val adapter = recyclerView.adapter ?: return

        val itemCount = adapter.itemCount
        if (itemCount == 0) {
            visibility = GONE
            return
        }

        val verticalScrollRange = recyclerView.computeVerticalScrollRange()
        val verticalScrollExtent = recyclerView.computeVerticalScrollExtent()

        if (verticalScrollRange <= verticalScrollExtent) {
            visibility = GONE
            isScrollBarVisible = false
            return
        }

        visibility = VISIBLE
        isScrollBarVisible = true
        if (currentAlpha == 0f) {
            currentAlpha = 1f
        }

        val scrollBarHeight = scrollBarMinHeight.toFloat()

        val verticalScrollOffset = recyclerView.computeVerticalScrollOffset()
        val maxScroll = verticalScrollRange - verticalScrollExtent
        val scrollProportion = if (maxScroll > 0) verticalScrollOffset.toFloat() / maxScroll else 0f

        val scrollBarTop = scrollProportion * (height - scrollBarHeight)
        val scrollBarBottom = scrollBarTop + scrollBarHeight

        scrollBarRect.top = scrollBarTop
        scrollBarRect.bottom = scrollBarBottom

        invalidate()
    }

    private fun showScrollBar() {
        if (visibility != VISIBLE) {
            visibility = VISIBLE
        }
        isScrollBarVisible = true
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(currentAlpha, 1f).apply {
            duration = 150
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                currentAlpha = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun hideScrollBar() {
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(currentAlpha, 0f).apply {
            duration = 500
            startDelay = 1000
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                currentAlpha = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isScrollBarVisible = false
                    visibility = GONE
                }
            })
            start()
        }
    }

    private fun dpToPx(dp: Int): Float {
        return dp * resources.displayMetrics.density
    }
}
