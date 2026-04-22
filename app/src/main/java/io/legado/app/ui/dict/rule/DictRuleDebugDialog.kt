package io.legado.app.ui.dict.rule

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.DictRule
import io.legado.app.databinding.DialogDictRuleDebugBinding
import io.legado.app.help.config.DictDebugConfig
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.currentCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext

class DictRuleDebugDialog() : BaseDialogFragment(R.layout.dialog_dict_rule_debug) {

    private val binding by viewBinding(DialogDictRuleDebugBinding::bind)
    private var dictRule: DictRule? = null
    private var urlSrc: String = ""
    private var resultSrc: String = ""

    constructor(dictRule: DictRule) : this() {
        arguments = Bundle().apply {
            putString("name", dictRule.name)
            putString("urlRule", dictRule.urlRule)
            putString("showRule", dictRule.showRule)
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        dictRule = DictRule().apply {
            name = arguments?.getString("name") ?: ""
            urlRule = arguments?.getString("urlRule") ?: ""
            showRule = arguments?.getString("showRule") ?: ""
        }
        
        initToolBar()
        initInputView()
    }

    private fun initToolBar() {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.inflateMenu(R.menu.dict_rule_debug)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_url_src -> {
                    if (urlSrc.isNotEmpty()) {
                        showDialogFragment(TextDialog("URL源码", urlSrc))
                    } else {
                        toastOnUi("请先执行搜索")
                    }
                }
                R.id.menu_result_src -> {
                    if (resultSrc.isNotEmpty()) {
                        showDialogFragment(TextDialog("结果源码", resultSrc))
                    } else {
                        toastOnUi("请先执行搜索")
                    }
                }
            }
            true
        }
    }

    private fun initInputView() {
        val history = DictDebugConfig.getSearchHistory()
        binding.inputView.setFilterValues(history)

        binding.inputView.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.btnSearch.setOnClickListener {
            performSearch()
        }
    }

    private fun performSearch() {
        val word = binding.inputView.text.toString()
        val rule = dictRule ?: return
        
        if (word.isBlank()) {
            toastOnUi("关键词不能为空")
            return
        }
        
        DictDebugConfig.addSearchHistory(word)
        binding.viewResult.text = "正在搜索..."
        
        execute {
            val analyzeUrl = AnalyzeUrl(
                rule.urlRule,
                key = word,
                coroutineContext = currentCoroutineContext()
            )
            val response = analyzeUrl.getStrResponseAwait()
            val body = response.body ?: ""
            val result = if (rule.showRule.isBlank()) {
                body
            } else {
                val analyzeRule = AnalyzeRule().setCoroutineContext(currentCoroutineContext())
                analyzeRule.setRuleName(rule.name)
                analyzeRule.getString(rule.showRule, mContent = body) ?: body
            }
            result to body
        }.onSuccess {
            urlSrc = it.second
            resultSrc = it.first
            binding.viewResult.text = resultSrc
        }.onError {
            toastOnUi("调试失败: ${it.localizedMessage}")
        }
    }

}
