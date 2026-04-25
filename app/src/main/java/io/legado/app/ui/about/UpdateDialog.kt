package io.legado.app.ui.about

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogUpdateBinding
import io.legado.app.help.update.AppUpdate
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.Download
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.help.InnerBrowserLinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin

/**
 * 应用更新对话框
 * 用于显示新版本更新信息并提供下载功能
 * 支持 Markdown 格式的更新日志渲染
 */
class UpdateDialog() : BaseDialogFragment(R.layout.dialog_update) {

    /**
     * 便利构造函数，用于传入更新信息自动构建参数
     */
    constructor(updateInfo: AppUpdate.UpdateInfo) : this() {
        arguments = Bundle().apply {
            putString("newVersion", updateInfo.tagName)
            putString("updateBody", updateInfo.updateLog)
            putString("url", updateInfo.downloadUrl)
            putString("name", updateInfo.fileName)
        }
    }

    val binding by viewBinding(DialogUpdateBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.title = arguments?.getString("newVersion")
        val updateBody = arguments?.getString("updateBody")

        // 检查更新日志是否存在，不存在则关闭对话框
        if (updateBody == null) {
            toastOnUi("没有数据")
            dismiss()
            return
        }

        // 使用 Markwon 渲染 Markdown 格式的更新日志
        // Markwon 配置说明：
        // - InnerBrowserLinkResolver: 使 Markdown 中的链接点击后使用内置浏览器打开
        // - GlideImagesPlugin: 支持 Markdown 中的图片显示
        // - HtmlPlugin: 支持 Markdown 中的 HTML 标签
        // - TablePlugin: 支持 Markdown 中的表格渲染
        binding.textView.post {
            Markwon.builder(requireContext())
                .usePlugin(object : io.noties.markwon.AbstractMarkwonPlugin() {
                    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                        builder.linkResolver(InnerBrowserLinkResolver)
                    }
                })
                .usePlugin(GlideImagesPlugin.create(requireContext()))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(TablePlugin.create(requireContext()))
                .build()
                .setMarkdown(binding.textView, updateBody)
        }

        // 设置下载菜单
        binding.toolBar.inflateMenu(R.menu.app_update)
        binding.toolBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_download -> {
                    val url = arguments?.getString("url")
                    val name = arguments?.getString("name")
                    if (url != null && name != null) {
                        Download.start(requireContext(), url, name)
                        toastOnUi(R.string.download_start)
                    }
                }
            }
            return@setOnMenuItemClickListener true
        }
    }
}