package io.legado.app.lib.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import androidx.core.graphics.ColorUtils
import io.legado.app.R
import io.legado.app.utils.dpToPx

object UiCorner {

    fun panelRadius(context: Context): Float {
        return context.resources.getDimension(R.dimen.ui_panel_radius)
    }

    fun actionRadius(context: Context): Float {
        return context.resources.getDimension(R.dimen.ui_action_radius)
    }

    fun rounded(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    fun opaqueRounded(color: Int, radius: Float): GradientDrawable {
        return rounded(color, radius)
    }

    fun panelRounded(context: Context, color: Int, radius: Float): Drawable {
        return rounded(color, radius).apply {
            setStroke(1.dpToPx(), panelStrokeColor(color))
        }
    }

    fun actionSelector(defaultColor: Int, pressedColor: Int, radius: Float): StateListDrawable {
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(pressedColor, radius))
            addState(intArrayOf(android.R.attr.state_selected), rounded(pressedColor, radius))
            addState(intArrayOf(), opaqueRounded(defaultColor, radius))
        }
    }

    private fun panelStrokeColor(color: Int): Int {
        val base = if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE
        return ColorUtils.setAlphaComponent(base, (0.10f * 255).toInt())
    }
}
