package io.legado.app.ui.widget.dialog

import android.content.res.AssetManager
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogHelpSearchBinding
import io.legado.app.databinding.ItemHelpSearchHeaderBinding
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

    private val expandedGroups = mutableSetOf<String>()

    private val adapter by lazy { SearchAdapter() }

    companion object {
        private const val DEBOUNCE_DELAY = 300L
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_RESULT = 1
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

        setupSearchInput()

        loadDocsAsync()
    }

    private fun setupSearchInput() {
        binding.searchEditText.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, event ->
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
            updateResults(results)
        }
    }

    private fun searchAllDocs(query: String): List<DocSearchResult> {
        val results = mutableListOf<DocSearchResult>()
        val queryLower = query.lowercase()
        val contextChars = 80

        for (doc in HelpDocManager.allHelpDocs) {
            val content = allDocsContent[doc.fileName] ?: continue
            val lines = content.lineSequence().toList()
            val matchedLines = mutableListOf<SearchResultItem>()

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

                    matchedLines.add(SearchResultItem(
                        lineNumber = lineNum,
                        matchedText = contextText,
                        searchTerm = query
                    ))
                }
            }

            if (matchedLines.isNotEmpty()) {
                results.add(DocSearchResult(
                    docName = doc.displayName,
                    fileName = doc.fileName,
                    items = matchedLines
                ))
            }
        }

        return results
    }

    private fun updateResults(results: List<DocSearchResult>) {
        if (results.isEmpty()) {
            showEmptyState()
        } else {
            showResultsState(results)
        }
    }

    private fun showInitialState() {
        binding.recyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.resultCountText.visibility = View.GONE
        binding.initialStateLayout.visibility = View.VISIBLE
    }

    private fun showEmptyState() {
        binding.recyclerView.visibility = View.GONE
        binding.initialStateLayout.visibility = View.GONE
        binding.resultCountText.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
    }

    private fun showResultsState(results: List<DocSearchResult>) {
        binding.initialStateLayout.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.resultCountText.visibility = View.VISIBLE

        val totalCount = results.sumOf { it.items.size }
        binding.resultCountText.text = getString(R.string.search_result_count, totalCount)

        expandedGroups.clear()
        results.forEach { expandedGroups.add(it.fileName) }

        adapter.setData(results)
        binding.recyclerView.scrollToPosition(0)
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

    private inner class SearchAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<SearchListItem>()
        private var docResults: List<DocSearchResult> = emptyList()

        fun setData(results: List<DocSearchResult>) {
            docResults = results
            rebuildItems()
            notifyDataSetChanged()
        }

        private fun rebuildItems() {
            items.clear()
            for (result in docResults) {
                items.add(SearchListItem.Header(result))
                if (expandedGroups.contains(result.fileName)) {
                    result.items.forEach { item ->
                        items.add(SearchListItem.Result(result, item))
                    }
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is SearchListItem.Header -> VIEW_TYPE_HEADER
                is SearchListItem.Result -> VIEW_TYPE_RESULT
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_HEADER -> {
                    val binding = ItemHelpSearchHeaderBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                    HeaderViewHolder(binding)
                }
                else -> {
                    val binding = ItemHelpSearchResultBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                    ResultViewHolder(binding)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is SearchListItem.Header -> {
                    (holder as HeaderViewHolder).bind(item.result)
                }
                is SearchListItem.Result -> {
                    (holder as ResultViewHolder).bind(item.docResult, item.searchItem)
                }
            }
        }

        override fun getItemCount() = items.size

        private inner class HeaderViewHolder(
            private val binding: ItemHelpSearchHeaderBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(result: DocSearchResult) {
                binding.docNameText.text = result.docName
                binding.docCountText.text = getString(R.string.search_doc_result_count, result.items.size)

                val isExpanded = expandedGroups.contains(result.fileName)
                binding.expandIcon.rotation = if (isExpanded) 180f else 0f

                binding.root.setOnClickListener {
                    val fileName = result.fileName
                    if (expandedGroups.contains(fileName)) {
                        expandedGroups.remove(fileName)
                    } else {
                        expandedGroups.add(fileName)
                    }
                    rebuildItems()
                    notifyDataSetChanged()
                }
            }
        }

        private inner class ResultViewHolder(
            private val binding: ItemHelpSearchResultBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(docResult: DocSearchResult, searchItem: SearchResultItem) {
                binding.docNameText.visibility = View.GONE
                binding.lineNumberText.text = getString(R.string.line_number, searchItem.lineNumber)
                binding.matchedTextText.text = highlightText(searchItem.matchedText, searchItem.searchTerm)

                binding.root.setOnClickListener {
                    showDialogFragment(TextDialog(
                        docResult.docName,
                        allDocsContent[docResult.fileName],
                        TextDialog.Mode.MD,
                        docResult.fileName
                    ))
                }
            }
        }
    }
}

private sealed class SearchListItem {
    data class Header(val result: DocSearchResult) : SearchListItem()
    data class Result(val docResult: DocSearchResult, val searchItem: SearchResultItem) : SearchListItem()
}

private data class DocSearchResult(
    val docName: String,
    val fileName: String,
    val items: List<SearchResultItem>
)

private data class SearchResultItem(
    val lineNumber: Int,
    val matchedText: String,
    val searchTerm: String
)
