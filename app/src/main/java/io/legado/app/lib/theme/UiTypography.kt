package io.legado.app.lib.theme

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.TitleBar
import io.legado.app.utils.dpToPx

fun Context.uiTypeface(): Typeface {
    return when (AppConfig.systemTypefaces) {
        1 -> Typeface.SERIF
        2 -> Typeface.MONOSPACE
        else -> Typeface.SANS_SERIF
    }
}

fun View.applyUiBodyTypefaceDeep(typeface: Typeface) {
    when (this) {
        is TitleBar -> return
        is TextView -> {
            if (getTag(R.id.ui_title_typeface_role) == true) return
            if (this.typeface != typeface) {
                this.typeface = typeface
            }
        }
        is ViewGroup -> {
            for (index in 0 until childCount) {
                getChildAt(index).applyUiBodyTypefaceDeep(typeface)
            }
        }
    }
}

fun TextView.applyUiLabelStyle(context: Context) {
    setTag(R.id.ui_title_typeface_role, false)
    typeface = context.uiTypeface()
    textSize = 14f
    setTextColor(ContextCompat.getColor(context, R.color.secondaryText))
}

fun TextView.applyUiSectionTitleStyle(context: Context) {
    setTag(R.id.ui_title_typeface_role, true)
    typeface = context.uiTypeface()
    textSize = 15f
    setTextColor(ContextCompat.getColor(context, R.color.primaryText))
}

fun EditText.applyUiInputStyle(context: Context, minLines: Int = 1) {
    setTag(R.id.ui_title_typeface_role, false)
    typeface = context.uiTypeface()
    textSize = 15f
    this.minLines = minLines
    maxLines = if (minLines > 1) 8 else 2
    setSingleLine(minLines == 1)
    minHeight = if (minLines > 1) 92.dpToPx() else 44.dpToPx()
    setTextColor(ContextCompat.getColor(context, R.color.primaryText))
    setHintTextColor(ContextCompat.getColor(context, R.color.secondaryText))
    val horizontal = 12.dpToPx()
    val vertical = if (minLines > 1) 10.dpToPx() else 8.dpToPx()
    setPadding(horizontal, vertical, horizontal, vertical)
}
