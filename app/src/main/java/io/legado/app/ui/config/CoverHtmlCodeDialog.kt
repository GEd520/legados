package io.legado.app.ui.config

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogCoverHtmlCodeBinding
import io.legado.app.help.DefaultData
import io.legado.app.help.config.CoverHtmlTemplateConfig
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.ui.widget.code.addHtmlPattern
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.utils.GSON
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch
import splitties.views.onClick

/**
 * HTML封面代码配置对话框
 * 
 * 用于配置自定义HTML模板来生成封面图片，支持以下功能：
 * - 设置模板名称
 * - 输入书名和作者进行实时预览
 * - 编辑HTML代码模板（支持语法高亮）
 * - WebView实时渲染预览效果
 * 
 * 支持的变量：
 * - {{bookName}}: 书名
 * - {{author}}: 作者
 */
class CoverHtmlCodeDialog : BaseDialogFragment(R.layout.dialog_cover_html_code) {

    val binding by viewBinding(DialogCoverHtmlCodeBinding::bind)

    private var template: CoverHtmlTemplateConfig.Template? = null
    private var isNewTemplate: Boolean = false

    companion object {
        private const val KEY_TEMPLATE = "template"

        /**
         * 创建对话框实例
         * 
         * @param template 要编辑的模板，为null时表示新建模板
         */
        fun newInstance(template: CoverHtmlTemplateConfig.Template?): CoverHtmlCodeDialog {
            return CoverHtmlCodeDialog().apply {
                if (template != null) {
                    arguments = bundleOf(KEY_TEMPLATE to GSON.toJson(template))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        parseArguments()
        initWebView()
        initCodeView()
        initData()
        initClickListeners()
    }

    /**
     * 解析传入的参数
     */
    private fun parseArguments() {
        val templateJson = arguments?.getString(KEY_TEMPLATE)
        if (templateJson != null) {
            template = GSON.fromJson(templateJson, CoverHtmlTemplateConfig.Template::class.java)
        }
        isNewTemplate = template == null
    }

    /**
     * 初始化WebView配置
     * 
     * 配置WebView用于预览HTML封面效果：
     * - 启用JavaScript支持
     * - 禁用缩放功能
     * - 设置自适应布局
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        binding.webViewPreview.setBackgroundColor(Color.TRANSPARENT)
        binding.webViewPreview.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            domStorageEnabled = true
            defaultTextEncodingName = "UTF-8"
        }
        binding.webViewPreview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }
    }

    /**
     * 初始化代码编辑器
     * 
     * 为代码编辑器添加HTML和JavaScript语法高亮支持
     */
    private fun initCodeView() {
        binding.codeView.addHtmlPattern()
        binding.codeView.addJsPattern()
    }

    /**
     * 初始化数据
     * 
     * 从传入的模板加载配置，若为新建则使用默认模板
     */
    private fun initData() {
        lifecycleScope.launch {
            val currentTemplate = template
            if (currentTemplate != null) {
                binding.editTemplateName.setText(currentTemplate.name)
                binding.codeView.setText(currentTemplate.htmlCode)
            } else {
                binding.editTemplateName.setText("")
                binding.codeView.setText(DefaultData.coverHtmlTemplate)
            }
            binding.editBookName.setText("示例书名")
            binding.editAuthor.setText("示例作者")
            binding.webViewPreview.post {
                previewCover()
            }
        }
    }

    /**
     * 初始化点击事件监听
     */
    private fun initClickListeners() {
        binding.tvPreview.onClick {
            previewCover()
        }

        binding.tvCancel.onClick {
            dismissAllowingStateLoss()
        }

        binding.tvOk.onClick {
            saveTemplate()
            dismissAllowingStateLoss()
        }

        binding.tvFooterLeft.onClick {
            binding.editTemplateName.setText("")
            binding.codeView.setText(DefaultData.coverHtmlTemplate)
        }
    }

    /**
     * 预览封面
     * 
     * 获取用户输入的书名、作者和HTML模板，
     * 替换变量后在WebView中渲染预览
     */
    private fun previewCover() {
        val htmlTemplate = binding.codeView.text?.toString() ?: return
        val bookName = binding.editBookName.text?.toString() ?: "书名"
        val author = binding.editAuthor.text?.toString() ?: "作者"

        val renderedHtml = renderHtml(htmlTemplate, bookName, author)
        binding.webViewPreview.loadDataWithBaseURL(
            "about:blank",
            renderedHtml,
            "text/html",
            "UTF-8",
            null
        )
    }

    /**
     * 渲染HTML模板
     * 
     * 将模板中的变量替换为实际值
     * 
     * @param template HTML模板字符串
     * @param bookName 书名
     * @param author 作者
     * @return 渲染后的HTML字符串
     */
    private fun renderHtml(template: String, bookName: String, author: String): String {
        return template
            .replace("{{bookName}}", bookName)
            .replace("{{author}}", author)
    }

    /**
     * 保存模板
     */
    private fun saveTemplate() {
        val name = binding.editTemplateName.text?.toString()?.trim() ?: ""
        val htmlCode = binding.codeView.text?.toString() ?: ""

        val savedTemplate = if (isNewTemplate) {
            CoverHtmlTemplateConfig.Template(
                id = CoverHtmlTemplateConfig.generateId(),
                name = name.ifEmpty { "未命名模板" },
                htmlCode = htmlCode,
                isSelected = false
            )
        } else {
            template?.copy(
                name = name.ifEmpty { "未命名模板" },
                htmlCode = htmlCode
            ) ?: return
        }

        if (isNewTemplate) {
            CoverHtmlTemplateConfig.addTemplate(savedTemplate)
        } else {
            CoverHtmlTemplateConfig.updateTemplate(savedTemplate)
        }
        CoverImageView.clearHtmlCoverCache()

        (parentFragment as? CoverHtmlTemplateListDialog)?.refreshList()
    }

}
