package io.legado.app.ui.widget.recycler.scroller

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R

@Suppress("MemberVisibilityCanBePrivate", "unused")
open class FastScrollRecyclerView : RecyclerView {

    private lateinit var mFastScroller: FastScroller

    constructor(context: Context) : super(context) {
        layout(context, null)
        layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?
    ) : super(context, attrs) {
        layout(context, attrs)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        layout(context, attrs)
    }

    private fun layout(context: Context, attrs: AttributeSet?) {
        mFastScroller = FastScroller(context, attrs)
        mFastScroller.id = R.id.fast_scroller
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        super.setAdapter(adapter)
        if (adapter is FastScroller.SectionIndexer) {
            setSectionIndexer(adapter as FastScroller.SectionIndexer?)
        } else if (adapter == null) {
            setSectionIndexer(null)
        }
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        mFastScroller.visibility = visibility
    }

    fun setSectionIndexer(sectionIndexer: FastScroller.SectionIndexer?) {
        mFastScroller.setSectionIndexer(sectionIndexer)
    }

    fun setFastScrollEnabled(enabled: Boolean) {
        mFastScroller.isEnabled = enabled
    }

    fun setHideScrollbar(hideScrollbar: Boolean) {
        mFastScroller.setFadeScrollbar(hideScrollbar)
    }

    fun setTrackVisible(visible: Boolean) {
        mFastScroller.setTrackVisible(visible)
    }

    fun setTrackColor(@ColorInt color: Int) {
        mFastScroller.setTrackColor(color)
    }

    fun setHandleColor(@ColorInt color: Int) {
        mFastScroller.setHandleColor(color)
    }

    fun setBubbleVisible(visible: Boolean) {
        mFastScroller.setBubbleVisible(visible)
    }

    fun setBubbleColor(@ColorInt color: Int) {
        mFastScroller.setBubbleColor(color)
    }

    fun setBubbleTextColor(@ColorInt color: Int) {
        mFastScroller.setBubbleTextColor(color)
    }

    fun setFastScrollStateChangeListener(fastScrollStateChangeListener: FastScrollStateChangeListener) {
        mFastScroller.setFastScrollStateChangeListener(fastScrollStateChangeListener)
    }

    fun refreshFastScrollerLayout() {
        (mFastScroller.parent as? ViewGroup)?.let {
            mFastScroller.setLayoutParams(it)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mFastScroller.attachRecyclerView(this)
        var parent = parent
        while (parent != null) {
            when (parent) {
                is ConstraintLayout, is CoordinatorLayout, is FrameLayout, is RelativeLayout -> break
                else -> parent = parent.parent
            }
        }
        if (parent is ViewGroup && parent.indexOfChild(mFastScroller) == -1) {
            parent.addView(mFastScroller)
            mFastScroller.setLayoutParams(parent)
        }
    }

    override fun onDetachedFromWindow() {
        mFastScroller.detachRecyclerView()
        super.onDetachedFromWindow()
    }

}
