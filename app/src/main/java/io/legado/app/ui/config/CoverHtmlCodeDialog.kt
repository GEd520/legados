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
import io.legado.app.constant.EventBus
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.ui.widget.code.addHtmlPattern
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.utils.GSON
import io.legado.app.utils.postEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
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
        initToolBar()
        initWebView()
        initCodeView()
        loadTemplateData()
        initClickListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshIfTemplateChanged()
    }

    override fun onDestroyView() {
        binding.webViewPreview.stopLoading()
        binding.webViewPreview.destroy()
        super.onDestroyView()
    }

    /**
     * 解析传入参数，确定当前编辑的模板
     * 
     * 若从模板列表传入模板则编辑该模板；
     * 若无传入（从封面配置页直接进入）则加载当前选中模板进行编辑，
     * 保存时直接更新该模板而非创建新模板
     */
    private fun parseArguments() {
        val templateJson = arguments?.getString(KEY_TEMPLATE)
        if (templateJson != null) {
            template = GSON.fromJson(templateJson, CoverHtmlTemplateConfig.Template::class.java)
        }
        if (template == null) {
            template = CoverHtmlTemplateConfig.getSelectedTemplate()
        }
        isNewTemplate = false
    }

    /**
     * 初始化工具栏菜单
     * 
     * 加载模板切换按钮，点击后弹出模板列表对话框，
     * 用户可在列表中切换当前使用的模板
     */
    private fun initToolBar() {
        binding.toolBar.inflateMenu(R.menu.cover_html_code)
        binding.toolBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_template -> {
                    showDialogFragment(CoverHtmlTemplateListDialog())
                    true
                }
                else -> false
            }
        }
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
     * 加载模板数据到界面
     * 
     * 将当前编辑模板的名称和HTML代码填充到输入框，
     * 设置默认预览参数并触发首次预览
     */
    private fun loadTemplateData() {
        lifecycleScope.launch {
            val currentTemplate = template ?: return@launch
            binding.editTemplateName.setText(currentTemplate.name)
            binding.codeView.setText(currentTemplate.htmlCode)
            binding.editBookName.setText("示例书名")
            binding.editAuthor.setText("示例作者")
            binding.webViewPreview.post {
                previewCover()
            }
        }
    }

    /**
     * 检测模板是否在模板列表中被切换
     * 
     * 从模板列表对话框返回后，若当前选中模板与编辑中的模板ID不同，
     * 则自动加载新选中模板的内容并刷新预览
     */
    private fun refreshIfTemplateChanged() {
        val selected = CoverHtmlTemplateConfig.getSelectedTemplate()
        val current = template
        if (current != null && selected.id != current.id) {
            template = selected
            binding.editTemplateName.setText(selected.name)
            binding.codeView.setText(selected.htmlCode)
            previewCover()
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
     * 
     * 校验HTML代码不为空，将编辑内容更新到当前模板并持久化，
     * 同时清除封面缓存使书架上的封面重新生成
     */
    private fun saveTemplate() {
        val name = binding.editTemplateName.text?.toString()?.trim() ?: ""
        val htmlCode = binding.codeView.text?.toString()?.trim() ?: ""

        if (htmlCode.isBlank()) {
            context?.toastOnUi(R.string.cover_html_code_empty)
            return
        }

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
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

}
