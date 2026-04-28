package io.legado.app.ui.rss.source.edit

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.gson.Gson
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogContentEditBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

class RssSourceJsonEditDialog(
    private val sourceJson: String,
    private val onSave: (String) -> Unit
) : BaseDialogFragment(R.layout.dialog_content_edit) {

    val binding by viewBinding(DialogContentEditBinding::bind)

    private var searchKeyword: String = ""
    private var currentIndex: Int = -1
    private var matchPositions: MutableList<Int> = mutableListOf()
    private var originalContent: SpannableString? = null
    private var formattedJson: String = ""

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.title = getString(R.string.edit_content)
        initMenu()
        initSearchPanel()
        loadJson()
    }

    private fun loadJson() {
        formattedJson = try {
            val jsonElement = JsonParser.parseString(sourceJson)
            Gson().toJson(jsonElement)
        } catch (e: Exception) {
            sourceJson
        }
        binding.contentView.setText(formattedJson)
        originalContent = SpannableString(formattedJson)
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.content_edit)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_search -> toggleSearchPanel()
                R.id.menu_save -> {
                    val content = originalContent?.toString()
                        ?: binding.contentView.text?.toString()
                        ?: return@setOnMenuItemClickListener true
                    try {
                        JsonParser.parseString(content)
                        onSave(content)
                        dismiss()
                    } catch (e: Exception) {
                        alert(R.string.error) {
                            setMessage("${getString(R.string.json_format)}\n${e.message}")
                            positiveButton(R.string.confirm)
                        }
                    }
                }
                R.id.menu_reset -> {
                    loadJson()
                }
                R.id.menu_copy_all -> {
                    android.content.ClipboardManager::class.java.let { _ ->
                        val text = binding.contentView.text?.toString() ?: ""
                        val clipboard = requireContext()
                            .getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("json", text)
                        clipboard.setPrimaryClip(clip)
                    }
                }
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun toggleSearchPanel() {
        if (binding.searchPanel.isVisible) {
            binding.searchPanel.visibility = View.GONE
            clearSearchHighlight()
        } else {
            binding.searchPanel.visibility = View.VISIBLE
            binding.etSearch.requestFocus()
            if (searchKeyword.isNotEmpty()) {
                binding.etSearch.setText(searchKeyword)
            }
        }
    }

    private fun initSearchPanel() {
        binding.etSearch.addTextChangedListener { text ->
            searchKeyword = text?.toString() ?: ""
            performSearch()
        }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
        binding.btnCloseSearch.setOnClickListener {
            binding.searchPanel.visibility = View.GONE
            clearSearchHighlight()
        }
        binding.btnPrev.setOnClickListener {
            navigateToMatch(-1)
        }
        binding.btnNext.setOnClickListener {
            navigateToMatch(1)
        }
    }

    private fun performSearch() {
        if (searchKeyword.isEmpty()) {
            clearSearchHighlight()
            updateSearchResultText()
            return
        }
        val content = binding.contentView.text?.toString() ?: return
        matchPositions.clear()
        var startIndex = 0
        while (true) {
            val index = content.indexOf(searchKeyword, startIndex, true)
            if (index == -1) break
            matchPositions.add(index)
            startIndex = index + 1
        }
        if (matchPositions.isNotEmpty()) {
            currentIndex = 0
            highlightMatches()
            scrollToMatch(0)
        } else {
            currentIndex = -1
            clearSearchHighlight()
        }
        updateSearchResultText()
    }

    private fun highlightMatches() {
        val content = binding.contentView.text?.toString() ?: return
        if (originalContent == null) {
            originalContent = SpannableString(content)
        }
        val spannable = SpannableString(content)
        matchPositions.forEach { pos ->
            spannable.setSpan(
                BackgroundColorSpan(0xFFFFFF00.toInt()),
                pos,
                pos + searchKeyword.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (currentIndex >= 0 && currentIndex < matchPositions.size) {
            val currentPos = matchPositions[currentIndex]
            spannable.setSpan(
                BackgroundColorSpan(0xFF00FFFF.toInt()),
                currentPos,
                currentPos + searchKeyword.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.contentView.setText(spannable)
    }

    private fun clearSearchHighlight() {
        originalContent?.let {
            binding.contentView.setText(it)
        }
        matchPositions.clear()
        currentIndex = -1
    }

    private fun navigateToMatch(direction: Int) {
        if (matchPositions.isEmpty()) return
        currentIndex = (currentIndex + direction + matchPositions.size) % matchPositions.size
        highlightMatches()
        scrollToMatch(currentIndex)
        updateSearchResultText()
    }

    private fun scrollToMatch(index: Int) {
        if (index < 0 || index >= matchPositions.size) return
        val pos = matchPositions[index]
        binding.contentView.post {
            val layout = binding.contentView.layout ?: return@post
            val line = layout.getLineForOffset(pos)
            val lineHeight = layout.getLineTop(line)
            binding.contentView.scrollTo(0, lineHeight - binding.contentView.height / 3)
        }
    }

    private fun updateSearchResultText() {
        if (matchPositions.isEmpty()) {
            binding.tvSearchResult.text = if (searchKeyword.isEmpty()) "" else "0"
        } else {
            binding.tvSearchResult.text = "${currentIndex + 1}/${matchPositions.size}"
        }
    }
}
