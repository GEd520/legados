@file:Suppress("DEPRECATION")

package io.legado.app.ui.main.bookshelf.style1

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf1Binding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.group.GroupEditDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.style1.books.BooksFragment
import io.legado.app.ui.widget.applyTopBarChildConfig
import io.legado.app.ui.widget.applyTopBarConfig
import io.legado.app.utils.MenuExtensions
import io.legado.app.utils.dpToPx
import io.legado.app.utils.applyTint
import io.legado.app.utils.isCreated
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlin.collections.set

/**
 * 书架界面
 */
class BookshelfFragment1() : BaseBookshelfFragment(R.layout.fragment_bookshelf1),
    TabLayout.OnTabSelectedListener,
    SearchView.OnQueryTextListener {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf1Binding::bind)
    private val adapter by lazy { TabFragmentPageAdapter(childFragmentManager) }
    private val tabLayout: TabLayout by lazy {
        binding.titleBar.findViewById(R.id.tab_layout)
    }
    private val groupSwitchContainer: View by lazy {
        binding.titleBar.findViewById(R.id.group_switch_container)
    }
    private val groupTitleSwitch: LinearLayout by lazy { createGroupTitleSwitch() }
    private lateinit var groupTitleText: TextView
    private lateinit var groupTitleArrow: AppCompatImageView
    private val bookGroups = mutableListOf<BookGroup>()
    private val fragmentMap = hashMapOf<Long, BooksFragment>()
    private var groupPopup: PopupWindow? = null
    override val groupId: Long get() = selectedGroup?.groupId ?: 0

    override val books: List<Book>
        get() {
            val fragment = fragmentMap[groupId]
            return fragment?.getBooks() ?: emptyList()
        }

    override var onlyUpdateRead = false
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        binding.titleBar.applyTopBarConfig()
        initView()
        initBookGroupData()
    }

    private val selectedGroup: BookGroup?
        get() = bookGroups.getOrNull(binding.viewPagerBookshelf.currentItem)

    private fun initView() {
        binding.viewPagerBookshelf.setEdgeEffectColor(primaryColor)
        initGroupSwitchView()
        tabLayout.isTabIndicatorFullWidth = false
        tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        tabLayout.applyTopBarChildConfig()
        tabLayout.setupWithViewPager(binding.viewPagerBookshelf)
        binding.viewPagerBookshelf.offscreenPageLimit = 2
        binding.viewPagerBookshelf.adapter = adapter
        binding.viewPagerBookshelf.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                updateGroupSwitchTitle(position)
            }
        })
    }

    private fun initGroupSwitchView() {
        binding.titleBar.toolbar.addView(
            groupTitleSwitch,
            Toolbar.LayoutParams(
                Toolbar.LayoutParams.WRAP_CONTENT,
                36.dpToPx(),
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        )
        groupTitleSwitch.setOnClickListener {
            showGroupSwitchMenu()
        }
        updateGroupSwitchMode()
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    @Synchronized
    override fun upGroup(data: List<BookGroup>) {
        if (data.isEmpty()) {
            appDb.bookGroupDao.enableGroup(BookGroup.IdAll)
        } else {
            if (data != bookGroups) {
                bookGroups.clear()
                bookGroups.addAll(data)
                adapter.notifyDataSetChanged()
                stabilizeMultilineTabs()
                updateGroupSwitchTitle()
                selectLastTab()
                for (i in 0 until adapter.count) {
                    tabLayout.getTabAt(i)?.view?.setOnLongClickListener {
                        showDialogFragment(GroupEditDialog(bookGroups[i]))
                        true
                    }
                }
            }
        }
    }

    private fun stabilizeMultilineTabs() {
        bookGroups.forEachIndexed { index, group ->
            if (!group.groupName.contains('\n') && !group.groupName.contains('\r')) return@forEachIndexed
            tabLayout.getTabAt(index)?.customView = TextView(requireContext()).apply {
                text = group.groupName.replace("\r\n", "\n").replace('\r', '\n')
                gravity = Gravity.CENTER
                includeFontPadding = false
                setLines(2)
                textSize = 14f
                setTextColor(tabLayout.tabTextColors)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }
    }

    override fun upSort() {
        adapter.notifyDataSetChanged()
    }

    private fun selectLastTab() {
        tabLayout.post {
            tabLayout.removeOnTabSelectedListener(this)
            if (bookGroups.isNotEmpty()) {
                val target = AppConfig.saveTabPosition.coerceIn(0, bookGroups.lastIndex)
                tabLayout.getTabAt(target)?.select()
                updateGroupSwitchTitle(target)
            }
            tabLayout.addOnTabSelectedListener(this)
        }
    }

    override fun onTabReselected(tab: TabLayout.Tab) {
        selectedGroup?.let { group ->
            fragmentMap[group.groupId]?.let {
                toastOnUi("${group.groupName}(${it.getBooksCount()})")
            }
        }
    }

    override fun onTabUnselected(tab: TabLayout.Tab) = Unit

    override fun onTabSelected(tab: TabLayout.Tab) {
        AppConfig.saveTabPosition = tab.position
        updateGroupSwitchTitle(tab.position)
    }

    override fun gotoTop() {
        fragmentMap[groupId]?.gotoTop()
    }

    override fun updateMainBottomPadding(bottomPadding: Int) {
        if (view == null) return
        fragmentMap.values.forEach {
            if (it.view != null) {
                it.updateMainBottomPadding(bottomPadding)
            }
        }
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<Boolean>(EventBus.TOP_BAR_CHANGED) {
            if (it == AppConfig.isNightTheme && view != null) {
                binding.titleBar.applyTopBarConfig()
                updateGroupTitleColor()
            }
        }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            updateGroupSwitchMode()
        }
    }

    private fun updateGroupSwitchMode() {
        val useDropDown = AppConfig.bookshelfGroupDropDown
        tabLayout.visibility = if (useDropDown) View.GONE else View.VISIBLE
        groupSwitchContainer.visibility = if (useDropDown) View.GONE else View.VISIBLE
        groupTitleSwitch.visibility = if (useDropDown) View.VISIBLE else View.GONE
        if (useDropDown) {
            binding.titleBar.title = ""
            updateGroupTitleColor()
            updateGroupSwitchTitle()
        } else {
            binding.titleBar.setTitle(R.string.bookshelf)
        }
    }

    private fun updateGroupSwitchTitle(position: Int = binding.viewPagerBookshelf.currentItem) {
        groupTitleText.text = bookGroups.getOrNull(position)?.groupName ?: getString(R.string.bookshelf)
    }

    private fun showGroupSwitchMenu() {
        if (bookGroups.isEmpty()) return
        groupPopup?.dismiss()
        val availableHeight = availableGroupPopupHeight()
        val contentHeight = estimateGroupPopupContentHeight()
        val needScroll = contentHeight > availableHeight
        val popupWidth = estimateGroupPopupWidth(needScroll)
        val popupHeight = if (needScroll) {
            availableHeight.coerceAtLeast(72.dpToPx())
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val list = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6.dpToPx(), 6.dpToPx(), 6.dpToPx(), 6.dpToPx())
            bookGroups.forEachIndexed { index, group ->
                addView(
                    createGroupMenuItem(index, group.groupName),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        36.dpToPx()
                    )
                )
            }
        }
        val content = ScrollView(requireContext()).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            overScrollMode = if (needScroll) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_popup_menu)
            addView(
                list,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        groupPopup = PopupWindow(content, popupWidth, popupHeight, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 6.dpToPx().toFloat()
            showAsDropDown(groupTitleSwitch, 0, 4.dpToPx())
        }
        if (needScroll) {
            scrollGroupPopupToCurrent(content, popupHeight, contentHeight)
        }
    }

    private fun switchToGroup(position: Int) {
        if (position !in bookGroups.indices) return
        binding.viewPagerBookshelf.setCurrentItem(position, false)
        AppConfig.saveTabPosition = position
        updateGroupSwitchTitle(position)
    }

    private fun createGroupTitleSwitch(): LinearLayout {
        val contentColor = groupTitleColor()
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(0, 0, 4.dpToPx(), 0)
            groupTitleText = TextView(context).apply {
                includeFontPadding = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(contentColor)
                textSize = 18f
                typeface = Typeface.DEFAULT
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            groupTitleArrow = AppCompatImageView(context).apply {
                setImageResource(R.drawable.ic_arrow_drop_down)
                applyTint(contentColor)
                layoutParams = LinearLayout.LayoutParams(20.dpToPx(), 20.dpToPx())
            }
            addView(groupTitleText)
            addView(groupTitleArrow)
        }
    }

    private fun updateGroupTitleColor() {
        val contentColor = groupTitleColor()
        if (::groupTitleText.isInitialized) {
            groupTitleText.setTextColor(contentColor)
        }
        if (::groupTitleArrow.isInitialized) {
            groupTitleArrow.applyTint(contentColor)
        }
    }

    private fun groupTitleColor(): Int {
        return MenuExtensions.getMenuColor(requireContext(), binding.titleBar.topBarTheme)
    }

    private fun createGroupMenuItem(position: Int, title: String): TextView {
        val selected = position == binding.viewPagerBookshelf.currentItem
        return TextView(requireContext()).apply {
            text = title
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            textSize = 14f
            includeFontPadding = false
            minHeight = 36.dpToPx()
            setPadding(12.dpToPx(), 0, 12.dpToPx(), 0)
            setTextColor(ContextCompat.getColor(context, R.color.primaryText))
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            background = if (selected) selectedGroupMenuItemBg() else null
            setOnClickListener {
                groupPopup?.dismiss()
                switchToGroup(position)
            }
        }
    }

    private fun estimateGroupPopupWidth(needScroll: Boolean): Int {
        val longest = bookGroups.maxOfOrNull { it.groupName.length } ?: 0
        if (!needScroll) {
            return (56 + longest.coerceAtMost(12) * 12).coerceIn(124, 220).dpToPx()
        }
        return (48 + longest.coerceAtMost(10) * 11).coerceIn(116, 188).dpToPx()
    }

    private fun estimateGroupPopupContentHeight(): Int {
        return 12.dpToPx() + bookGroups.size * 36.dpToPx()
    }

    private fun availableGroupPopupHeight(): Int {
        val root = binding.root.rootView
        val rootLocation = IntArray(2)
        val anchorLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        groupTitleSwitch.getLocationOnScreen(anchorLocation)
        val anchorBottom = anchorLocation[1] - rootLocation[1] + groupTitleSwitch.height
        val bottomAvailable = root.height - anchorBottom - 8.dpToPx()
        val middleAvailable = root.height / 2 - anchorBottom - 4.dpToPx()
        return middleAvailable.coerceAtLeast(72.dpToPx()).coerceAtMost(bottomAvailable)
    }

    private fun scrollGroupPopupToCurrent(content: ScrollView, popupHeight: Int, contentHeight: Int) {
        val current = binding.viewPagerBookshelf.currentItem.coerceIn(0, bookGroups.lastIndex)
        val itemHeight = 36.dpToPx()
        val paddingTop = 6.dpToPx()
        val itemCenter = paddingTop + current * itemHeight + itemHeight / 2
        val maxScroll = (contentHeight - popupHeight).coerceAtLeast(0)
        val targetScroll = (itemCenter - popupHeight / 2).coerceIn(0, maxScroll)
        content.post {
            content.scrollTo(0, targetScroll)
        }
    }

    private fun selectedGroupMenuItemBg() = GradientDrawable().apply {
        cornerRadius = 8f.dpToPx()
        setColor(ContextCompat.getColor(requireContext(), R.color.transparent10))
    }

    private inner class TabFragmentPageAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getPageTitle(position: Int): CharSequence {
            return bookGroups[position].groupName
        }

        /**
         * 确定视图位置是否更改时调用
         * @return POSITION_NONE 已更改,刷新视图. POSITION_UNCHANGED 未更改,不刷新视图
         */
        override fun getItemPosition(any: Any): Int {
            val fragment = any as BooksFragment
            val position = fragment.position
            val group = bookGroups.getOrNull(position)
            if (fragment.groupId != group?.groupId) {
                return POSITION_NONE
            }
            val bookSort = group.getRealBookSort()
            fragment.setEnableRefresh(group.enableRefresh)
            if (fragment.bookSort != bookSort) {
                fragment.upBookSort(bookSort)
            }
            return POSITION_UNCHANGED
        }

        override fun getItem(position: Int): Fragment {
            val group = bookGroups[position]
            onlyUpdateRead = group.onlyUpdateRead
            return BooksFragment(position, group)
        }

        override fun getCount(): Int {
            return bookGroups.size
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as BooksFragment
            val group = bookGroups[position]
            /**
             * Activity recreate 会复用之前的 Fragment，不正确的需要重新创建
             */
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as BooksFragment
            }
            fragmentMap[group.groupId] = fragment
            return fragment
        }

    }
}
