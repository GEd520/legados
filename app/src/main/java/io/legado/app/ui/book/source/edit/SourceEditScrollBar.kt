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

/**
 * 书源编辑界面的自定义滚动条组件
 *
 * 功能：
 * 1. 精确同步 RecyclerView 的滚动位置
 * 2. 支持拖拽滚动条快速定位
 * 3. 使用增量滚动避免抽搐问题
 *
 * 使用方式：
 * 1. 在 XML 中将本组件覆盖在 RecyclerView 上方
 * 2. 调用 attachRecyclerView() 绑定 RecyclerView
 * 3. 销毁时调用 detachRecyclerView() 解绑
 */
class SourceEditScrollBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 滚动条宽度 */
    private val scrollBarWidth = dpToPx(6)

    /** 滚动条最小高度（内容过少时也保持这个尺寸） */
    private val scrollBarMinHeight = dpToPx(40)

    /** 滚动条距右侧的距离 */
    private val scrollBarMarginEnd = dpToPx(4)

    /** 触摸区域扩展 padding（扩大触摸范围，提升用户体验） */
    private val touchAreaPadding = dpToPx(12)

    /** 滚动条默认颜色（50%透明度的主题色） */
    @ColorInt
    private val scrollBarColor: Int = ColorUtils.adjustAlpha(context.accentColor, 0.5f)

    /** 滚动条按下时的颜色（100%透明度的主题色） */
    @ColorInt
    private val scrollBarColorPressed: Int = context.accentColor

    /** 绘制滚动条的画笔 */
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = scrollBarColor
        style = Paint.Style.FILL
    }

    /** 滚动条的矩形区域（用于绘制和触摸判断） */
    private val scrollBarRect = RectF()

    /** 绑定的 RecyclerView 引用 */
    private var recyclerView: RecyclerView? = null

    /** 是否正在拖拽滚动条 */
    private var isDragging = false

    /** 拖拽开始时手指的 Y 坐标 */
    private var dragStartY = 0f

    /** 拖拽开始时 RecyclerView 的滚动偏移量 */
    private var scrollStartOffset = 0

    /**
     * RecyclerView 的滚动监听器
     * 滚动时触发重绘，更新滚动条位置
     */
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            invalidate()
        }
    }

    /**
     * 绑定 RecyclerView
     * @param recyclerView 要绑定的 RecyclerView 实例
     */
    fun attachRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        recyclerView.addOnScrollListener(scrollListener)
        post { invalidate() }
    }

    /**
     * 解绑 RecyclerView
     * 应在 Activity/Fragment 销毁时调用，防止内存泄漏
     */
    fun detachRecyclerView() {
        recyclerView?.removeOnScrollListener(scrollListener)
        recyclerView = null
    }

    /**
     * 绘制滚动条
     *
     * 核心逻辑：
     * 1. 从 RecyclerView 获取滚动信息（总范围、可视范围、当前偏移）
     * 2. 计算滚动条高度（根据可视比例）
     * 3. 计算滚动条顶部位置（根据滚动比例）
     * 4. 绘制圆角矩形作为滚动条
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val recyclerView = recyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val adapter = recyclerView.adapter ?: return

        val itemCount = adapter.itemCount
        if (itemCount == 0) return

        // 获取 RecyclerView 滚动相关参数
        val verticalScrollRange = recyclerView.computeVerticalScrollRange()        // 内容总高度
        val verticalScrollExtent = recyclerView.computeVerticalScrollExtent()      // 可视区域高度
        val verticalScrollOffset = recyclerView.computeVerticalScrollOffset()     // 当前已滚动偏移

        // 如果内容不足以滚动，则不显示滚动条
        if (verticalScrollRange <= verticalScrollExtent) return

        // 计算滚动条高度（根据可视范围占总内容的比例）
        val scrollBarHeight = (verticalScrollExtent.toFloat() / verticalScrollRange * height)
            .coerceAtLeast(scrollBarMinHeight.toFloat())

        // 最大滚动距离（总高度 - 可视高度）
        val maxScroll = verticalScrollRange - verticalScrollExtent

        // 当前滚动位置占总可滚动距离的比例 [0, 1]
        val scrollProportion = verticalScrollOffset.toFloat() / maxScroll

        // 计算滚动条在 View 中的实际位置
        val availableHeight = height - scrollBarHeight
        val scrollBarTop = scrollProportion * availableHeight
        val scrollBarBottom = scrollBarTop + scrollBarHeight

        // 计算滚动条左右边界
        val right = width - scrollBarMarginEnd
        val left = right - scrollBarWidth

        // 更新滚动条矩形区域
        scrollBarRect.set(left, scrollBarTop, right, scrollBarBottom)

        // 拖拽时高亮显示
        paint.color = if (isDragging) scrollBarColorPressed else scrollBarColor

        // 绘制滚动条
        canvas.drawRect(scrollBarRect, paint)
    }

    /**
     * 处理触摸事件，实现拖拽滚动功能
     *
     * 触摸区域判断：滚动条矩形区域左右扩展 touchAreaPadding
     *
     * @return true 表示事件已处理，false 表示忽略
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val recyclerView = recyclerView ?: return false
        val touchX = event.x
        val touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 计算触摸区域（扩展左右范围）
                val scrollBarLeft = scrollBarRect.left - touchAreaPadding
                val scrollBarRight = scrollBarRect.right + touchAreaPadding

                // 判断触摸点是否在滚动条区域内
                // 必须满足：Y 在滚动条范围内 且 X 在扩展触摸区域内
                if (touchY < scrollBarRect.top || touchY > scrollBarRect.bottom ||
                    touchX < scrollBarLeft || touchX > scrollBarRight
                ) {
                    return false
                }

                // 开始拖拽，记录起始状态
                isDragging = true
                dragStartY = touchY
                scrollStartOffset = recyclerView.computeVerticalScrollOffset()

                // 请求父 View 不要拦截触摸事件（防止滚动冲突）
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false

                // 计算手指移动的距离
                val deltaY = touchY - dragStartY

                // 获取 RecyclerView 滚动参数
                val recyclerViewHeight = recyclerView.height
                val verticalScrollRange = recyclerView.computeVerticalScrollRange()
                val verticalScrollExtent = recyclerView.computeVerticalScrollExtent()
                val maxScroll = verticalScrollRange - verticalScrollExtent

                if (maxScroll <= 0) return true

                // 将手指移动距离转换为内容滚动距离
                // 比例：手指移动 deltaY / RecyclerView高度 = 内容滚动 / 最大可滚动距离
                val scrollDelta = (deltaY / recyclerViewHeight * maxScroll).toInt()

                // 计算目标滚动位置（相对于拖拽开始时的偏移）
                val targetScroll = (scrollStartOffset + scrollDelta).coerceIn(0, maxScroll)

                // 使用 scrollBy 而非 scrollToPositionWithOffset，避免跳动
                recyclerView.scrollBy(0, targetScroll - scrollStartOffset)

                // 更新起始状态，保持拖拽连贯性
                scrollStartOffset = targetScroll
                dragStartY = touchY
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    // 恢复父 View 触摸事件拦截
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * dp 转 px
     * @param dp dp 值
     * @return px 值
     */
    private fun dpToPx(dp: Int): Float {
        return dp * resources.displayMetrics.density
    }
}
