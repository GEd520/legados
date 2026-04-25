package io.legado.app.ui.widget.dialog

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogTextViewBinding
import io.legado.app.help.CacheManager
import io.legado.app.help.HelpDocManager
import io.legado.app.help.IntentData
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.utils.applyTint
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

    enum class Mode {
        MD, HTML, TEXT
    }

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

    constructor(
        title: String,
        content: String?,
        mode: Mode = Mode.TEXT,
        helpDocName: String? = null
    ) : this() {
        arguments = Bundle().apply {
            putString("title", title)
            putString("content", IntentData.put(content))
            putString("mode", mode.name)
            putString("helpDocName", helpDocName)
        }
        isHelpMode = helpDocName != null
        currentHelpDoc = helpDocName
    }

    private val binding by viewBinding(DialogTextViewBinding::bind)
    private var time = 0L
    private var autoClose: Boolean = false
    private var isHelpMode: Boolean = false
    private var currentHelpDoc: String? = null
    private var markwon: Markwon? = null

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.inflateMenu(R.menu.dialog_text)
        binding.toolBar.menu.applyTint(requireContext())
        arguments?.let {
            val title = it.getString("title")
            binding.toolBar.title = title
            val content = IntentData.get(it.getString("content")) ?: ""
            val mode = it.getString("mode")
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
                }
            }
            binding.toolBar.setOnMenuItemClickListener { menu ->
                when (menu.itemId) {
                    R.id.menu_close -> dismissAllowingStateLoss()
                    R.id.menu_fullscreen_edit -> {
                        val cacheKey = "code_text_${System.currentTimeMillis()}"
                        CacheManager.putMemory(cacheKey, content)
                        startActivity<CodeEditActivity> {
                            putExtra("cacheKey", cacheKey)
                            putExtra("title", title)
                            putExtra("languageName", if (mode == Mode.MD.name) "text.html.markdown" else "text.html.basic")
                        }
                    }
                }
                true
            }
            time = it.getLong("time", 0L)
        }
        if (time > 0) {
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
            view.post {
                dialog?.setCancelable(true)
            }
        }
        
        setupHelpSelector()
    }
    
    private fun setupHelpSelector() {
        if (!isHelpMode) {
            binding.helpSelectorLayout.visibility = View.GONE
            return
        }
        
        binding.helpSelectorLayout.visibility = View.VISIBLE
        
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            HelpDocManager.allHelpDocs.map { it.displayName }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.helpSpinner.adapter = adapter
        
        currentHelpDoc?.let { docName ->
            val index = HelpDocManager.getDocIndex(docName)
            if (index >= 0) {
                binding.helpSpinner.setSelection(index, false)
            }
        }
        
        binding.helpSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedDoc = HelpDocManager.allHelpDocs[position]
                if (selectedDoc.fileName != currentHelpDoc) {
                    loadHelpDoc(selectedDoc.fileName)
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }
    
    private fun loadHelpDoc(fileName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val content = withContext(IO) {
                HelpDocManager.loadDoc(requireContext().assets, fileName)
            }
            currentHelpDoc = fileName
            updateContent(content)
        }
    }
    
    private fun updateContent(content: String) {
        markwon?.let { mw ->
            viewLifecycleOwner.lifecycleScope.launch {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    binding.textView.setTextClassifier(TextClassifier.NO_OP)
                }
                val markdown = withContext(IO) {
                    mw.toMarkdown(content)
                }
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
