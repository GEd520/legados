package io.legado.app.ui.widget

import android.annotation.SuppressLint
import android.app.SearchableInfo
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.drawable.DrawableCompat
import io.legado.app.R
import io.legado.app.utils.printOnDebug


class SearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SearchView(context, attrs) {
    private var mSearchHintIcon: Drawable? = null
    private var mOriginalHintIcon: Drawable? = null
    private var textView: TextView? = null
    private var hintIconTint: Int? = null

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        super.onLayout(changed, left, top, right, bottom)
        try {
            if (textView == null) {
                textView = findViewById(androidx.appcompat.R.id.search_src_text)
                mOriginalHintIcon = this.context.getDrawable(R.drawable.ic_search_hint)
                mSearchHintIcon = mOriginalHintIcon
                applyHintIconTint()
                // 改变字体（只需初始化一次）
                textView!!.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                textView!!.gravity = Gravity.CENTER_VERTICAL
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    textView!!.isLocalePreferredLineHeightForMinimumUsed = false
                }
            }
            updateQueryHint()
        } catch (e: Exception) {
            e.printOnDebug()
        }
    }

    private fun getDecoratedHint(hintText: CharSequence): CharSequence {
        // If the field is always expanded or we don't have a search hint icon,
        // then don't add the search icon to the hint.
        val icon = mSearchHintIcon ?: return hintText
        val textSize = textView!!.textSize.toInt()
        icon.setBounds(0, 0, textSize, textSize)
        val ssb = SpannableStringBuilder("   ")
        ssb.setSpan(CenteredImageSpan(icon), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.append(hintText)
        return ssb
    }

    private fun updateQueryHint() {
        textView?.let {
            it.hint = getDecoratedHint(queryHint ?: "")
        }
    }

    fun setSearchHintIconTint(@ColorInt color: Int) {
        hintIconTint = color
        applyHintIconTint()
        updateQueryHint()
    }

    private fun applyHintIconTint() {
        val color = hintIconTint ?: return
        mSearchHintIcon = mOriginalHintIcon?.mutate()?.let {
            DrawableCompat.wrap(it).apply {
                DrawableCompat.setTint(this, color)
            }
        }
    }

    override fun setIconifiedByDefault(iconified: Boolean) {
        super.setIconifiedByDefault(iconified)
        updateQueryHint()
    }

    override fun setSearchableInfo(searchable: SearchableInfo?) {
        super.setSearchableInfo(searchable)
        searchable?.let {
            updateQueryHint()
        }
    }

    override fun setQueryHint(hint: CharSequence?) {
        super.setQueryHint(hint)
        updateQueryHint()
    }

    internal class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {
        override fun draw(
            canvas: Canvas, text: CharSequence,
            start: Int, end: Int, x: Float,
            top: Int, y: Int, bottom: Int, paint: Paint
        ) {
            // image to draw
            val b = drawable
            // font metrics of text to be replaced
            val fm = paint.fontMetricsInt
            val transY = ((y + fm.descent + y + fm.ascent) / 2
                    - b.bounds.bottom / 2)
            canvas.save()
            canvas.translate(x, transY.toFloat())
            b.draw(canvas)
            canvas.restore()
        }
    }
}
