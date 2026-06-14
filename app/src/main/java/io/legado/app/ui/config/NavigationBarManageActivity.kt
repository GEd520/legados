package io.legado.app.ui.config

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ActivityNavigationBarManageBinding
import io.legado.app.databinding.ItemNavBarConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 底栏管理页面 - 参考 mmr 项目的 NavigationBarManagePage
 */
class NavigationBarManageActivity : BaseActivity<ActivityNavigationBarManageBinding>() {

    override val binding by viewBinding(ActivityNavigationBarManageBinding::inflate)
    private val adapter by lazy { Adapter(this) }
    private val configs = mutableListOf<NavigationBarConfig>()
    private var isNightMode = false
    private var activeConfigId: String? = null

    companion object {
        private const val PREF_KEY_IS_NIGHT = "navBarIsNight"
        private const val PREF_KEY_ACTIVE_DAY = "activeDayNavBarId"
        private const val PREF_KEY_ACTIVE_NIGHT = "activeNightNavBarId"
        private const val PREF_KEY_CUSTOM_CONFIGS = "customNavBarConfigs"
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        initTabs()
        loadConfigs()
    }

    private fun initView() = binding.run {
        recyclerView.layoutManager = LinearLayoutManager(this@NavigationBarManageActivity)
        recyclerView.addItemDecoration(VerticalDivider(this@NavigationBarManageActivity))
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
        val accentColor = primaryColor
        val ctx = this
        binding.apply {
        val primaryTextColor = ContextCompat.getColor(ctx, R.color.primaryText)
        
        tvTabDay.setTextColor(if (!isNightMode) accentColor else primaryTextColor)
        tabDay.background = if (!isNightMode) {
            ContextCompat.getDrawable(ctx, R.drawable.bg_theme_tab_selected)
        } else {
            null
        }
        
        tvTabNight.setTextColor(if (isNightMode) accentColor else primaryTextColor)
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
            tvSummary.text = ctx.getString(R.string.nav_bar_summary_empty, themeType)
        } else {
            tvSummary.text = ctx.getString(R.string.nav_bar_summary)
        }
        }
    }

    private fun getFilteredConfigs(): List<NavigationBarConfig> {
        return configs.filter { it.isNight == isNightMode }
    }

    private fun loadConfigs() {
        configs.clear()
        
        // 加载内置底栏包
        configs.add(NavigationBarConfig.createDefaultDay())
        configs.add(NavigationBarConfig.createDefaultNight())
        
        // 加载自定义底栏包
        val customConfigJsons = getPrefString(PREF_KEY_CUSTOM_CONFIGS)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        for (json in customConfigJsons) {
            try {
                configs.add(NavigationBarConfig.fromJson(json))
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

    private fun editConfig(existing: NavigationBarConfig?) {
        val isEdit = existing != null
        val wasActive = isEdit && existing?.id == activeConfigId
        
        val config = existing ?: NavigationBarConfig(
            id = "custom_${System.currentTimeMillis()}",
            name = getNextConfigName(),
            isNight = isNightMode,
            isBuiltin = false,
            layoutMode = "floating",
            effectMode = "glass",
            opacity = 100
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

    private fun showEditDialog(config: NavigationBarConfig, isEdit: Boolean, onSave: (NavigationBarConfig) -> Unit) {
        val context = this
        val items = mutableListOf<String>()
        
        // 构建编辑选项
        items.add("${getString(R.string.name)}: ${config.name}")
        items.add("${getString(R.string.layout_mode)}: ${config.getLayoutModeText()}")
        if (config.layoutMode == "floating") {
            items.add("${getString(R.string.effect_mode)}: ${config.getEffectModeText()}")
        }
        items.add("${getString(R.string.opacity)}: ${config.opacity}%")
        
        alert(if (isEdit) R.string.edit else R.string.add) {
            customView {
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 16, 16, 16)
                    
                    for (item in items) {
                        TextView(context).apply {
                            text = item
                            textSize = 14f
                            setPadding(8, 8, 8, 8)
                        }.also { addView(it) }
                    }
                }
            }
            okButton {
                onSave(config)
            }
            cancelButton()
        }
    }

    private fun getNextConfigName(): String {
        val base = getString(R.string.custom_nav_bar)
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
                val config = NavigationBarConfig.fromJson(clipText)
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

    private fun applyConfig(config: NavigationBarConfig) {
        activeConfigId = config.id
        saveConfigs()
        adapter.setItems(getFilteredConfigs())
        toastOnUi(getString(R.string.applied_nav_bar_config, config.name))
    }

    private fun exportConfig(config: NavigationBarConfig) {
        val json = config.toJson()
        share(json, getString(R.string.share_nav_bar_config))
    }

    private fun deleteConfig(config: NavigationBarConfig) {
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

    private fun showMoreOptions(config: NavigationBarConfig) {
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
        RecyclerAdapter<NavigationBarConfig, ItemNavBarConfigBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemNavBarConfigBinding {
            return ItemNavBarConfigBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemNavBarConfigBinding,
            item: NavigationBarConfig,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                tvName.text = item.name
                
                // 内置标签
                tvBuiltin.visibility = if (item.isBuiltin) View.VISIBLE else View.GONE
                
                // 构建信息文本
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateStr = dateFormat.format(Date(item.updatedAt))
                
                var infoText = item.getLayoutModeText()
                if (item.layoutMode == "floating") {
                    infoText += " · ${item.getEffectModeText()}"
                }
                if (item.layoutMode != "sidebar") {
                    infoText += " · ${getString(R.string.opacity)} ${item.opacity}%"
                }
                infoText += " · $dateStr"
                
                val isActive = item.id == activeConfigId
                if (isActive) {
                    infoText = "${getString(R.string.current_applied)} · $infoText"
                }
                
                tvInfo.text = infoText
                
                // 应用按钮
                tvApply.text = if (isActive) getString(R.string.applied) else getString(R.string.apply)
                if (isActive) {
                    tvApply.setTextColor(ContextCompat.getColor(context, R.color.error))
                } else {
                    tvApply.setTextColor(ContextCompat.getColor(context, R.color.primaryText))
                }
                
                // 编辑按钮 (内置配置不显示)
                tvEdit.visibility = if (item.isBuiltin) View.GONE else View.VISIBLE
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemNavBarConfigBinding) {
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