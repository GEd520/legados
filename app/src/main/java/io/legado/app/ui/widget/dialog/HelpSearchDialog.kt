package io.legado.app.ui.widget.dialog

import android.content.res.AssetManager
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView.OnEditorActionListener
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogHelpSearchBinding
import io.legado.app.databinding.ItemHelpSearchResultBinding
import io.legado.app.help.HelpDocManager
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HelpSearchDialog : BaseDialogFragment(R.layout.dialog_help_search) {

    private val binding by viewBinding(DialogHelpSearchBinding::bind)

    private var searchJob: Job? = null

    private val allDocsContent = mutableMapOf<String, String>()
    private var isDocsLoaded = false
    private var currentSearchTerm = ""
    private var selectedDocFilter: String = "all"

    private val adapter by lazy { Adapter() }

    companion object {
        private const val DEBOUNCE_DELAY = 300L
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.title = getString(R.string.help_search)
        binding.toolBar.inflateMenu(R.menu.dialog_help_search)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_close -> {
                    dismissAllowingStateLoss()
                    true
                }
                else -> false
            }
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        setupScopeSpinner()
        setupSearchInput()

        loadDocsAsync()
    }

    private fun setupScopeSpinner() {
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf(getString(R.string.all_docs)) + HelpDocManager.allHelpDocs.map { it.displayName }
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.scopeSpinner.adapter = spinnerAdapter

        binding.scopeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedDocFilter = if (position == 0) "all" else {
                    HelpDocManager.allHelpDocs[position - 1].fileName
                }
                if (currentSearchTerm.isNotEmpty()) {
                    performSearch(currentSearchTerm)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupSearchInput() {
        binding.searchEditText.setOnEditorActionListener(OnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val query = binding.searchEditText.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchJob?.cancel()
                    performSearch(query)
                }
                true
            } else {
                false
            }
        })

        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchJob?.cancel()
                binding.clearBtn.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE

                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    showInitialState()
                    return
                }

                searchJob = lifecycleScope.launch {
                    delay(DEBOUNCE_DELAY)
                    currentSearchTerm = query
                    performSearch(query)
                }
            }
        })

        binding.clearBtn.setOnClickListener {
            binding.searchEditText.text.clear()
            binding.clearBtn.visibility = View.GONE
            showInitialState()
        }
    }

    private fun loadDocsAsync() {
        binding.loadingIndicator.visibility = View.VISIBLE
        lifecycleScope.launch {
            val assets = requireContext().assets
            val docs = withContext(IO) {
                val result = mutableMapOf<String, String>()
                for (doc in HelpDocManager.allHelpDocs) {
                    try {
                        result[doc.fileName] = loadDoc(assets, doc.fileName)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                result
            }
            allDocsContent.putAll(docs)
            isDocsLoaded = true
            binding.loadingIndicator.visibility = View.GONE

            if (currentSearchTerm.isNotEmpty()) {
                performSearch(currentSearchTerm)
            }
        }
    }

    private fun loadDoc(assets: AssetManager, fileName: String): String {
        return String(assets.open("web/help/md/${fileName}.md").readBytes())
    }

    private fun performSearch(query: String) {
        if (!isDocsLoaded) return

        lifecycleScope.launch {
            val results = withContext(IO) {
                searchAllDocs(query)
            }
            updateResults(results, query)
        }
    }

    private fun searchAllDocs(query: String): List<HelpSearchResultItem> {
        val results = mutableListOf<HelpSearchResultItem>()
        val queryLower = query.lowercase()
        val contextChars = 80

        val docsToSearch = if (selectedDocFilter == "all") {
            HelpDocManager.allHelpDocs
        } else {
            HelpDocManager.allHelpDocs.filter { it.fileName == selectedDocFilter }
        }

        for (doc in docsToSearch) {
            val content = allDocsContent[doc.fileName] ?: continue
            val lines = content.lineSequence().toList()

            for ((lineIndex, line) in lines.withIndex()) {
                if (line.lowercase().contains(queryLower)) {
                    val lineNum = lineIndex + 1
                    val matchIndex = line.lowercase().indexOf(queryLower)
                    val start = maxOf(0, matchIndex - contextChars)
                    val end = minOf(line.length, matchIndex + query.length + contextChars)
                    val contextText = buildString {
                        if (start > 0) append("...")
                        append(line.substring(start, end))
                        if (end < line.length) append("...")
                    }

                    results.add(HelpSearchResultItem(
                        docName = doc.displayName,
                        fileName = doc.fileName,
                        lineNumber = lineNum,
                        matchedText = contextText,
                        searchTerm = query
                    ))
                }
            }
        }

        return results.take(100)
    }

    private fun updateResults(results: List<HelpSearchResultItem>, query: String) {
        if (results.isEmpty()) {
            showEmptyState()
        } else {
            showResultsState(results, query)
        }
    }

    private fun showInitialState() {
        binding.recyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.initialStateLayout.visibility = View.VISIBLE
    }

    private fun showEmptyState() {
        binding.recyclerView.visibility = View.GONE
        binding.initialStateLayout.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
    }

    private fun showResultsState(results: List<HelpSearchResultItem>, query: String) {
        binding.initialStateLayout.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.resultCountText.visibility = View.VISIBLE

        binding.resultCountText.text = getString(R.string.search_result_count, results.size)

        adapter.setItems(results)
        binding.recyclerView.scrollToPosition(0)
    }

    private inner class Adapter : RecyclerAdapter<HelpSearchResultItem, ItemHelpSearchResultBinding>(requireContext()) {

        override fun getViewBinding(parent: ViewGroup): ItemHelpSearchResultBinding {
            return ItemHelpSearchResultBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemHelpSearchResultBinding,
            item: HelpSearchResultItem,
            payloads: MutableList<Any>
        ) {
            binding.docNameText.text = item.docName
            binding.lineNumberText.text = getString(R.string.line_number, item.lineNumber)
            binding.matchedTextText.text = highlightText(item.matchedText, item.searchTerm)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemHelpSearchResultBinding) {
            binding.root.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                    dismissAllowingStateLoss()
                    showDialogFragment(TextDialog(
                        item.docName,
                        allDocsContent[item.fileName],
                        TextDialog.Mode.MD,
                        item.fileName
                    ))
                }
            }
        }

        private fun highlightText(text: String, searchTerm: String): SpannableString {
            val spannable = SpannableString(text)
            val termLower = searchTerm.lowercase()
            val textLower = text.lowercase()
            var startIndex = 0
            val highlightColor = ContextCompat.getColor(requireContext(), R.color.accent)
            val bgColor = android.graphics.Color.argb(60, android.graphics.Color.red(highlightColor),
                android.graphics.Color.green(highlightColor), android.graphics.Color.blue(highlightColor))

            while (true) {
                val index = textLower.indexOf(termLower, startIndex)
                if (index == -1) break
                spannable.setSpan(
                    BackgroundColorSpan(bgColor),
                    index,
                    index + searchTerm.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIndex = index + searchTerm.length
            }
            return spannable
        }
    }
}

data class HelpSearchResultItem(
    val docName: String,
    val fileName: String,
    val lineNumber: Int,
    val matchedText: String,
    val searchTerm: String
)
