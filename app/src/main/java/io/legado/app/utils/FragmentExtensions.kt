@file:Suppress("unused")

package io.legado.app.utils

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isVideo
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.video.VideoPlayerActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.widget.dialog.TextDialog

/**
 * 显示对话框的泛型扩展函数
 * 通过反射创建 DialogFragment 实例并显示
 *
 * @param T DialogFragment 的子类类型
 * @param arguments 可选的 Bundle 参数配置 lambda
 *
 * @sample showDialogFragment<HelpSearchDialog>()  // 无参调用
 * @sample showDialogFragment<TextDialog> { putString("title", "帮助") }  // 带参数调用
 */
inline fun <reified T : DialogFragment> Fragment.showDialogFragment(
    arguments: Bundle.() -> Unit = {}
) {
    @Suppress("DEPRECATION")
    val dialog = T::class.java.newInstance()
    val bundle = Bundle()
    bundle.apply(arguments)
    dialog.arguments = bundle
    dialog.show(childFragmentManager, T::class.simpleName)
}

/**
 * 显示已创建的 DialogFragment 实例
 *
 * @param dialogFragment 已实例化的 DialogFragment 对象
 */
fun Fragment.showDialogFragment(dialogFragment: DialogFragment) {
    dialogFragment.show(childFragmentManager, dialogFragment::class.simpleName)
}

/** 读取偏好设置中的布尔值，默认值为 false */
fun Fragment.getPrefBoolean(key: String, defValue: Boolean = false) =
    requireContext().defaultSharedPreferences.getBoolean(key, defValue)

/** 保存布尔值到偏好设置 */
fun Fragment.putPrefBoolean(key: String, value: Boolean = false) =
    requireContext().defaultSharedPreferences.edit { putBoolean(key, value) }

/** 读取偏好设置中的整数值，默认值为 0 */
fun Fragment.getPrefInt(key: String, defValue: Int = 0) =
    requireContext().defaultSharedPreferences.getInt(key, defValue)

/** 保存整数值到偏好设置 */
fun Fragment.putPrefInt(key: String, value: Int) =
    requireContext().defaultSharedPreferences.edit { putInt(key, value) }

/** 读取偏好设置中的长整型值，默认值为 0L */
fun Fragment.getPrefLong(key: String, defValue: Long = 0L) =
    requireContext().defaultSharedPreferences.getLong(key, defValue)

/** 保存长整型值到偏好设置 */
fun Fragment.putPrefLong(key: String, value: Long) =
    requireContext().defaultSharedPreferences.edit { putLong(key, value) }

/** 读取偏好设置中的字符串值，默认值为 null */
fun Fragment.getPrefString(key: String, defValue: String? = null) =
    requireContext().defaultSharedPreferences.getString(key, defValue)

/** 保存字符串值到偏好设置 */
fun Fragment.putPrefString(key: String, value: String) =
    requireContext().defaultSharedPreferences.edit { putString(key, value) }

/** 读取偏好设置中的字符串集合，默认值为 null */
fun Fragment.getPrefStringSet(
    key: String,
    defValue: MutableSet<String>? = null
): MutableSet<String>? =
    requireContext().defaultSharedPreferences.getStringSet(key, defValue)

/** 保存字符串集合到偏好设置 */
fun Fragment.putPrefStringSet(key: String, value: MutableSet<String>) =
    requireContext().defaultSharedPreferences.edit { putStringSet(key, value) }

/** 从偏好设置中移除指定键的值 */
fun Fragment.removePref(key: String) =
    requireContext().defaultSharedPreferences.edit { remove(key) }

/** 获取兼容模式的颜色值（支持夜间模式等） */
fun Fragment.getCompatColor(@ColorRes id: Int): Int = requireContext().getCompatColor(id)

/** 获取兼容模式的 Drawable（支持夜间模式等） */
fun Fragment.getCompatDrawable(@DrawableRes id: Int): Drawable? =
    requireContext().getCompatDrawable(id)

/** 获取兼容模式的 ColorStateList（支持夜间模式等） */
fun Fragment.getCompatColorStateList(@ColorRes id: Int): ColorStateList? =
    requireContext().getCompatColorStateList(id)

/**
 * 启动 Activity 的泛型扩展函数
 *
 * @param T Activity 的子类类型
 * @param configIntent 可选的 Intent 配置 lambda，用于传递数据
 *
 * @sample startActivity<CodeEditActivity> { putExtra("key", "value") }
 */
inline fun <reified T : Activity> Fragment.startActivity(
    configIntent: Intent.() -> Unit = {}
) {
    startActivity(Intent(requireContext(), T::class.java).apply(configIntent))
}

/**
 * 根据书籍类型启动对应的阅读 Activity
 * 自动判断是视频、音频、图片漫画还是普通阅读
 *
 * @param book 书籍实体对象
 * @param configIntent 可选的 Intent 配置 lambda
 */
fun Fragment.startActivityForBook(
    book: Book,
    configIntent: Intent.() -> Unit = {},
) {
    val cls = when {
        book.isVideo -> VideoPlayerActivity::class.java
        book.isAudio -> AudioPlayActivity::class.java
        !book.isLocal && book.isImage && AppConfig.showMangaUi -> ReadMangaActivity::class.java
        else -> ReadBookActivity::class.java
    }
    val intent = Intent(requireActivity(), cls)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.putExtra("bookUrl", book.bookUrl)
    intent.apply(configIntent)
    startActivity(intent)
}

/**
 * 显示目录 help 下的帮助文档
 * 会加载 assets/web/help/md/ 目录下的 Markdown 文件并以格式化方式显示
 *
 * @param fileName 帮助文档文件名（不含 .md 后缀），如 "ruleHelp"、"rssRuleHelp"
 *
 * @see TextDialog.Mode.MD
 */
fun Fragment.showHelp(fileName: String) {
    val mdText = String(requireContext().assets.open("web/help/md/${fileName}.md").readBytes())
    showDialogFragment(TextDialog(getString(R.string.help), mdText, TextDialog.Mode.MD, fileName))
}

/**
 * 判断 Fragment 是否已创建（处于 CREATED 或更晚的生命周期状态）
 * 可用于防止在 Fragment 未完全创建时执行操作
 */
val Fragment.isCreated
    get() = lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)
