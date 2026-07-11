package io.legado.app.ui.book.read.config

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.appcompat.view.WindowCallbackWrapper
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
    private val useFadeAnimation: Boolean = false,
) : BottomSheetDialogFragment(layoutId) {

    private var onDismissListener: DialogInterface.OnDismissListener? = null
    private var originalWindowCallback: Window.Callback? = null

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) {
        onDismissListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).also { dialog ->
            if (useFadeAnimation && !AppConfig.isEInkMode) {
                dialog.window?.setWindowAnimations(R.style.TextActionMenuAnimation)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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

        val behavior = BottomSheetBehavior.from(bottomSheet).apply {
            peekHeight = sheetHeight
            skipCollapsed = true
            isHideable = true
            isDraggable = true
            isDraggableOnNestedScroll = false
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        restrictDraggingToHeader(behavior)
    }

    override fun onStop() {
        dialog?.window?.let { window ->
            originalWindowCallback?.let { window.callback = it }
        }
        originalWindowCallback = null
        super.onStop()
    }

    override fun onDismiss(dialog: DialogInterface) {
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

    private fun restrictDraggingToHeader(behavior: BottomSheetBehavior<FrameLayout>) {
        val window = dialog?.window ?: return
        val sheet = view?.findViewById<View>(R.id.sheet_container) ?: return
        val header = view?.findViewById<View>(R.id.drag_header) ?: return
        val callback = window.callback ?: return
        if (originalWindowCallback != null) return

        originalWindowCallback = callback
        window.callback = object : WindowCallbackWrapper(callback) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    behavior.isDraggable = event.isInsideHeader(sheet, header)
                }
                return super.dispatchTouchEvent(event).also {
                    if (event.actionMasked == MotionEvent.ACTION_UP ||
                        event.actionMasked == MotionEvent.ACTION_CANCEL
                    ) {
                        behavior.isDraggable = true
                    }
                }
            }
        }
    }

    private fun MotionEvent.isInsideHeader(sheet: View, header: View): Boolean {
        val sheetLocation = IntArray(2)
        val headerLocation = IntArray(2)
        sheet.getLocationOnScreen(sheetLocation)
        header.getLocationOnScreen(headerLocation)
        return rawX >= sheetLocation[0] &&
            rawX <= sheetLocation[0] + sheet.width &&
            rawY >= sheetLocation[1] &&
            rawY <= headerLocation[1] + header.height
    }

    private companion object {
        const val SHEET_HEIGHT_RATIO = 0.85f
    }
}
