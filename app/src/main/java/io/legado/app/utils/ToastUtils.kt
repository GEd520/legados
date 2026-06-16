@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import io.legado.app.BuildConfig
import io.legado.app.databinding.ViewToastBinding
import io.legado.app.data.repository.debug.DebugEventCenter
import io.legado.app.help.config.AppConfig
import io.legado.app.model.debug.DebugCategory
import io.legado.app.model.debug.DebugEvent
import io.legado.app.model.debug.DebugLevel
import io.legado.app.model.debug.ToastContext
import io.legado.app.help.LifecycleHelp
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.utils.runOnUI
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import splitties.systemservices.layoutInflater

private var toast: Toast? = null

private var toastLegacy: Toast? = null

fun Context.toastOnUi(message: Int, duration: Int = Toast.LENGTH_SHORT) {
    toastOnUi(getString(message), duration)
}

@SuppressLint("InflateParams")
@Suppress("DEPRECATION")
fun Context.toastOnUi(message: CharSequence?, duration: Int = Toast.LENGTH_SHORT) {
    runOnUI {
        kotlin.runCatching {
            toast?.cancel()
            toast = Toast(this)
            val bgColor = backgroundColor
            val isLight = ColorUtils.isColorLight(bgColor)
            ViewToastBinding.inflate(layoutInflater).run {
                toast?.view = root
                cvToast.setCardBackgroundColor(bgColor)
                tvText.setTextColor(getPrimaryTextColor(isLight))
                tvText.text = message
            }
            toast?.duration = duration
            toast?.show()
            
            // 记录Toast到调试日志
            recordToast(message, duration)
        }
    }
}

fun Context.toastOnUiLegacy(message: CharSequence) {
    runOnUI {
        kotlin.runCatching {
            if (toastLegacy == null || BuildConfig.DEBUG || AppConfig.recordLog) {
                toastLegacy = Toast.makeText(this, message, Toast.LENGTH_SHORT)
            } else {
                toastLegacy?.setText(message)
                toastLegacy?.duration = Toast.LENGTH_SHORT
            }
            toastLegacy?.show()
            
            // 记录Toast到调试日志
            recordToast(message, Toast.LENGTH_SHORT)
        }
    }
}

fun Context.longToastOnUi(message: Int) {
    toastOnUi(message, Toast.LENGTH_LONG)
}

fun Context.longToastOnUi(message: CharSequence?) {
    toastOnUi(message, Toast.LENGTH_LONG)
}

fun Context.longToastOnUiLegacy(message: CharSequence) {
    runOnUI {
        kotlin.runCatching {
            if (toastLegacy == null || BuildConfig.DEBUG || AppConfig.recordLog) {
                toastLegacy = Toast.makeText(this, message, Toast.LENGTH_LONG)
            } else {
                toastLegacy?.setText(message)
                toastLegacy?.duration = Toast.LENGTH_LONG
            }
            toastLegacy?.show()
            
            // 记录Toast到调试日志
            recordToast(message, Toast.LENGTH_LONG)
        }
    }
}

fun Fragment.toastOnUi(message: Int) = requireActivity().toastOnUi(message)

fun Fragment.toastOnUi(message: CharSequence) = requireActivity().toastOnUi(message)

fun Fragment.longToast(message: Int) = requireContext().longToastOnUi(message)

fun Fragment.longToast(message: CharSequence) = requireContext().longToastOnUi(message)

/**
 * 记录Toast消息到调试日志
 */
@OptIn(DelicateCoroutinesApi::class)
private fun recordToast(message: CharSequence?, duration: Int, context: ToastContext = ToastContext()) {
    if (message.isNullOrBlank()) return
    
    val durationText = if (duration == Toast.LENGTH_LONG) "长" else "短"
    
    val activityName = context.activityName ?: LifecycleHelp.getCurrentActivityName()
    val mergedContext = ToastContext(
        activityName = activityName,
        sourceName = context.sourceName,
        sourceType = context.sourceType,
        ruleType = context.ruleType,
        ruleLine = context.ruleLine
    )
    
    GlobalScope.launch(Dispatchers.Default) {
        DebugEventCenter.emit(
            DebugEvent(
                level = DebugLevel.INFO,
                category = DebugCategory.TOAST,
                message = "[${durationText}Toast] $message",
                detail = message.toString(),
                sourceName = mergedContext.sourceName,
                tags = mergedContext.toTagsMap()
            )
        )
    }
}

fun Context.toastOnUi(message: CharSequence?, context: ToastContext, duration: Int = Toast.LENGTH_SHORT) {
    runOnUI {
        kotlin.runCatching {
            toast?.cancel()
            toast = Toast(this)
            val bgColor = backgroundColor
            val isLight = ColorUtils.isColorLight(bgColor)
            ViewToastBinding.inflate(layoutInflater).run {
                toast?.view = root
                cvToast.setCardBackgroundColor(bgColor)
                tvText.setTextColor(getPrimaryTextColor(isLight))
                tvText.text = message
            }
            toast?.duration = duration
            toast?.show()
            
            recordToast(message, duration, context)
        }
    }
}

fun Context.longToastOnUi(message: CharSequence?, context: ToastContext) {
    toastOnUi(message, context, Toast.LENGTH_LONG)
}

fun Fragment.toastOnUi(message: CharSequence, context: ToastContext) = requireActivity().toastOnUi(message, context)

fun Fragment.longToast(message: CharSequence, context: ToastContext) = requireContext().longToastOnUi(message, context)
