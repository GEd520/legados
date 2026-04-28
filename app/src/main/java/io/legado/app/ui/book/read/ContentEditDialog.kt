package io.legado.app.ui.book.read

import android.app.Application
import android.content.DialogInterface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.DialogContentEditBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.applyTint
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内容编辑
 */
class ContentEditDialog : BaseDialogFragment(R.layout.dialog_content_edit) {

    val binding by viewBinding(DialogContentEditBinding::bind)
    val viewModel by viewModels<ContentEditViewModel>()

    private var searchKeyword: String = ""
    private var currentIndex: Int = -1
    private var matchPositions: MutableList<Int> = mutableListOf()
    private var originalContent: SpannableString? = null

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.title = ReadBook.curTextChapter?.title
        initMenu()
        binding.toolBar.setOnClickListener {
            lifecycleScope.launch {
                val book = ReadBook.book ?: return@launch
                val chapter = withContext(IO) {
                    appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                } ?: return@launch
                editTitle(chapter)
            }
        }
        viewModel.loadStateLiveData.observe(viewLifecycleOwner) {
            if (it) {
                binding.rlLoading.visible()
            } else {
                binding.rlLoading.gone()
            }
        }
        viewModel.initContent {
            binding.contentView.setText(it)
            binding.contentView.post {
                binding.contentView.apply {
                    val lineIndex = layout.getLineForOffset(ReadBook.durChapterPos)
                    val lineHeight = layout.getLineTop(lineIndex)
                    scrollTo(0, lineHeight)
                }
            }
        }
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.content_edit)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_search -> toggleSearchPanel()
                R.id.menu_save -> {
                    save()
                    dismiss()
                }
                R.id.menu_reset -> viewModel.initContent(true) { content ->
                    binding.contentView.setText(content)
                    originalContent = null
                    ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                }
                R.id.menu_copy_all -> requireContext()
                    .sendToClip("${binding.toolBar.title}\n${binding.contentView.text}")
            }
            return@setOnMenuItemClickListener true
        }
        initSearchPanel()
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

    private fun editTitle(chapter: BookChapter) {
        alert {
            setTitle(R.string.edit)
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater)
            alertBinding.editView.setText(chapter.title)
            setCustomView(alertBinding.root)
            okButton {
                chapter.title = alertBinding.editView.text.toString()
                lifecycleScope.launch {
                    withContext(IO) {
                        chapter.update()
                    }
                    binding.toolBar.title = chapter.getDisplayTitle()
                    ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                }
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        save()
    }

    private fun save() {
        val content = originalContent?.toString() ?: binding.contentView.text?.toString() ?: return
        Coroutine.async {
            val book = ReadBook.book ?: return@async
            val chapter = appDb.bookChapterDao
                .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@async
            BookHelp.saveText(book, chapter, content)
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }
    }

    class ContentEditViewModel(application: Application) : BaseViewModel(application) {
        val loadStateLiveData = MutableLiveData<Boolean>()
        var content: String? = null

        fun initContent(reset: Boolean = false, success: (String) -> Unit) {
            execute {
                val book = ReadBook.book ?: return@execute null
                val chapter = appDb.bookChapterDao
                    .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                    ?: return@execute null
                if (reset) {
                    content = null
                    BookHelp.delContent(book, chapter)
                    if (!book.isLocal) ReadBook.bookSource?.let { bookSource ->
                        WebBook.getContentAwait(bookSource, book, chapter)
                    }
                }
                return@execute content ?: let {
                    val contentProcessor = ContentProcessor.get(book.name, book.origin)
                    val content = BookHelp.getContent(book, chapter) ?: return@let null
                    contentProcessor.getContent(book, chapter, content, includeTitle = false)
                        .toString()
                }
            }.onStart {
                loadStateLiveData.postValue(true)
            }.onSuccess {
                content = it
                success.invoke(it ?: "")
            }.onFinally {
                loadStateLiveData.postValue(false)
            }
        }

    }

}