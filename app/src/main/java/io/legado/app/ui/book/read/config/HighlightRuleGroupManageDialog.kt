package io.legado.app.ui.book.read.config

import android.graphics.PorterDuff
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogHighlightRuleGroupManageBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.observeEvent
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class HighlightRuleGroupManageDialog @JvmOverloads constructor(
    private val onChanged: (oldGroup: String?, newGroup: String?) -> Unit = { _, _ -> },
    private val onSelectGroup: (String?) -> Unit = {},
) : HighlightRuleBottomSheetFragment(
    R.layout.dialog_highlight_rule_group_manage,
    useFadeAnimation = true,
) {

    private val binding by viewBinding(DialogHighlightRuleGroupManageBinding::bind)
    private val adapter by lazy { GroupAdapter(requireContext()) }
    private val groups = ArrayList<String>()
    private val rules = ArrayList<HighlightRule>()
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private var accentColor = 0
    private var cardBgColor = 0

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initTheme()

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.ivBack.setOnClickListener { dismissAllowingStateLoss() }
        binding.tvAddGroup.setOnClickListener { showGroupInputDialog(null) }
        binding.llViewAll.setOnClickListener {
            onSelectGroup(null)
            dismissAllowingStateLoss()
        }
        loadData()
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

        binding.sheetContainer.background?.mutate()?.setTint(bg)
        binding.ivBack.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.ivBack.background?.mutate()?.setTint(cardBgColor)
        binding.tvPageTitle.setTextColor(primaryTextColor)
        binding.tvPageSubtitle.setTextColor(secondaryTextColor)

        binding.tvAddGroup.background?.mutate()?.setTint(accentColor)
        binding.tvAddGroup.setTextColor(
            if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        )

        binding.tvEmptyMsg.setTextColor(secondaryTextColor)
        
        binding.llViewAll.background?.mutate()?.setTint(cardBgColor)
        binding.tvAllCount.setTextColor(secondaryTextColor)
    }

    private fun loadData() {
        groups.clear()
        groups.addAll(HighlightRuleGroupStore.load(requireContext()))
        rules.clear()
        rules.addAll(HighlightRuleStore.load(requireContext()))
        adapter.setItems(groups.toList())
        binding.tvEmptyMsg.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
        binding.tvAllCount.text = "${rules.size} 条规则"
    }

    private fun showGroupInputDialog(source: String?) {
        val editText = EditText(requireContext()).apply {
            setText(source.orEmpty())
            setSelection(text.length)
            hint = "输入分组名称"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(), 8.dpToPx(), 20.dpToPx(), 0)
            addView(
                editText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        alert(if (source == null) "新增分组" else "重命名分组") {
            customView { container }
            okButton {
                val newName = editText.text?.toString()?.trim().orEmpty()
                if (newName.isBlank()) {
                    requireContext().toastOnUi("分组名称不能为空")
                    return@okButton
                }
                if (groups.contains(newName) && newName != source) {
                    requireContext().toastOnUi("分组名称已存在")
                    return@okButton
                }
                if (source == null) {
                    groups.add(newName)
                    HighlightRuleGroupStore.save(requireContext(), groups)
                    loadData()
                    onChanged(null, null)
                } else {
                    val index = groups.indexOf(source)
                    if (index >= 0) groups[index] = newName
                    rules.replaceAll { rule ->
                        if (rule.group == source) rule.copy(group = newName) else rule
                    }
                    HighlightRuleGroupStore.save(requireContext(), groups)
                    HighlightRuleStore.save(requireContext(), rules)
                    loadData()
                    onChanged(source, newName)
                }
            }
            cancelButton()
        }
    }

    private fun deleteGroup(group: String) {
        if (group == HighlightRuleGroupStore.DEFAULT_GROUP) {
            context?.toastOnUi("默认分组不能删除")
            return
        }
        alert("删除分组") {
            setMessage("删除后，该分组下的规则会移动到默认分组。")
            okButton {
                groups.remove(group)
                rules.replaceAll { rule ->
                    if (rule.group == group) {
                        rule.copy(group = HighlightRuleGroupStore.DEFAULT_GROUP)
                    } else {
                        rule
                    }
                }
                HighlightRuleGroupStore.save(requireContext(), groups)
                HighlightRuleStore.save(requireContext(), rules)
                loadData()
                onChanged(group, null)
            }
            cancelButton()
        }
    }

    private fun exportGroup(group: String) {
        val targetRules = rules.filter { it.group == group }
        if (targetRules.isEmpty()) {
            context?.toastOnUi("璇ュ垎缁勬殏鏃犺鍒欏彲瀵煎嚭")
            return
        }
        requireContext().sendToClip(GSON.toJson(targetRules))
        context?.toastOnUi("宸插鍒?${targetRules.size} 鏉¤鍒?")
    }

    private fun showItemMenu(group: String, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.highlight_rule_group_item, menu)
            if (group == HighlightRuleGroupStore.DEFAULT_GROUP) {
                menu.findItem(R.id.menu_delete)?.isVisible = false
            }
            setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.menu_rename_group -> showGroupInputDialog(group)
                    R.id.menu_export_group -> exportGroup(group)
                    R.id.menu_delete -> deleteGroup(group)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }.show()
    }

    private fun groupCount(group: String): Int {
        return rules.count { it.group == group }
    }

    private inner class GroupAdapter(context: android.content.Context) :
        RecyclerAdapter<String, ViewBindingHolder>(context) {

        override fun getViewBinding(parent: ViewGroup): ViewBindingHolder {
            val view = inflater.inflate(R.layout.item_highlight_rule_group, parent, false)
            return ViewBindingHolder(view)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ViewBindingHolder) {
            binding.itemRoot.setOnClickListener {
                getItem(holder.layoutPosition)?.let { group ->
                    onSelectGroup(group)
                    dismissAllowingStateLoss()
                }
            }
            binding.itemRoot.setOnLongClickListener {
                getItem(holder.layoutPosition)?.let { group ->
                    showItemMenu(group, binding.itemRoot)
                }
                true
            }
            binding.tvEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::showGroupInputDialog)
            }
            binding.tvDelete.setOnClickListener {
                getItem(holder.layoutPosition)?.let { group ->
                    showItemMenu(group, binding.tvDelete)
                }
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ViewBindingHolder,
            item: String,
            payloads: MutableList<Any>
        ) {
            binding.itemRoot.background?.mutate()?.setTint(cardBgColor)
            binding.tvEdit.background?.mutate()?.setTint(accentColor)

            binding.tvTitle.text = item
            binding.tvTitle.setTextColor(primaryTextColor)
            binding.tvCount.text = "${groupCount(item)} 条规则"
            binding.tvCount.setTextColor(secondaryTextColor)
            binding.tvEdit.setTextColor(
                if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            )
            binding.tvDelete.setTextColor(primaryTextColor)
            binding.tvDelete.visibility = View.VISIBLE
        }
    }

    class ViewBindingHolder(view: View) : androidx.viewbinding.ViewBinding {
        override fun getRoot(): View = itemRoot
        val itemRoot: View = view
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
        val tvCount: TextView = view.findViewById(R.id.tv_count)
        val tvEdit: TextView = view.findViewById(R.id.tv_edit)
        val tvDelete: TextView = view.findViewById(R.id.tv_delete)
    }
}
