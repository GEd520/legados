package io.legado.app.ui.config

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ActivityTopBarManageBinding
import io.legado.app.databinding.ItemTopBarConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 顶栏管理页面 - 参考 mmr 项目的 TopBarManagePage
 */
class TopBarManageActivity : BaseActivity<ActivityTopBarManageBinding>() {

    override val binding by viewBinding(ActivityTopBarManageBinding::inflate)
    private val adapter by lazy { Adapter(this) }
    private val configs = mutableListOf<TopBarConfig>()
    private var isNightMode = false
    private var activeConfigId: String? = null

    companion object {
        private const val PREF_KEY_IS_NIGHT = "topBarIsNight"
        private const val PREF_KEY_ACTIVE_DAY = "activeDayTopBarId"
        private const val PREF_KEY_ACTIVE_NIGHT = "activeNightTopBarId"
        private const val PREF_KEY_CUSTOM_CONFIGS = "customTopBarConfigs"
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        initTabs()
        loadConfigs()
    }

    private fun initView() = binding.run {
        recyclerView.layoutManager = LinearLayoutManager(this@TopBarManageActivity)
        recyclerView.addItemDecoration(VerticalDivider(this@TopBarManageActivity))
        recyclerView.adapter = adapter
        
        tvAddConfig.setOnClickListener {
            showAddOptions()
        }
    }

    private fun initTabs() = binding.run {
        isNightMode = AppConfig.isNightTheme
        updateTabSelection()
        
        tabDay.setOnClickListener {
            if (isNightMode) {
                isNightMode = false
                updateTabSelection()
                loadConfigs()
            }
        }
        
        tabNight.setOnClickListener {
            if (!isNightMode) {
                isNightMode = true
                updateTabSelection()
                loadConfigs()
            }
        }
    }

    private fun updateTabSelection() {
        val activeColor = accentColor
        val ctx = this
        binding.apply {
        val primaryTextColor = ContextCompat.getColor(ctx, R.color.primaryText)
        
        tvTabDay.setTextColor(if (!isNightMode) activeColor else primaryTextColor)
        tabDay.background = if (!isNightMode) {
            ContextCompat.getDrawable(ctx, R.drawable.bg_theme_tab_selected)
        } else {
            null
        }
        
        tvTabNight.setTextColor(if (isNightMode) activeColor else primaryTextColor)
        tabNight.background = if (isNightMode) {
            ContextCompat.getDrawable(ctx, R.drawable.bg_theme_tab_selected)
        } else {
            null
        }
        }
    }

    private fun updateSummary() {
        val ctx = this
        binding.apply {
        val filteredConfigs = getFilteredConfigs()
        if (filteredConfigs.isEmpty()) {
            val themeType = if (isNightMode) ctx.getString(R.string.night) else ctx.getString(R.string.day)
            tvSummary.text = ctx.getString(R.string.top_bar_summary_empty, themeType)
        } else {
            tvSummary.text = ctx.getString(R.string.top_bar_summary)
        }
        }
    }

    private fun getFilteredConfigs(): List<TopBarConfig> {
        return configs.filter { it.isNight == isNightMode }
    }

    private fun loadConfigs() {
        configs.clear()
        
        // 加载内置顶栏包
        configs.add(TopBarConfig.createDefaultDay())
        configs.add(TopBarConfig.createDefaultNight())
        
        // 加载自定义顶栏包
        val customConfigJsons = getPrefString(PREF_KEY_CUSTOM_CONFIGS)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        for (json in customConfigJsons) {
            try {
                configs.add(TopBarConfig.fromJson(json))
            } catch (e: Exception) {
                // 忽略解析失败的配置
            }
        }
        
        // 获取当前激活的配置ID
        activeConfigId = getPrefString(if (isNightMode) PREF_KEY_ACTIVE_NIGHT else PREF_KEY_ACTIVE_DAY)
        
        // 如果没有激活的配置，默认激活第一个
        if (activeConfigId.isNullOrEmpty()) {
            activeConfigId = getFilteredConfigs().firstOrNull()?.id
        }
        
        adapter.setItems(getFilteredConfigs())
        updateSummary()
    }

    private fun saveConfigs() {
        putPrefBoolean(PREF_KEY_IS_NIGHT, isNightMode)
        putPrefString(if (isNightMode) PREF_KEY_ACTIVE_NIGHT else PREF_KEY_ACTIVE_DAY, activeConfigId ?: "")
        
        val customConfigJsons = configs.filter { !it.isBuiltin }.map { it.toJson() }
        putPrefString(PREF_KEY_CUSTOM_CONFIGS, customConfigJsons.joinToString("\n"))
    }

    private fun showAddOptions() {
        val items = listOf(
            getString(R.string.manual_config),
            getString(R.string.import_str)
        )
        selector(items = items) { _, i ->
            when (i) {
                0 -> addConfig()
                1 -> importConfig()
            }
        }
    }

    private fun addConfig() {
        editConfig(null)
    }

    private fun editConfig(existing: TopBarConfig?) {
        val isEdit = existing != null
        val wasActive = existing?.id == activeConfigId
        
        val config = existing ?: TopBarConfig(
            id = "custom_${System.currentTimeMillis()}",
            name = getNextConfigName(),
            isNight = isNightMode,
            isBuiltin = false,
            style = "default",
            cornerScale = 1.0f,
            tagBarAlpha = 100,
            tagSelectedAlpha = 100,
            wallpaperAlpha = 100
        )
        
        // 显示编辑对话框
        showEditDialog(config, isEdit) { updatedConfig ->
            if (isEdit) {
                val index = configs.indexOfFirst { it.id == updatedConfig.id }
                if (index >= 0) {
                    configs[index] = updatedConfig
                }
            } else {
                configs.add(updatedConfig)
            }
            saveConfigs()
            adapter.setItems(getFilteredConfigs())
            updateSummary()
            
            // 如果编辑的是当前已应用的配置，重新应用
            if (wasActive) {
                applyConfig(updatedConfig)
            }
        }
    }

    private fun showEditDialog(config: TopBarConfig, isEdit: Boolean, onSave: (TopBarConfig) -> Unit) {
        val root = buildEditViewClean(config)
        alert(if (isEdit) R.string.edit else R.string.add) {
            customView { root }
            okButton {
                val name = root.findViewWithTag<EditText>("name")
                    ?.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                if (name.isBlank()) {
                    toastOnUi(R.string.input_is_empty)
                    return@okButton
                }
                config.name = name
                config.wallpaperPath = root.findViewWithTag<EditText>("wallpaperPath")
                    ?.text
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                onSave(config)
            }
            cancelButton()
        }
    }

    private fun buildEditView(config: TopBarConfig): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            addView(EditText(context).apply {
                tag = "name"
                hint = getString(R.string.name)
                setText(config.name)
                setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    48.dp
                )
            })
            addView(optionRow(getString(R.string.top_bar_style), topBarStyleText(config)) {
                selector(items = listOf("默认顶栏", "常规顶栏")) { _, index ->
                    config.style = if (index == 1) "regular" else "default"
                }
            })
            addView(optionRow(getString(R.string.corner_scale), String.format(Locale.ROOT, "%.1f", config.cornerScale)) {
                NumberPickerDialog(this@TopBarManageActivity, isDecimalMode = true)
                    .setTitle(getString(R.string.corner_scale))
                    .setMinValue(0)
                    .setMaxValue(30)
                    .setValue((config.cornerScale.coerceIn(0f, 3f) * 10).toInt())
                    .show {
                        config.cornerScale = (it / 10f).coerceIn(0f, 3f)
                    }
            })
            addView(optionRow(getString(R.string.tag_bar_opacity), "${config.tagBarAlpha}%") {
                editPercent(getString(R.string.tag_bar_opacity), config.tagBarAlpha) {
                    config.tagBarAlpha = it
                }
            })
            addView(optionRow(getString(R.string.tag_selected_opacity), "${config.tagSelectedAlpha}%") {
                editPercent(getString(R.string.tag_selected_opacity), config.tagSelectedAlpha) {
                    config.tagSelectedAlpha = it
                }
            })
            addView(EditText(context).apply {
                tag = "wallpaperPath"
                hint = getString(R.string.wallpaper)
                setText(config.wallpaperPath.orEmpty())
                setSingleLine(false)
                minLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    58.dp
                ).apply { topMargin = 8.dp }
            })
            addView(optionRow("${getString(R.string.wallpaper)} ${getString(R.string.opacity)}", "${config.wallpaperAlpha}%") {
                editPercent("${getString(R.string.wallpaper)} ${getString(R.string.opacity)}", config.wallpaperAlpha) {
                    config.wallpaperAlpha = it
                }
            })
            addView(switchRow("筛选栏默认展开", config.expandFiltersByDefault) {
                config.expandFiltersByDefault = !config.expandFiltersByDefault
            })
        }
    }

    private fun buildEditViewClean(config: TopBarConfig): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            addView(EditText(context).apply {
                tag = "name"
                hint = getString(R.string.name)
                setText(config.name)
                setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    48.dp
                )
            })
            addView(optionRow(getString(R.string.top_bar_style), topBarStyleText(config)) {
                selector(items = listOf(getString(R.string.default_top_bar), getString(R.string.regular_top_bar))) { _, index ->
                    config.style = if (index == 1) "regular" else "default"
                }
            })
            addView(optionRow(getString(R.string.corner_scale), String.format(Locale.ROOT, "%.1f", config.cornerScale)) {
                NumberPickerDialog(this@TopBarManageActivity, isDecimalMode = true)
                    .setTitle(getString(R.string.corner_scale))
                    .setMinValue(0)
                    .setMaxValue(30)
                    .setValue((config.cornerScale.coerceIn(0f, 3f) * 10).toInt())
                    .show {
                        config.cornerScale = (it / 10f).coerceIn(0f, 3f)
                    }
            })
            addView(optionRow(getString(R.string.tag_bar_opacity), "${config.tagBarAlpha}%") {
                editPercent(getString(R.string.tag_bar_opacity), config.tagBarAlpha) {
                    config.tagBarAlpha = it
                }
            })
            addView(optionRow(getString(R.string.tag_selected_opacity), "${config.tagSelectedAlpha}%") {
                editPercent(getString(R.string.tag_selected_opacity), config.tagSelectedAlpha) {
                    config.tagSelectedAlpha = it
                }
            })
            addView(EditText(context).apply {
                tag = "wallpaperPath"
                hint = getString(R.string.wallpaper)
                setText(config.wallpaperPath.orEmpty())
                setSingleLine(false)
                minLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    58.dp
                ).apply { topMargin = 8.dp }
            })
            addView(optionRow("${getString(R.string.wallpaper)} ${getString(R.string.opacity)}", "${config.wallpaperAlpha}%") {
                editPercent("${getString(R.string.wallpaper)} ${getString(R.string.opacity)}", config.wallpaperAlpha) {
                    config.wallpaperAlpha = it
                }
            })
            addView(switchRow(getString(R.string.expand_filters_by_default), config.expandFiltersByDefault) {
                config.expandFiltersByDefault = !config.expandFiltersByDefault
            })
        }
    }

    private fun optionRow(title: String, value: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp, 0, 14.dp, 0)
            background = ContextCompat.getDrawable(context, R.drawable.bg_config_card)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                46.dp
            ).apply { topMargin = 8.dp }
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.primaryText))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = value
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.secondaryText))
            })
            setOnClickListener { onClick() }
        }
    }

    private fun switchRow(title: String, checked: Boolean, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp, 0, 8.dp, 0)
            background = ContextCompat.getDrawable(context, R.drawable.bg_config_card)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                46.dp
            ).apply { topMargin = 8.dp }
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.primaryText))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Switch(context).apply {
                isChecked = checked
                isClickable = false
            })
            setOnClickListener {
                onClick()
                (getChildAt(1) as? Switch)?.isChecked = !(getChildAt(1) as Switch).isChecked
            }
        }
    }

    private fun editPercent(title: String, value: Int, onValue: (Int) -> Unit) {
        NumberPickerDialog(this)
            .setTitle(title)
            .setMinValue(0)
            .setMaxValue(100)
            .setValue(value.coerceIn(0, 100))
            .show { onValue(it) }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun topBarStyleText(config: TopBarConfig): String {
        return if (config.style == "regular") {
            getString(R.string.regular_top_bar)
        } else {
            getString(R.string.default_top_bar)
        }
    }

    private fun getNextConfigName(): String {
        val base = getString(R.string.custom_top_bar)
        val usedNames = configs.map { it.name }.toSet()
        if (!usedNames.contains(base)) return base
        for (index in 2..999) {
            val name = "$base $index"
            if (!usedNames.contains(name)) return name
        }
        return "$base ${System.currentTimeMillis()}"
    }

    private fun importConfig() {
        getClipText()?.let { clipText ->
            try {
                val config = TopBarConfig.fromJson(clipText)
                configs.add(config)
                saveConfigs()
                adapter.setItems(getFilteredConfigs())
                updateSummary()
                toastOnUi(R.string.import_success)
            } catch (e: Exception) {
                toastOnUi(R.string.import_failed)
            }
        } ?: toastOnUi(R.string.clipboard_empty)
    }

    private fun applyConfig(config: TopBarConfig) {
        activeConfigId = config.id
        saveConfigs()
        adapter.setItems(getFilteredConfigs())
        toastOnUi(getString(R.string.applied_top_bar_config, config.name))
    }

    private fun exportConfig(config: TopBarConfig) {
        val json = config.toJson()
        share(json, getString(R.string.share_top_bar_config))
    }

    private fun deleteConfig(config: TopBarConfig) {
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                configs.remove(config)
                saveConfigs()
                adapter.setItems(getFilteredConfigs())
                updateSummary()
            }
            noButton()
        }
    }

    private fun showMoreOptions(config: TopBarConfig) {
        val items = mutableListOf<String>()
        items.add(getString(R.string.apply))
        if (!config.isBuiltin) {
            items.add(getString(R.string.edit))
            items.add(getString(R.string.export_str))
        }
        if (!config.isBuiltin && config.id != activeConfigId) {
            items.add(getString(R.string.delete))
        }
        
        selector(items = items) { _, i ->
            when (items[i]) {
                getString(R.string.apply) -> applyConfig(config)
                getString(R.string.edit) -> editConfig(config)
                getString(R.string.export_str) -> exportConfig(config)
                getString(R.string.delete) -> deleteConfig(config)
            }
        }
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<TopBarConfig, ItemTopBarConfigBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemTopBarConfigBinding {
            return ItemTopBarConfigBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTopBarConfigBinding,
            item: TopBarConfig,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                tvName.text = item.name
                
                // 内置标签
                tvBuiltin.visibility = if (item.isBuiltin) View.VISIBLE else View.GONE
                
                // 构建信息文本
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateStr = dateFormat.format(Date(item.updatedAt))
                
                var infoText = topBarStyleText(item)
                if (item.style == "regular") {
                    infoText += " · ${getString(R.string.corner_scale)} ${item.cornerScale}"
                    val wp = item.wallpaperPath
                    if (wp != null && wp.isNotEmpty()) {
                        infoText += " · ${getString(R.string.wallpaper)}"
                    }
                }
                infoText += " · ${getString(R.string.tag_bar_opacity)} ${item.tagBarAlpha}%"
                infoText += " · $dateStr"
                
                val isActive = item.id == activeConfigId
                if (isActive) {
                    infoText = "${getString(R.string.current_applied)} · $infoText"
                }
                
                tvInfo.text = infoText
                
                // 应用按钮
                tvApply.text = if (isActive) getString(R.string.applied) else getString(R.string.apply)
                tvApply.setTextColor(
                    if (isActive) accentColor else ContextCompat.getColor(context, R.color.primaryText)
                )
                
                // 编辑按钮 (内置配置不显示)
                tvEdit.visibility = if (item.isBuiltin) View.GONE else View.VISIBLE
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTopBarConfigBinding) {
            binding.apply {
                tvApply.setOnClickListener {
                    val position = holder.layoutPosition
                    val filteredConfigs = getFilteredConfigs()
                    if (position < filteredConfigs.size) {
                        applyConfig(filteredConfigs[position])
                    }
                }
                tvEdit.setOnClickListener {
                    val position = holder.layoutPosition
                    val filteredConfigs = getFilteredConfigs()
                    if (position < filteredConfigs.size) {
                        editConfig(filteredConfigs[position])
                    }
                }
                tvMore.setOnClickListener {
                    val position = holder.layoutPosition
                    val filteredConfigs = getFilteredConfigs()
                    if (position < filteredConfigs.size) {
                        showMoreOptions(filteredConfigs[position])
                    }
                }
            }
        }
    }
}
