package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemCoverHtmlTemplateBinding
import io.legado.app.help.config.CoverHtmlTemplateConfig
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.views.onClick

/**
 * HTML封面模板列表对话框
 * 
 * 显示所有已保存的HTML封面模板，支持：
 * - 选择当前使用的模板
 * - 新建模板
 * - 编辑模板
 * - 删除模板
 */
class CoverHtmlTemplateListDialog : BaseDialogFragment(R.layout.dialog_recycler_view) {

    val binding by viewBinding(DialogRecyclerViewBinding::bind)

    private val adapter by lazy { TemplateAdapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.cover_html_template)
        binding.toolBar.setNavigationIcon(R.drawable.ic_baseline_close)
        binding.toolBar.setNavigationOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.toolBar.inflateMenu(R.menu.cover_html_template)
        binding.toolBar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.menu_add) {
                showEditDialog(null)
                true
            } else {
                false
            }
        }
        binding.recyclerView.adapter = adapter
        adapter.setItems(CoverHtmlTemplateConfig.templateList)
    }

    /**
     * 显示编辑对话框
     * 
     * @param template 要编辑的模板，为null时表示新建
     */
    private fun showEditDialog(template: CoverHtmlTemplateConfig.Template?) {
        val dialog = CoverHtmlCodeDialog.newInstance(template)
        showDialogFragment(dialog)
    }

    /**
     * 刷新列表
     */
    fun refreshList() {
        adapter.setItems(CoverHtmlTemplateConfig.templateList)
    }

    /**
     * 模板列表适配器
     */
    inner class TemplateAdapter(context: android.content.Context) : 
        RecyclerAdapter<CoverHtmlTemplateConfig.Template, ItemCoverHtmlTemplateBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemCoverHtmlTemplateBinding {
            return ItemCoverHtmlTemplateBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemCoverHtmlTemplateBinding,
            item: CoverHtmlTemplateConfig.Template,
            payloads: MutableList<Any>
        ) {
            binding.tvName.text = item.name
            binding.rbSelected.isChecked = item.isSelected
            binding.swEnable.setOnCheckedChangeListener(null)
            binding.swEnable.isChecked = item.enable

            val previewText = item.htmlCode
                .replace(Regex("<[^>]*>"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(50)
            binding.tvPreview.text = if (previewText.isNotBlank()) {
                "$previewText..."
            } else {
                context.getString(R.string.cover_html_empty)
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemCoverHtmlTemplateBinding) {
            binding.rbSelected.onClick {
                val position = holder.layoutPosition
                val item = getItem(position) ?: return@onClick
                CoverHtmlTemplateConfig.setSelectedTemplate(item.id)
                CoverImageView.clearHtmlCoverCache()
                setItems(CoverHtmlTemplateConfig.templateList)
            }

            binding.swEnable.setOnCheckedChangeListener { _, isChecked ->
                val position = holder.layoutPosition
                val item = getItem(position) ?: return@setOnCheckedChangeListener
                if (item.enable != isChecked) {
                    item.enable = isChecked
                    CoverHtmlTemplateConfig.save()
                    CoverImageView.clearHtmlCoverCache()
                }
            }

            binding.ivEdit.onClick {
                val position = holder.layoutPosition
                val item = getItem(position) ?: return@onClick
                showEditDialog(item)
            }

            binding.ivDelete.onClick {
                val position = holder.layoutPosition
                if (CoverHtmlTemplateConfig.templateList.size <= 1) {
                    return@onClick
                }
                val item = getItem(position) ?: return@onClick
                CoverHtmlTemplateConfig.deleteTemplateById(item.id)
                CoverImageView.clearHtmlCoverCache()
                setItems(CoverHtmlTemplateConfig.templateList)
            }

            holder.itemView.onClick {
                val position = holder.layoutPosition
                val item = getItem(position) ?: return@onClick
                CoverHtmlTemplateConfig.setSelectedTemplate(item.id)
                CoverImageView.clearHtmlCoverCache()
                setItems(CoverHtmlTemplateConfig.templateList)
            }
        }

    }

}
