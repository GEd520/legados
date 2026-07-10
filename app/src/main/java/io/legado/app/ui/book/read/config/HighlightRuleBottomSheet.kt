package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.windowSize
import splitties.systemservices.windowManager

abstract class HighlightRuleBottomSheetFragment(
    @LayoutRes layoutId: Int,
    private val adaptationSoftKeyboard: Boolean = false,
) : BottomSheetDialogFragment(layoutId) {

    private var onDismissListener: DialogInterface.OnDismissListener? = null
    private var parentDialogDecor: View? = null

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) {
        onDismissListener = listener
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentDialogDecor = generateSequence(parentFragment) { it.parentFragment }
            .filterIsInstance<DialogFragment>()
            .firstOrNull()
            ?.dialog
            ?.window
            ?.decorView
            ?.also { it.visibility = View.INVISIBLE }

        view.setBackgroundColor(Color.TRANSPARENT)
        if (adaptationSoftKeyboard) {
            view.findViewById<View>(R.id.vw_bg)?.setOnClickListener(null)
            view.setOnClickListener { dismiss() }
        }
        onFragmentCreated(view, savedInstanceState)
        observeLiveBus()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            if (adaptationSoftKeyboard) {
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            if (AppConfig.isEInkMode) {
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply {
                    dimAmount = 0f
                    windowAnimations = 0
                }
            }
        }

        val bottomSheet = dialog
            ?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        val sheetHeight =
            (requireContext().windowManager.windowSize.heightPixels * SHEET_HEIGHT_RATIO).toInt()

        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = sheetHeight
        }
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        view?.layoutParams = view?.layoutParams?.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }

        BottomSheetBehavior.from(bottomSheet).apply {
            peekHeight = sheetHeight
            skipCollapsed = true
            isHideable = true
            isDraggable = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        parentDialogDecor?.visibility = View.VISIBLE
        parentDialogDecor = null
        super.onDismiss(dialog)
        val listener = onDismissListener
        onDismissListener = null
        listener?.onDismiss(dialog)
    }

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching {
            manager.beginTransaction().remove(this).commit()
            super.show(manager, tag)
        }.onFailure {
            AppLog.put("显示高亮规则底栏失败 tag:$tag", it)
        }
    }

    abstract fun onFragmentCreated(view: View, savedInstanceState: Bundle?)

    open fun observeLiveBus() {
    }

    private companion object {
        const val SHEET_HEIGHT_RATIO = 0.85f
    }
}
