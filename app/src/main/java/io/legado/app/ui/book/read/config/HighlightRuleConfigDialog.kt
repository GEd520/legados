package io.legado.app.ui.book.read.config

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogHighlightRuleConfigBinding
import io.legado.app.databinding.ItemHighlightPresetRuleBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getClipText
import io.legado.app.utils.getPrefString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefString
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class HighlightRuleConfigDialog : HighlightRuleBottomSheetFragment(R.layout.dialog_highlight_rule_config),
    PopupMenu.OnMenuItemClickListener {

    private val binding by viewBinding(DialogHighlightRuleConfigBinding::bind)
    private val adapter by lazy { HighlightRuleAdapter(requireContext()) }
    private val rules = ArrayList<HighlightRule>()
    private var currentGroup: String? = null
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private var accentColor = 0
    private var cardBgColor = 0
    private var cardStrokeColor = 0
    private var previewBgColor = 0
    private var previewStrokeColor = 0

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initTheme()

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.clipToPadding = false
        binding.recyclerView.setPadding(0, 12.dpToPx(), 0, 28.dpToPx())

        binding.ivClose.setOnClickListener { dismissAllowingStateLoss() }
        binding.ivMenu.setOnClickListener { showMenu(it) }
        binding.tvEmptyAdd.setOnClickListener { showPresetRules() }

        loadRules()
    }

    override fun observeLiveBus() {
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) {
            if (it.contains(1) || it.contains(2)) {
                initTheme()
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun initTheme() {
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        primaryTextColor = requireContext().getPrimaryTextColor(isLight)
        secondaryTextColor = requireContext().getSecondaryTextColor(isLight)
        accentColor = requireContext().accentColor

        cardBgColor = if (isLight) {
            ColorUtils.blendColors(bg, 0xFF000000.toInt(), 0.08f)
        } else {
            ColorUtils.blendColors(bg, 0xFFFFFFFF.toInt(), 0.06f)
        }
        cardStrokeColor = if (isLight) {
            ColorUtils.blendColors(0xFF000000.toInt(), bg, 0.88f)
        } else {
            ColorUtils.blendColors(0xFFFFFFFF.toInt(), bg, 0.85f)
        }
        previewBgColor = cardBgColor
        previewStrokeColor = cardStrokeColor

        binding.sheetContainer.background?.mutate()?.setTint(bg)
        binding.ivClose.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.ivClose.background?.mutate()?.setTint(accentColor)
        binding.tvPageTitle.setTextColor(primaryTextColor)
        binding.tvPageSubtitle.setTextColor(secondaryTextColor)
        binding.ivMenu.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.ivMenu.background?.mutate()?.setTint(accentColor)
        binding.ivEmpty.setColorFilter(secondaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.tvEmptyMsg.setTextColor(secondaryTextColor)
        binding.tvEmptyHint.setTextColor(secondaryTextColor)

        binding.tvEmptyAdd.background?.mutate()?.setTint(accentColor)
        binding.tvEmptyAdd.setTextColor(
            if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        )
    }

    private fun showMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.highlight_rule_config, menu)
            setOnMenuItemClickListener(this@HighlightRuleConfigDialog)
        }.show()
    }

    private fun showItemMenu(rule: HighlightRule, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.highlight_rule_item, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_edit -> editRule(rule)
                    R.id.menu_delete -> deleteRule(rule)
                    R.id.menu_export_single -> exportRulesToClipboard(listOf(rule))
                    R.id.menu_share_single -> shareRules(listOf(rule))
                }
                true
            }
        }.show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_select -> showRuleSelector()
            R.id.menu_add -> editRule(null)
            R.id.menu_preset -> showPresetRules()
            R.id.menu_import -> importRulesFromClipboard()
            R.id.menu_group -> showGroupManager()
            R.id.menu_share -> shareRules(getFilteredRules())
            R.id.menu_export -> exportRulesToClipboard(getFilteredRules())
            R.id.menu_reset -> resetRules()
            else -> return false
        }
        return true
    }

    private fun loadRules() {
        rules.clear()
        rules.addAll(HighlightRuleStore.load(requireContext()))
        loadCurrentGroup()
        applyGroupFilter()
    }

    private fun loadCurrentGroup() {
        val saved = requireContext().getPrefString(PreferKey.highlightRuleCurrentGroup)
        if (!saved.isNullOrBlank()) {
            val groups = HighlightRuleGroupStore.load(requireContext())
            if (groups.contains(saved)) {
                currentGroup = saved
            } else {
                currentGroup = null
                saveCurrentGroup()
            }
        }
    }

    private fun saveCurrentGroup() {
        requireContext().putPrefString(PreferKey.highlightRuleCurrentGroup, currentGroup.orEmpty())
    }

    private fun applyGroupFilter() {
        val filtered = getFilteredRules()
        adapter.setItems(filtered)
        updateEmptyState()
        updateSubtitle()
        saveCurrentGroup()
    }

    private fun getFilteredRules(): List<HighlightRule> {
        return if (currentGroup == null) {
            rules.toList()
        } else {
            rules.filter { it.group == currentGroup }
        }
    }

    private fun updateSubtitle() {
        val groupText = currentGroup ?: "全部分组"
        val count = getFilteredRules().size
        binding.tvPageSubtitle.text = "$groupText · $count 条规则"
    }

    fun switchToGroup(group: String?) {
        currentGroup = group
        applyGroupFilter()
    }

    private fun updateEmptyState() {
        val filteredCount = getFilteredRules().size
        val empty = filteredCount == 0
        binding.recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
        binding.emptyPanel.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun resetRules() {
        alert("恢复默认") {
            setMessage("恢复后会重新生成预置规则，自定义规则会被覆盖。")
            okButton {
                rules.clear()
                rules.addAll(HighlightRuleStore.reset(requireContext()))
                syncRules()
            }
            cancelButton()
        }
    }

    private fun editRule(rule: HighlightRule?) {
        val defaultGroup = rule?.group ?: currentGroup
        HighlightRuleEditDialog(rule, defaultGroup) { newRule ->
            val index = rules.indexOfFirst { it.id == newRule.id }
            if (index >= 0) {
                rules[index] = newRule
            } else {
                rules.add(newRule)
            }
            syncRules()
        }.show(childFragmentManager, "highlightRuleEdit")
    }

    private fun deleteRule(rule: HighlightRule) {
        alert("删除") {
            setMessage("确定删除“${rule.name}”吗？")
            okButton {
                rules.removeAll { it.id == rule.id }
                syncRules()
            }
            cancelButton()
        }
    }

    private fun showPresetRules() {
        HighlightPresetRuleDialog(currentGroup) { rule ->
            rules.add(rule)
            syncRules()
        }.show(childFragmentManager, "highlightPresetRule")
    }

    private fun showGroupManager() {
        HighlightRuleGroupManageDialog(
            onChanged = { oldGroup, newGroup ->
                if (oldGroup != null && currentGroup == oldGroup) {
                    currentGroup = newGroup
                }
                loadRules()
            },
            onSelectGroup = { group ->
                currentGroup = group
                applyGroupFilter()
            }
        ).show(childFragmentManager, "highlightRuleGroupManage")
    }

    private fun showRuleSelector() {
        if (rules.isEmpty()) {
            context?.toastOnUi("暂无可选择规则")
            return
        }
        val selected = BooleanArray(rules.size)
        val names = rules.map {
            "${it.name.ifBlank { "未命名规则" }} / ${it.group}"
        }.toTypedArray()
        alert("选择规则") {
            multiChoiceItems(names, selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            positiveButton("分享选中") {
                val picked = rules.filterIndexed { index, _ -> selected[index] }
                if (picked.isEmpty()) {
                    requireContext().toastOnUi("请先选择规则")
                } else {
                    shareRules(picked)
                }
            }
            neutralButton("删除选中") {
                val ids = rules.filterIndexed { index, _ -> selected[index] }.map { it.id }
                if (ids.isEmpty()) {
                    requireContext().toastOnUi("请先选择规则")
                } else {
                    rules.removeAll { ids.contains(it.id) }
                    syncRules()
                }
            }
            negativeButton("取消")
        }
    }

    private fun importRulesFromClipboard() {
        val clip = requireContext().getClipText()
        if (clip.isNullOrBlank()) {
            context?.toastOnUi(R.string.highlight_rule_clipboard_empty)
            return
        }
        val imported = GSON.fromJsonArray<HighlightRule>(clip).getOrNull()
        if (imported.isNullOrEmpty()) {
            context?.toastOnUi(R.string.highlight_rule_import_invalid)
            return
        }
        val targetGroup = currentGroup ?: HighlightRuleGroupStore.DEFAULT_GROUP
        imported.forEach { rule ->
            var normalized = HighlightRuleStore.sanitizeRule(rule, targetGroup)
            if (rules.any { it.id == normalized.id }) {
                normalized = normalized.copyWithNewId()
            }
            rules.add(normalized)
        }
        syncRules()
        context?.toastOnUi(R.string.highlight_rule_import_success)
    }

    private fun exportRulesToClipboard(targetRules: List<HighlightRule>) {
        if (targetRules.isEmpty()) {
            context?.toastOnUi("暂无规则可导出")
            return
        }
        requireContext().sendToClip(GSON.toJson(targetRules))
        context?.toastOnUi("已复制 ${targetRules.size} 条规则")
    }

    private fun shareRules(targetRules: List<HighlightRule>) {
        if (targetRules.isEmpty()) {
            context?.toastOnUi("没有可分享的规则")
            return
        }
        val json = GSON.toJson(targetRules)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(intent, "分享规则"))
    }

    private fun syncRules() {
        HighlightRuleStore.save(requireContext(), rules)
        applyGroupFilter()
        postEvent(EventBus.UP_CONFIG, arrayListOf(5))
    }

    private inner class HighlightRuleAdapter(context: Context) :
        RecyclerAdapter<HighlightRule, ItemHighlightPresetRuleBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemHighlightPresetRuleBinding {
            return ItemHighlightPresetRuleBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemHighlightPresetRuleBinding) {
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::editRule)
            }
            binding.root.setOnLongClickListener {
                getItem(holder.layoutPosition)?.let { showItemMenu(it, binding.root) }
                true
            }
            binding.tvPreview.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::editRule)
            }
            binding.tvEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::editRule)
            }
            binding.tvDelete.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::deleteRule)
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemHighlightPresetRuleBinding,
            item: HighlightRule,
            payloads: MutableList<Any>
        ) {
            binding.tvTitle.text = item.name.ifBlank { getString(R.string.highlight_rule_unnamed) }
            binding.tvDesc.text = item.styleSummary()
            binding.tvPattern.text = "${item.group} / ${item.targetScopeLabel()} / ${item.displayPattern()}"
            binding.tvPreview.text = HighlightRulePreview.build(item)

            val density = binding.root.context.resources.displayMetrics.density
            binding.root.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f * density
                setColor(cardBgColor)
                setStroke((1f * density).toInt().coerceAtLeast(1), cardStrokeColor)
            }
            binding.tvPattern.background?.mutate()?.setTint(accentColor)
            binding.tvPreview.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                setColor(previewBgColor)
                setStroke((1f * density).toInt().coerceAtLeast(1), previewStrokeColor)
            }
            binding.tvEdit.background?.mutate()?.setTint(accentColor)

            binding.switchEnable.setOnCheckedChangeListener(null)
            binding.switchEnable.isChecked = item.enabled
            binding.switchEnable.trackTintList = android.content.res.ColorStateList.valueOf(
                if (item.enabled) accentColor else 0xFF666666.toInt()
            )
            binding.switchEnable.thumbTintList = android.content.res.ColorStateList.valueOf(
                if (item.enabled) accentColor else 0xFF999999.toInt()
            )
            binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
                if (item.enabled != isChecked) {
                    item.enabled = isChecked
                    syncRules()
                }
            }

            binding.tvTitle.setTextColor(primaryTextColor)
            binding.tvDesc.setTextColor(secondaryTextColor)
            binding.tvPattern.setTextColor(
                if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            )
            binding.tvPreviewLabel.setTextColor(secondaryTextColor)
            binding.tvPreview.setTextColor(primaryTextColor)

            (binding.tvEdit.getChildAt(0) as? android.widget.ImageView)
                ?.setColorFilter(
                    if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(),
                    PorterDuff.Mode.SRC_IN
                )
            (binding.tvEdit.getChildAt(1) as? android.widget.TextView)
                ?.setTextColor(
                    if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                )

            (binding.tvDelete.getChildAt(0) as? android.widget.ImageView)
                ?.setColorFilter(context.getColor(R.color.error), PorterDuff.Mode.SRC_IN)
            (binding.tvDelete.getChildAt(1) as? android.widget.TextView)
                ?.setTextColor(context.getColor(R.color.error))
        }
    }
}
