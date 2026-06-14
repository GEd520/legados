package io.legado.app.ui.config

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.databinding.DialogThemeListBinding
import io.legado.app.databinding.ItemThemeConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ThemeListDialog : BaseDialogFragment(R.layout.dialog_theme_list),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogThemeListBinding::bind)
    private val adapter by lazy { Adapter(requireContext()) }
    private var isMultiSelectMode = false
    private val selectedPositions = mutableSetOf<Int>()
    private var isNightThemeTab = false  // 当前选中的 Tab (false = 日间, true = 夜间)

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.theme_list)
        initView()
        initMenu()
        initTabs()
        initData()
        updateSummary()
    }

    private fun initView() = binding.run {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        recyclerView.adapter = adapter
        
        // 添加主题按钮
        tvAddTheme.setOnClickListener {
            showAddOptions()
        }
    }

    private fun initMenu() = binding.run {
        toolBar.setOnMenuItemClickListener(this@ThemeListDialog)
        toolBar.inflateMenu(R.menu.theme_list)
        toolBar.menu.applyTint(requireContext())
    }

    // 初始化 Tab 切换
    private fun initTabs() = binding.run {
        // 初始化时根据当前主题模式设置 Tab
        isNightThemeTab = AppConfig.isNightTheme
        updateTabSelection()
        
        // 日间 Tab 点击
        tabDay.setOnClickListener {
            if (isNightThemeTab) {
                isNightThemeTab = false
                updateTabSelection()
                initData()
                updateSummary()
            }
        }
        
        // 夜间 Tab 点击
        tabNight.setOnClickListener {
            if (!isNightThemeTab) {
                isNightThemeTab = true
                updateTabSelection()
                initData()
                updateSummary()
            }
        }
    }

    // 更新 Tab 选中状态
    private fun updateTabSelection() = binding.run {
        val accentColor = primaryColor
        val primaryTextColor = ContextCompat.getColor(requireContext(), R.color.primaryText)
        
        // 日间 Tab
        val dayTabSelected = !isNightThemeTab
        tvTabDay.setTextColor(if (dayTabSelected) accentColor else primaryTextColor)
        tabDay.background = if (dayTabSelected) {
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_theme_tab_selected)
        } else {
            null
        }
        
        // 夜间 Tab
        val nightTabSelected = isNightThemeTab
        tvTabNight.setTextColor(if (nightTabSelected) accentColor else primaryTextColor)
        tabNight.background = if (nightTabSelected) {
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_theme_tab_selected)
        } else {
            null
        }
    }

    // 更新摘要文本
    private fun updateSummary() = binding.run {
        val filteredThemes = getFilteredThemes()
        if (filteredThemes.isEmpty()) {
            val themeType = if (isNightThemeTab) getString(R.string.night) else getString(R.string.day)
            tvSummary.text = getString(R.string.theme_summary_empty, themeType)
        } else {
            tvSummary.text = getString(R.string.theme_summary)
        }
    }

    // 获取过滤后的主题列表
    private fun getFilteredThemes(): List<ThemeConfig.Config> {
        return ThemeConfig.configList.filter { it.isNightTheme == isNightThemeTab }
    }

    // 初始化多选菜单
    private fun initMultiSelectMenu() = binding.run {
        toolBar.menu.clear()
        toolBar.inflateMenu(R.menu.theme_list_multi)
        toolBar.menu.applyTint(requireContext())
        toolBar.setTitle(getString(R.string.selected, selectedPositions.size))
    }

    fun initData() {
        adapter.setItems(getFilteredThemes())
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_import -> {
                requireContext().getClipText()?.let { clipText ->
                    val count = ThemeConfig.addConfig(clipText)
                    if (count > 0) {
                        initData()
                        toastOnUi("成功导入 $count 个主题")
                    } else {
                        toastOnUi("格式不对,添加失败")
                    }
                } ?: toastOnUi("剪贴板为空")
            }
            R.id.menu_select_all -> {
                if (selectedPositions.size == adapter.itemCount) {
                    selectedPositions.clear()
                } else {
                    selectedPositions.clear()
                    for (i in 0 until adapter.itemCount) {
                        selectedPositions.add(i)
                    }
                }
                adapter.notifyDataSetChanged()
                binding.toolBar.setTitle(getString(R.string.selected, selectedPositions.size))
            }
            R.id.menu_to_top -> {
                if (selectedPositions.isEmpty()) {
                    toastOnUi("请先选择主题")
                    return true
                }
                toTopSelected()
            }
            R.id.menu_export -> {
                if (selectedPositions.isEmpty()) {
                    toastOnUi("请先选择主题")
                    return true
                }
                exportSelected()
            }
            R.id.menu_delete -> {
                if (selectedPositions.isEmpty()) {
                    toastOnUi("请先选择主题")
                    return true
                }
                deleteSelected()
            }
        }
        return true
    }

    // 显示添加主题选项
    private fun showAddOptions() {
        val items = listOf(
            getString(R.string.save_theme_config),
            getString(R.string.import_str)
        )
        requireContext().selector(items = items) { _, i ->
            when (i) {
                0 -> alertSaveTheme()
                1 -> {
                    requireContext().getClipText()?.let { clipText ->
                        val count = ThemeConfig.addConfig(clipText)
                        if (count > 0) {
                            initData()
                            updateSummary()
                            toastOnUi("成功导入 $count 个主题")
                        } else {
                            toastOnUi("格式不对,添加失败")
                        }
                    } ?: toastOnUi("剪贴板为空")
                }
            }
        }
    }

    // 保存当前主题
    private fun alertSaveTheme() {
        alert(R.string.theme_name) {
            val alertBinding = io.legado.app.databinding.DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "name"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let { themeName ->
                    if (themeName.isNotBlank()) {
                        if (isNightThemeTab) {
                            ThemeConfig.saveNightTheme(requireContext(), themeName)
                        } else {
                            ThemeConfig.saveDayTheme(requireContext(), themeName)
                        }
                        initData()
                        updateSummary()
                        toastOnUi("主题已保存")
                    }
                }
            }
            cancelButton()
        }
    }

    // 进入多选模式
    private fun enterMultiSelectMode(position: Int) {
        isMultiSelectMode = true
        selectedPositions.clear()
        selectedPositions.add(position)
        initMultiSelectMenu()
        adapter.notifyDataSetChanged()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedPositions.clear()
        binding.toolBar.menu.clear()
        initMenu()
        binding.toolBar.setTitle(R.string.theme_list)
        adapter.notifyDataSetChanged()
    }

    private fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
            if (selectedPositions.isEmpty()) {
                exitMultiSelectMode()
                return
            }
        } else {
            selectedPositions.add(position)
        }
        adapter.notifyItemChanged(position)
        binding.toolBar.setTitle(getString(R.string.selected, selectedPositions.size))
    }

    private fun exportSelected() {
        val filteredThemes = getFilteredThemes()
        val configs = selectedPositions.sorted().map { index ->
            filteredThemes[index]
        }
        val json = GSON.toJson(configs)
        requireContext().share(json, "主题分享")
        exitMultiSelectMode()
    }

    private fun deleteSelected() {
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                val filteredThemes = getFilteredThemes()
                val positions = selectedPositions.sortedDescending()
                // 需要找到在原始列表中的位置
                positions.forEach { filteredIndex ->
                    val config = filteredThemes[filteredIndex]
                    val originalIndex = ThemeConfig.configList.indexOfFirst { 
                        it.themeName == config.themeName && it.isNightTheme == config.isNightTheme 
                    }
                    if (originalIndex >= 0) {
                        ThemeConfig.delConfig(originalIndex)
                    }
                }
                exitMultiSelectMode()
                initData()
                updateSummary()
            }
            noButton()
        }
    }

    // 移动选中主题到顶部
    private fun toTopSelected() {
        val filteredThemes = getFilteredThemes()
        val positions = selectedPositions.sorted()
        val themeNames = positions.map { filteredThemes[it].themeName }
        AppLog.put("置顶主题: ${themeNames.joinToString(", ")}", toast = true)
        
        // 需要找到在原始列表中的位置
        val originalPositions = positions.map { filteredIndex ->
            val config = filteredThemes[filteredIndex]
            ThemeConfig.configList.indexOfFirst { 
                it.themeName == config.themeName && it.isNightTheme == config.isNightTheme 
            }
        }.filter { it >= 0 }
        
        ThemeConfig.toTopConfigs(originalPositions)
        exitMultiSelectMode()
        initData()
    }

    fun delete(index: Int) {
        val filteredThemes = getFilteredThemes()
        val config = filteredThemes[index]
        val originalIndex = ThemeConfig.configList.indexOfFirst { 
            it.themeName == config.themeName && it.isNightTheme == config.isNightTheme 
        }
        
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                if (originalIndex >= 0) {
                    ThemeConfig.delConfig(originalIndex)
                }
                initData()
                updateSummary()
            }
            noButton()
        }
    }

    fun share(index: Int) {
        val filteredThemes = getFilteredThemes()
        val json = GSON.toJson(filteredThemes[index])
        requireContext().share(json, "主题分享")
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<ThemeConfig.Config, ItemThemeConfigBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemThemeConfigBinding {
            return ItemThemeConfigBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemThemeConfigBinding,
            item: ThemeConfig.Config,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                tvName.text = item.themeName
                
                // 设置预览卡片的背景颜色
                val bgColor = try {
                    android.graphics.Color.parseColor(item.backgroundColor)
                } catch (e: Exception) {
                    if (item.isNightTheme) {
                        ContextCompat.getColor(context, R.color.default_night_background)
                    } else {
                        ContextCompat.getColor(context, R.color.default_background)
                    }
                }
                
                val primaryColor = try {
                    android.graphics.Color.parseColor(item.primaryColor)
                } catch (e: Exception) {
                    if (item.isNightTheme) {
                        ContextCompat.getColor(context, R.color.default_night_primary)
                    } else {
                        ContextCompat.getColor(context, R.color.default_primary)
                    }
                }
                
                // 设置预览容器背景
                val previewDrawable = GradientDrawable()
                previewDrawable.cornerRadius = 10f
                previewDrawable.setColor(bgColor)
                previewContainer.background = previewDrawable
                
                // 设置预览元素颜色
                val primaryDrawable = GradientDrawable()
                primaryDrawable.cornerRadius = 4f
                primaryDrawable.setColor(primaryColor)
                previewPrimary.background = primaryDrawable
                
                val bar1Drawable = GradientDrawable()
                bar1Drawable.cornerRadius = 2f
                bar1Drawable.setColor(primaryColor)
                bar1Drawable.alpha = 77  // 30% opacity
                previewBar1.background = bar1Drawable
                
                val bar2Drawable = GradientDrawable()
                bar2Drawable.cornerRadius = 2f
                bar2Drawable.setColor(primaryColor)
                bar2Drawable.alpha = 51  // 20% opacity
                previewBar2.background = bar2Drawable
                
                // 检查是否是当前应用的主题
                val currentConfig = ThemeConfig.getDurConfig(context)
                val isCurrentTheme = item.themeName == currentConfig.themeName 
                    && item.isNightTheme == currentConfig.isNightTheme
                
                // 设置当前主题标记
                ivCurrent.visibility = if (isCurrentTheme && !isMultiSelectMode) View.VISIBLE else View.GONE
                
                // 设置信息文本
                val themeType = if (item.isNightTheme) getString(R.string.night) else getString(R.string.day)
                val infoText = if (isCurrentTheme) {
                    "${getString(R.string.current_applied)} · $themeType"
                } else {
                    themeType
                }
                tvInfo.text = infoText
                
                // 设置应用按钮文本
                tvApply.text = if (isCurrentTheme) getString(R.string.applied) else getString(R.string.apply_theme)
                if (isCurrentTheme) {
                    tvApply.setTextColor(ContextCompat.getColor(context, R.color.error))
                } else {
                    tvApply.setTextColor(ContextCompat.getColor(context, R.color.primaryText))
                }
                
                // 多选模式下的显示
                if (isMultiSelectMode) {
                    cbSelect.visibility = View.VISIBLE
                    cbSelect.isChecked = selectedPositions.contains(holder.layoutPosition)
                    tvApply.visibility = View.GONE
                    tvEdit.visibility = View.GONE
                    tvMore.visibility = View.GONE
                    ivShare.visibility = View.GONE
                    ivDelete.visibility = View.GONE
                } else {
                    cbSelect.visibility = View.GONE
                    tvApply.visibility = View.VISIBLE
                    tvEdit.visibility = View.VISIBLE
                    tvMore.visibility = View.VISIBLE
                    ivShare.visibility = View.GONE
                    ivDelete.visibility = View.GONE
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemThemeConfigBinding) {
            binding.apply {
                root.setOnClickListener {
                    val position = holder.layoutPosition
                    if (isMultiSelectMode) {
                        toggleSelection(position)
                    }
                }
                root.setOnLongClickListener {
                    if (!isMultiSelectMode) {
                        enterMultiSelectMode(holder.layoutPosition)
                    }
                    true
                }
                tvApply.setOnClickListener {
                    if (!isMultiSelectMode) {
                        val filteredThemes = getFilteredThemes()
                        val config = filteredThemes[holder.layoutPosition]
                        AppLog.put("应用主题: ${config.themeName}", toast = true)
                        ThemeConfig.applyConfig(context, config)
                        // 更新 Tab 到应用的主题类型
                        isNightThemeTab = config.isNightTheme
                        updateTabSelection()
                        adapter.notifyDataSetChanged()
                    }
                }
                tvEdit.setOnClickListener {
                    if (!isMultiSelectMode) {
                        // 编辑主题 (暂时使用分享功能)
                        share(holder.layoutPosition)
                    }
                }
                tvMore.setOnClickListener {
                    if (!isMultiSelectMode) {
                        val filteredThemes = getFilteredThemes()
                        val config = filteredThemes[holder.layoutPosition]
                        showMoreOptions(config, holder.layoutPosition)
                    }
                }
            }
        }
        
        private fun showMoreOptions(config: ThemeConfig.Config, position: Int) {
            val items = listOf(
                getString(R.string.apply_theme),
                getString(R.string.export_str),
                getString(R.string.delete)
            )
            requireContext().selector(items = items) { _, i ->
                when (i) {
                    0 -> {
                        AppLog.put("应用主题: ${config.themeName}", toast = true)
                        ThemeConfig.applyConfig(context, config)
                        isNightThemeTab = config.isNightTheme
                        updateTabSelection()
                        adapter.notifyDataSetChanged()
                    }
                    1 -> share(position)
                    2 -> delete(position)
                }
            }
        }
    }
}