package io.legado.app.ui.widget.dialog

import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.repository.debug.DebugEventCenter
import io.legado.app.databinding.DialogTextViewBinding
import io.legado.app.help.CacheManager
import io.legado.app.help.HelpDocManager
import io.legado.app.help.IntentData
import io.legado.app.lib.theme.isDarkTheme
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.debug.DebugCategory
import io.legado.app.model.debug.DebugEvent
import io.legado.app.model.debug.DebugLevel
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.constant.Theme
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.setHtml
import io.legado.app.utils.setLayout
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.help.InnerBrowserLinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文本弹窗，支持显示Markdown、HTML、普通文本
 */
class TextDialog() : BaseDialogFragment(R.layout.dialog_text_view) {
    
    private suspend fun logDebug(msg: String, detail: String? = null) {
        android.util.Log.d("TextDialog", msg)
        DebugEventCenter.emit(
            DebugEvent(
                level = DebugLevel.DEBUG,
                category = DebugCategory.APP,
                message = msg,
                detail = detail,
                dialogName = "TextDialog"
            )
        )
    }
    
    private fun logDebugSync(msg: String, detail: String? = null) {
        android.util.Log.d("TextDialog", msg)
        lifecycleScope.launch {
            DebugEventCenter.emit(
                DebugEvent(
                    level = DebugLevel.DEBUG,
                    category = DebugCategory.APP,
                    message = msg,
                    detail = detail,
                    dialogName = "TextDialog"
                )
            )
        }
    }

    // 显示模式枚举
    enum class Mode {
        MD, HTML, TEXT
    }

    // 普通文本弹窗构造函数
    constructor(
        title: String,
        content: String?,
        mode: Mode = Mode.TEXT,
        time: Long = 0,
        autoClose: Boolean = false
    ) : this() {
        arguments = Bundle().apply {
            putString("title", title)
            putString("content", IntentData.put(content))
            putString("mode", mode.name)
            putLong("time", time)
        }
        isCancelable = false
        this.autoClose = autoClose
    }

    // 帮助文档弹窗构造函数
    constructor(
        title: String,
        content: String?,
        mode: Mode = Mode.TEXT,
        helpDocName: String? = null,
        scrollToLine: Int = 0,
        highlightTerm: String? = null
    ) : this() {
        arguments = Bundle().apply {
            putString("title", title)
            putString("content", IntentData.put(content))
            putString("mode", mode.name)
            putString("helpDocName", helpDocName)
            putInt("scrollToLine", scrollToLine)
            putString("highlightTerm", highlightTerm)
        }
        isHelpMode = helpDocName != null
        currentHelpDoc = helpDocName
    }

    private val binding by viewBinding(DialogTextViewBinding::bind)
    private var time = 0L // 自动关闭倒计时
    private var autoClose: Boolean = false // 倒计时结束后是否自动关闭
    private var isHelpMode: Boolean = false // 是否为帮助文档模式
    private var currentHelpDoc: String? = null // 当前帮助文档文件名
    // 追踪当前显示的内容，切换帮助文档时同步更新，确保打开编辑器时获取的是最新内容
    private var currentContent: String? = null
    private var markwon: Markwon? = null // Markdown渲染器
    private var helpDocPopup: PopupWindow? = null

    companion object {
        private const val TAG = "TextDialog"
    }

    override fun onStart() {
        // 设置弹窗大小为屏幕宽度的MATCH_PARENT，高度为90%
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 设置工具栏颜色
        binding.toolBar.setBackgroundColor(primaryColor)
        // 根据主题动态选择标题颜色：isDarkTheme=true表示浅色背景→用黑色，isDarkTheme=false表示深色背景→用白色
        val titleColor = if (isDarkTheme) Color.BLACK else Color.WHITE
        binding.toolBar.setTitleTextColor(titleColor)
        binding.toolBar.setSubtitleTextColor(titleColor)
        // 加载菜单
        binding.toolBar.inflateMenu(R.menu.dialog_text)
        // 应用菜单着色：根据主题动态选择
        val menuTheme = if (isDarkTheme) Theme.Light else Theme.Dark
        binding.toolBar.menu.applyTint(requireContext(), menuTheme)
        
        // 处理传递的参数
        arguments?.let {
            val title = it.getString("title")
            binding.toolBar.title = title
            binding.toolBar.post { tintToolbarTextAndIcons() }
            val content = IntentData.get(it.getString("content")) ?: ""
            currentContent = content
            val mode = it.getString("mode")
            val scrollToLine = it.getInt("scrollToLine", 0)
            val highlightTerm = it.getString("highlightTerm")
            // 从 arguments 恢复帮助文档模式相关变量
            val helpDocName = it.getString("helpDocName")
            isHelpMode = helpDocName != null
            currentHelpDoc = helpDocName
            when (mode) {
                Mode.MD.name -> viewLifecycleOwner.lifecycleScope.launch {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        binding.textView.setTextClassifier(TextClassifier.NO_OP)
                    }
                    markwon = Markwon.builder(requireContext())
                        .usePlugin(object : io.noties.markwon.AbstractMarkwonPlugin() {
                            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                                builder.linkResolver(InnerBrowserLinkResolver)
                            }
                        })
                        .usePlugin(GlideImagesPlugin.create(Glide.with(requireContext())))
                        .usePlugin(HtmlPlugin.create())
                        .usePlugin(TablePlugin.create(requireContext()))
                        .build()
                    val markdown = withContext(IO) {
                        markwon!!.toMarkdown(content)
                    }
                    binding.textView.setMarkdown(
                        markwon!!,
                        markdown,
                        imgOnLongClickListener = { source  ->
                            showDialogFragment(PhotoDialog(source))
                        }
                    )
                    if (scrollToLine > 0) {
                        val totalLinesInOriginal = content.lineSequence().count()
                        binding.textView.postDelayed({
                            scrollToLineInText(binding.textView, scrollToLine, highlightTerm, null, totalLinesInOriginal)
                        }, 200)
                    }
                }

                Mode.HTML.name -> binding.textView.setHtml(content)
                else -> {
                    if (content.length >= 32 * 1024) {
                        val truncatedContent =
                            content.take(32 * 1024) + "\n\n数据太大，无法全部显示…"
                        binding.textView.text = truncatedContent
                    } else {
                        binding.textView.text = content
                    }
                    if (scrollToLine > 0) {
                        val totalLinesInOriginal = content.lineSequence().count()
                        binding.textView.postDelayed({
                            scrollToLineInText(binding.textView, scrollToLine, highlightTerm, null, totalLinesInOriginal)
                        }, 200)
                    }
                }
            }
            time = it.getLong("time", 0L)
        }
        
        // 根据文档类型控制放大镜按钮的可见性（必须在恢复 isHelpMode 和 currentHelpDoc 之后调用）
        updateSearchButtonVisibility()
        
        binding.toolBar.setOnMenuItemClickListener { menu ->
            when (menu.itemId) {
                R.id.menu_close -> dismissAllowingStateLoss()
                R.id.menu_fullscreen_edit -> {
                    currentContent?.let { content ->
                        val cacheKey = "code_text_${System.currentTimeMillis()}"
                        CacheManager.putMemory(cacheKey, content)
                        startActivity<CodeEditActivity> {
                            putExtra("cacheKey", cacheKey)
                            putExtra("title", binding.toolBar.title)
                            putExtra("languageName", "text.html.markdown")
                        }
                    }
                }
                R.id.menu_search_help -> {
                    showDialogFragment(HelpSearchDialog())
                }
            }
            true
        }
        // 设置倒计时显示
        if (time > 0) {
            // 显示倒计时徽章
            binding.badgeView.setBadgeCount((time / 1000).toInt())
            lifecycleScope.launch {
                while (time > 0) {
                    delay(1000)
                    time -= 1000
                    binding.badgeView.setBadgeCount((time / 1000).toInt())
                    if (time <= 0) {
                        view.post {
                            dialog?.setCancelable(true)
                            if (autoClose) dialog?.cancel()
                        }
                    }
                }
            }
        } else {
            // 无倒计时，允许关闭弹窗
            view.post {
                dialog?.setCancelable(true)
            }
        }
        
        // 初始化帮助文档选择器
        setupHelpSelector()
        
        // 监听帮助文档搜索结果
        setupHelpSearchResultListener()
    }

    private fun tintToolbarTextAndIcons() {
        // 根据主题动态选择着色颜色：isDarkTheme=true表示浅色背景→用黑色，isDarkTheme=false表示深色背景→用白色
        val tintColor = if (isDarkTheme) Color.BLACK else Color.WHITE
        fun tintView(view: View) {
            when (view) {
                is TextView -> view.setTextColor(tintColor)
                is ImageButton -> view.setColorFilter(tintColor)
                is ViewGroup -> {
                    for (index in 0 until view.childCount) {
                        tintView(view.getChildAt(index))
                    }
                }
            }
        }
        tintView(binding.toolBar)
    }
    
    /**
     * 监听帮助文档搜索结果
     * 
     * 使用 Fragment Result API 接收 HelpSearchDialog 返回的结果，
     * 而不是创建新的 TextDialog，这样可以：
     * 1. 避免 Dialog 无限叠加（原来的问题：TextDialog → HelpSearchDialog → TextDialog → ...）
     * 2. 复用当前的 TextDialog，更新内容即可
     * 3. 保持返回栈清晰，用户按返回键时不会需要多次点击
     */
    private fun setupHelpSearchResultListener() {
        // 使用 childFragmentManager，因为 HelpSearchDialog 是通过 childFragmentManager 显示的
        childFragmentManager.setFragmentResultListener(HelpSearchDialog.REQUEST_KEY, this) { _, bundle ->
            logDebugSync("setupHelpSearchResultListener - result received")
            // 从 Bundle 中解析搜索结果
            val docName = bundle.getString(HelpSearchDialog.RESULT_DOC_NAME) ?: return@setFragmentResultListener
            val fileName = bundle.getString(HelpSearchDialog.RESULT_FILE_NAME) ?: return@setFragmentResultListener
            val content = bundle.getString(HelpSearchDialog.RESULT_CONTENT) ?: return@setFragmentResultListener
            val lineNumber = bundle.getInt(HelpSearchDialog.RESULT_LINE_NUMBER, 0)
            val highlightTerm = bundle.getString(HelpSearchDialog.RESULT_HIGHLIGHT_TERM)
            val lineContent = bundle.getString(HelpSearchDialog.RESULT_LINE_CONTENT)
            
            logDebugSync("setupHelpSearchResultListener - docName: $docName, lineNumber: $lineNumber, lineContent: \"$lineContent\"")
            
            // 更新当前文档信息
            currentHelpDoc = fileName
            currentContent = content
            binding.toolBar.title = docName
            
            // 同步更新文档选择器的选中项（下拉列表）
            val docIndex = HelpDocManager.getDocIndex(fileName)
            if (docIndex >= 0) {
                updateHelpSpinnerText(fileName)
            }
            
            // 更新内容显示并滚动到搜索结果所在行
            updateContentWithScroll(content, lineNumber, highlightTerm, lineContent)
        }
    }
    
    /**
     * 更新内容并滚动到指定行
     *
     * 用于在用户从搜索结果中选择一项后，更新 TextDialog 显示的内容，
     * 并自动滚动到匹配的行号位置。
     *
     * @param content 要显示的文档内容
     * @param scrollToLine 要滚动到的行号（1-based，原始 Markdown 文本的行号）
     * @param highlightTerm 要高亮的关键词（目前仅用于滚动定位，TextView 不支持文本高亮选区）
     * @param lineContent 匹配行的完整内容，用于在渲染后的文本中精确定位
     */
    private fun updateContentWithScroll(content: String, scrollToLine: Int, highlightTerm: String?, lineContent: String?) {
        logDebugSync("updateContentWithScroll called - scrollToLine: $scrollToLine, lineContent: \"$lineContent\"")
        currentContent = content
        val totalLinesInOriginal = content.lineSequence().count()
        logDebugSync("updateContentWithScroll - totalLinesInOriginal: $totalLinesInOriginal")
        
        viewLifecycleOwner.lifecycleScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                binding.textView.setTextClassifier(TextClassifier.NO_OP)
            }
            // 确保 markwon 已初始化
            if (markwon == null) {
                markwon = Markwon.builder(requireContext())
                    .usePlugin(object : io.noties.markwon.AbstractMarkwonPlugin() {
                        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                            builder.linkResolver(InnerBrowserLinkResolver)
                        }
                    })
                    .usePlugin(GlideImagesPlugin.create(Glide.with(requireContext())))
                    .usePlugin(HtmlPlugin.create())
                    .usePlugin(TablePlugin.create(requireContext()))
                    .build()
            }
            val markdown = withContext(IO) {
                markwon!!.toMarkdown(content)
            }
            logDebug("updateContentWithScroll - setting markdown, length: ${markdown.length}")
            binding.textView.setMarkdown(
                markwon!!,
                markdown,
                imgOnLongClickListener = { source ->
                    showDialogFragment(PhotoDialog(source))
                }
            )
            // 滚动到指定位置
            if (scrollToLine > 0) {
                logDebug("updateContentWithScroll - posting scrollToLineInText")
                binding.textView.postDelayed({
                    logDebugSync("updateContentWithScroll - scrollToLineInText called from postDelayed")
                    scrollToLineInText(binding.textView, scrollToLine, highlightTerm, lineContent, totalLinesInOriginal)
                }, 200)
            }
        }
    }
    
    /**
     * 滚动到指定行并高亮关键词
     * 
     * 优先使用 lineContent 在渲染后的文本中查找匹配位置，这样可以正确处理
     * Markdown 渲染后文本内容变化的情况。
     * 
     * 如果 lineContent 为空或在渲染后文本中找不到，则回退到使用行号计算。
     * 
     * @param textView 目标 TextView
     * @param lineNumber 原始 Markdown 文本的行号（1-based）
     * @param highlightTerm 要高亮的关键词
     * @param lineContent 匹配行的完整内容，用于精确定位
     * @param retryCount 重试次数，用于处理 layout 未就绪的情况
     */
    private fun scrollToLineInText(
        textView: android.widget.TextView,
        lineNumber: Int,
        highlightTerm: String?,
        lineContent: String?,
        totalLinesInOriginal: Int = 0,
        retryCount: Int = 0
    ) {
        val layout = textView.layout

        val logMsg = "scrollToLineInText - retryCount: $retryCount, lineNumber: $lineNumber, totalLinesInOriginal: $totalLinesInOriginal"
        logDebugSync(logMsg)

        // 如果 layout 未就绪，延迟重试（最多重试 20 次，每次间隔 100ms）
        if (layout == null) {
            logDebugSync("scrollToLineInText - layout is null, retrying...")
            if (retryCount < 20) {
                textView.postDelayed({
                    scrollToLineInText(textView, lineNumber, highlightTerm, lineContent, totalLinesInOriginal, retryCount + 1)
                }, 100)
            }
            return
        }

        // 如果是 ScrollTextView，检查是否可以滚动
        if (textView is io.legado.app.ui.widget.text.ScrollTextView) {
            logDebugSync("scrollToLineInText - canScroll: ${textView.canScroll()}, maxScrollOffset: ${textView.getMaxScrollOffset()}")
            if (!textView.canScroll()) {
                if (retryCount < 20) {
                    textView.postDelayed({
                        scrollToLineInText(textView, lineNumber, highlightTerm, lineContent, totalLinesInOriginal, retryCount + 1)
                    }, 100)
                }
                return
            }
        }

        // 简单的方法：按百分比滚动
        val totalLinesInView = layout.lineCount
        val scrollPercent = if (totalLinesInOriginal > 0) {
            lineNumber.toFloat() / totalLinesInOriginal.toFloat()
        } else {
            0.5f
        }
        logDebugSync("scrollToLineInText - totalLinesInView: $totalLinesInView, scrollPercent: $scrollPercent")

        val targetLine = (scrollPercent * totalLinesInView).toInt().coerceIn(0, totalLinesInView - 1)
        val y = layout.getLineTop(targetLine)
        val targetScrollY = (y - textView.height / 4).coerceAtLeast(0)
        logDebugSync("scrollToLineInText - targetLine: $targetLine, y: $y, targetScrollY: $targetScrollY, textView.height: ${textView.height}")

        textView.scrollTo(0, targetScrollY)
        logDebugSync("scrollToLineInText - scrollTo called, current scrollY: ${textView.scrollY}")
    }
    
    /**
     * 更新放大镜按钮的可见性
     * 
     * 规则：
     * 1. 非帮助文档模式：显示放大镜按钮
     * 2. 帮助文档模式且是隐藏文档：隐藏放大镜按钮
     * 3. 帮助文档模式且是公开文档：显示放大镜按钮
     */
    private fun updateSearchButtonVisibility() {
        val searchMenuItem = binding.toolBar.menu.findItem(R.id.menu_search_help)
        
        if (!isHelpMode) {
            // 非帮助文档模式，显示放大镜按钮
            searchMenuItem?.isVisible = true
        } else {
            // 帮助文档模式，根据是否是隐藏文档决定
            val isHidden = currentHelpDoc?.let { HelpDocManager.isHiddenDoc(it) } ?: false
            searchMenuItem?.isVisible = !isHidden
        }
    }
    
    /**
     * 初始化帮助文档选择器
     * 仅在帮助模式下显示下拉列表供用户切换不同的帮助文档
     */
    private fun setupHelpSelector() {
        if (!isHelpMode) {
            binding.helpSelectorLayout.visibility = View.GONE
            return
        }
        
        // 检查当前选中的文档是否存在,不存在则隐藏选择器
        val docIndex = HelpDocManager.getDocIndex(currentHelpDoc ?: "")
        if (docIndex < 0) {
            binding.helpSelectorLayout.visibility = View.GONE
            return
        }
        
        binding.helpSelectorLayout.visibility = View.VISIBLE
        
        // 创建帮助文档列表适配器
        updateHelpSpinnerText(currentHelpDoc)
        
        // 设置当前选中的文档
        binding.helpSpinner.setOnClickListener {
            showHelpDocPopup()
        }
    }

    private fun updateHelpSpinnerText(fileName: String?) {
        binding.helpSpinner.text = fileName
            ?.let { HelpDocManager.getDocByFileName(it)?.displayName }
            ?: ""
    }

    private fun showHelpDocPopup() {
        helpDocPopup?.dismiss()
        val docs = HelpDocManager.allHelpDocs
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner_dropdown,
            docs.map { it.displayName }
        )
        val listView = ListView(requireContext()).apply {
            this.adapter = adapter
            divider = null
            setOnItemClickListener { _, _, position, _ ->
                helpDocPopup?.dismiss()
                selectHelpDoc(docs[position])
            }
        }

        val rootLocation = IntArray(2)
        val spinnerLocation = IntArray(2)
        binding.root.getLocationOnScreen(rootLocation)
        binding.helpSpinner.getLocationOnScreen(spinnerLocation)

        val rootBottom = rootLocation[1] + binding.root.height
        val spinnerBottom = spinnerLocation[1] + binding.helpSpinner.height
        val availableHeight = rootBottom - spinnerBottom - 8.dpToPx()
        if (availableHeight <= 0) {
            return
        }

        val popupWidth = binding.helpSpinner.width
        val popupHeight = minOf(availableHeight, docs.size * 48.dpToPx()).coerceAtLeast(160.dpToPx())
        helpDocPopup = PopupWindow(listView, popupWidth, popupHeight, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            setOutsideTouchable(true)
            showAsDropDown(binding.helpSpinner)
        }
    }

    private fun selectHelpDoc(selectedDoc: io.legado.app.help.HelpDoc) {
        if (selectedDoc.fileName != currentHelpDoc) {
            currentHelpDoc = selectedDoc.fileName
            updateHelpSpinnerText(selectedDoc.fileName)
            loadHelpDoc(selectedDoc.fileName)
            updateSearchButtonVisibility()
        }
    }

    /**
     * 异步加载帮助文档内容
     */
    private fun loadHelpDoc(fileName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            // 在IO线程读取文档
            val content = withContext(IO) {
                HelpDocManager.loadDoc(requireContext().assets, fileName)
            }
            if (currentHelpDoc != fileName) {
                return@launch
            }
            updateContent(content)
        }
    }
    
    /**
     * 更新弹窗内容
     * 用于切换帮助文档时刷新显示
     */
    private fun updateContent(content: String) {
        // 同步更新当前内容变量，确保打开编辑器时获取到的是最新内容
        currentContent = content
        markwon?.let { mw ->
            viewLifecycleOwner.lifecycleScope.launch {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    binding.textView.setTextClassifier(TextClassifier.NO_OP)
                }
                // 在IO线程转换Markdown
                val markdown = withContext(IO) {
                    mw.toMarkdown(content)
                }
                // 渲染Markdown到TextView
                binding.textView.setMarkdown(
                    mw,
                    markdown,
                    imgOnLongClickListener = { source ->
                        showDialogFragment(PhotoDialog(source))
                    }
                )
            }
        }
    }

}
