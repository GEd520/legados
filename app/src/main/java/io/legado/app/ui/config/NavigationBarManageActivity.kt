package io.legado.app.ui.config

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ActivityNavigationBarManageBinding
import io.legado.app.databinding.ItemNavBarConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.getClipText
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 底栏管理页面 - 参考 mmr 项目的 NavigationBarManagePage.
 */
class NavigationBarManageActivity : BaseActivity<ActivityNavigationBarManageBinding>(),
    ColorPickerDialogListener {

    override val binding by viewBinding(ActivityNavigationBarManageBinding::inflate)
    private val adapter by lazy { Adapter(this) }
    private val configs = mutableListOf<NavigationBarConfig>()
    private var isNightMode = false
    private var activeConfigId: String? = null
    private var editingConfig: NavigationBarConfig? = null
    private var editingDialog: LinearLayout? = null
    private var pendingIconRequest: IconRequest? = null

    companion object {
        private const val PREF_KEY_IS_NIGHT = "navBarIsNight"
        private const val COLOR_DIALOG_BORDER = 1
    }

    private val selectIcon = registerForActivityResult(HandleFileContract()) { result ->
        val request = pendingIconRequest?.takeIf { it.requestCode == result.requestCode } ?: return@registerForActivityResult
        val uri = result.uri ?: return@registerForActivityResult
        kotlin.runCatching {
            val config = editingConfig ?: error(getString(R.string.navigation_bar_config_missing))
            val file = externalFiles
                .getFile("navigationBarIcons", config.id)
                .apply { mkdirs() }
                .getFile("${request.item.key}_${request.state}.${uri.lastPathSegment?.substringAfterLast('.', "png") ?: "png"}")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: error(getString(R.string.file_not_exist))
            config.icons = config.icons.toMutableMap().apply {
                put(NavigationBarConfig.iconKey(request.item.key, request.state), file.absolutePath)
            }
        }.onSuccess {
            refreshEditDialog()
        }.onFailure {
            toastOnUi(it.localizedMessage)
        }
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
        isNightMode = getPrefBoolean(PREF_KEY_IS_NIGHT, AppConfig.isNightTheme)
        updateTabSelection()
        tabDay.setOnClickListener {
            if (isNightMode) {
                isNightMode = false
                putPrefBoolean(PREF_KEY_IS_NIGHT, isNightMode)
                updateTabSelection()
                loadConfigs()
            }
        }
        tabNight.setOnClickListener {
            if (!isNightMode) {
                isNightMode = true
                putPrefBoolean(PREF_KEY_IS_NIGHT, isNightMode)
                updateTabSelection()
                loadConfigs()
            }
        }
    }

    private fun updateTabSelection() {
        val activeColor = accentColor
        val primaryTextColor = ContextCompat.getColor(this, R.color.primaryText)
        binding.apply {
            tvTabDay.setTextColor(if (!isNightMode) activeColor else primaryTextColor)
            tabDay.background = if (!isNightMode) {
                ContextCompat.getDrawable(this@NavigationBarManageActivity, R.drawable.bg_theme_tab_selected)
            } else {
                null
            }
            tvTabNight.setTextColor(if (isNightMode) activeColor else primaryTextColor)
            tabNight.background = if (isNightMode) {
                ContextCompat.getDrawable(this@NavigationBarManageActivity, R.drawable.bg_theme_tab_selected)
            } else {
                null
            }
        }
    }

    private fun updateSummary() {
        val filteredConfigs = getFilteredConfigs()
        binding.tvSummary.text = if (filteredConfigs.isEmpty()) {
            val themeType = if (isNightMode) getString(R.string.night) else getString(R.string.day)
            getString(R.string.nav_bar_summary_empty, themeType)
        } else {
            getString(R.string.nav_bar_summary)
        }
    }

    private fun getFilteredConfigs(): List<NavigationBarConfig> {
        return configs.filter { it.isNight == isNightMode }
    }

    private fun loadConfigs() {
        configs.clear()
        configs.addAll(NavigationBarConfig.loadConfigs(this))
        activeConfigId = NavigationBarConfig.activeId(this, isNightMode)
        if (activeConfigId.isNullOrEmpty()) {
            activeConfigId = getFilteredConfigs().firstOrNull()?.id
            NavigationBarConfig.setActiveId(this, isNightMode, activeConfigId)
        }
        adapter.setItems(getFilteredConfigs())
        updateSummary()
    }

    private fun saveConfigs() {
        putPrefBoolean(PREF_KEY_IS_NIGHT, isNightMode)
        NavigationBarConfig.setActiveId(this, isNightMode, activeConfigId)
        NavigationBarConfig.saveConfigs(this, configs)
    }

    private fun showAddOptions() {
        val items = listOf(
            getString(R.string.manual_config),
            getString(R.string.import_str)
        )
        selector(items = items) { _, i ->
            when (i) {
                0 -> editConfig(null)
                1 -> importConfig()
            }
        }
    }

    private fun editConfig(existing: NavigationBarConfig?) {
        val isEdit = existing != null
        val wasActive = existing?.id == activeConfigId
        val config = existing?.copySelf() ?: NavigationBarConfig(
            id = "custom_${System.currentTimeMillis()}",
            name = getNextConfigName(),
            isNight = isNightMode,
            isBuiltin = false,
            layoutMode = NavigationBarConfig.LAYOUT_FLOATING,
            effectMode = NavigationBarConfig.EFFECT_GLASS,
            opacity = 100
        )

        showEditDialog(config, isEdit) { updatedConfig ->
            updatedConfig.updatedAt = System.currentTimeMillis()
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
            if (wasActive) {
                applyConfig(updatedConfig)
            }
        }
    }

    private fun showEditDialog(
        config: NavigationBarConfig,
        isEdit: Boolean,
        onSave: (NavigationBarConfig) -> Unit
    ) {
        editingConfig = config
        val root = buildEditView(config)
        editingDialog = root
        alert(if (isEdit) R.string.edit else R.string.add) {
            customView { root }
            okButton {
                root.findViewWithTag<TextView>("name")?.text?.toString()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { config.name = it }
                onSave(config)
            }
            cancelButton()
        }
    }

    private fun buildEditView(config: NavigationBarConfig): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            addView(EditText(context).apply {
                tag = "name"
                hint = getString(R.string.navigation_bar_name)
                setText(config.name)
                setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    48.dp
                )
            })
            addView(optionRow(getString(R.string.bottom_bar_layout_mode), config.getLayoutModeText()) {
                selectLayoutModeClean(config)
            })
            if (config.layoutMode == NavigationBarConfig.LAYOUT_FLOATING) {
                addView(optionRow(getString(R.string.bottom_bar_effect_mode), config.getEffectModeText()) {
                    selectEffectModeClean(config)
                })
            }
            if (config.layoutMode != NavigationBarConfig.LAYOUT_SIDEBAR) {
                addView(optionRow(getString(R.string.opacity), "${config.opacity}%") {
                    editOpacity(config)
                })
                addView(optionRow(getString(R.string.bottom_bar_border_color), config.borderColorText()) {
                    selectBorderColor(config)
                })
                if (config.borderColor != null) {
                    addView(optionRow(getString(R.string.bottom_bar_border_alpha), "${config.borderAlpha}%") {
                        editBorderAlpha(config)
                    })
                }
            }
            NavigationBarConfig.items.forEach { item ->
                addView(iconRow(config, item))
            }
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

    private fun iconRow(config: NavigationBarConfig, item: NavigationBarConfig.NavItem): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp, 0, 8.dp, 0)
            background = ContextCompat.getDrawable(context, R.drawable.bg_config_card)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                54.dp
            ).apply { topMargin = 8.dp }
            addView(TextView(context).apply {
                setText(item.titleRes)
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.primaryText))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(iconButton(config, item, NavigationBarConfig.STATE_NORMAL, false))
            addView(iconButton(config, item, NavigationBarConfig.STATE_SELECTED, true))
        }
    }

    private fun iconButton(
        config: NavigationBarConfig,
        item: NavigationBarConfig.NavItem,
        state: String,
        selected: Boolean
    ): ImageView {
        return ImageView(this).apply {
            contentDescription = getString(if (selected) R.string.navigation_icon_selected else R.string.navigation_icon_normal)
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageDrawable(NavigationBarConfig.previewDrawable(this@NavigationBarManageActivity, config, item, selected))
            background = ContextCompat.getDrawable(context, R.drawable.bg_action_button)
            layoutParams = LinearLayout.LayoutParams(44.dp, 44.dp).apply { marginStart = 8.dp }
            setOnClickListener {
                val actions = listOf(getString(R.string.select_image), getString(R.string.delete))
                selector(contentDescription, actions) { _, index ->
                    if (index == 0) {
                        val code = NavigationBarConfig.items.indexOf(item) * 2 + if (selected) 1 else 0
                        pendingIconRequest = IconRequest(code, item, state)
                        selectIcon.launch {
                            mode = HandleFileContract.FILE
                            requestCode = code
                            title = contentDescription.toString()
                            allowExtensions = arrayOf("png", "jpg", "jpeg")
                        }
                    } else {
                        config.icons = config.icons.toMutableMap().apply {
                            remove(NavigationBarConfig.iconKey(item.key, state))
                        }
                        refreshEditDialog()
                    }
                }
            }
        }
    }

    private fun refreshEditDialog() {
        val config = editingConfig ?: return
        val root = editingDialog ?: return
        root.findViewWithTag<EditText>("name")
            ?.text
            ?.toString()
            ?.trim()
            ?.let { config.name = it }
        root.removeAllViews()
        buildEditView(config).let { rebuilt ->
            while (rebuilt.childCount > 0) {
                val child = rebuilt.getChildAt(0)
                rebuilt.removeViewAt(0)
                root.addView(child)
            }
        }
    }

    private fun selectLayoutModeClean(config: NavigationBarConfig) {
        val modes = listOf(
            NavigationBarConfig.LAYOUT_FLOATING,
            NavigationBarConfig.LAYOUT_STANDARD,
            NavigationBarConfig.LAYOUT_SIDEBAR
        )
        val labels = listOf(
            getString(R.string.floating_bottom_bar),
            getString(R.string.standard_bottom_bar),
            getString(R.string.side_bar)
        )
        selector(items = labels) { _, i ->
            config.layoutMode = modes[i]
            if (config.layoutMode == NavigationBarConfig.LAYOUT_STANDARD) {
                config.effectMode = NavigationBarConfig.EFFECT_SOLID
                config.opacity = 100
            }
            refreshEditDialog()
        }
    }

    private fun selectEffectModeClean(config: NavigationBarConfig) {
        val modes = listOf(
            NavigationBarConfig.EFFECT_SOLID,
            NavigationBarConfig.EFFECT_GLASS,
            NavigationBarConfig.EFFECT_FROSTED
        )
        val labels = listOf(
            getString(R.string.effect_solid),
            getString(R.string.effect_glass),
            getString(R.string.effect_frosted)
        )
        selector(items = labels) { _, i ->
            config.effectMode = modes[i]
            refreshEditDialog()
        }
    }

    private fun selectLayoutMode(config: NavigationBarConfig) {
        val modes = listOf(
            NavigationBarConfig.LAYOUT_FLOATING,
            NavigationBarConfig.LAYOUT_STANDARD,
            NavigationBarConfig.LAYOUT_SIDEBAR
        )
        val labels = listOf("悬浮底栏", "常规底栏", "侧边栏")
        selector(items = labels) { _, i ->
            config.layoutMode = modes[i]
            if (config.layoutMode == NavigationBarConfig.LAYOUT_STANDARD) {
                config.effectMode = NavigationBarConfig.EFFECT_SOLID
                config.opacity = 100
            }
            refreshEditDialog()
        }
    }

    private fun selectEffectMode(config: NavigationBarConfig) {
        val modes = listOf(
            NavigationBarConfig.EFFECT_SOLID,
            NavigationBarConfig.EFFECT_GLASS,
            NavigationBarConfig.EFFECT_FROSTED
        )
        val labels = listOf("实色", "玻璃", "磨砂")
        selector(items = labels) { _, i ->
            config.effectMode = modes[i]
            refreshEditDialog()
        }
    }

    private fun editOpacity(config: NavigationBarConfig) {
        NumberPickerDialog(this)
            .setTitle(getString(R.string.opacity))
            .setMinValue(0)
            .setMaxValue(100)
            .setValue(config.opacity.coerceIn(0, 100))
            .show {
                config.opacity = it
                refreshEditDialog()
            }
    }

    private fun selectBorderColor(config: NavigationBarConfig) {
        val actions = listOf(
            getString(R.string.transparent),
            getString(R.string.accent_color),
            getString(R.string.custom)
        )
        selector(getString(R.string.bottom_bar_border_color), actions) { _, index ->
            when (index) {
                0 -> {
                    config.borderColor = null
                    refreshEditDialog()
                }
                1 -> {
                    config.borderColor = accentColor
                    config.borderAlpha = config.borderAlpha.coerceIn(1, 100)
                    refreshEditDialog()
                }
                2 -> showBorderColorPicker(config)
            }
        }
    }

    private fun showBorderColorPicker(config: NavigationBarConfig) {
        editingConfig = config
        val dialog = ColorPickerDialog.newBuilder()
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setColor(config.borderColor ?: accentColor)
            .setShowAlphaSlider(false)
            .setAllowPresets(true)
            .setAllowCustom(true)
            .setDialogId(COLOR_DIALOG_BORDER)
            .create()
        dialog.setColorPickerDialogListener(this)
        supportFragmentManager
            .beginTransaction()
            .add(dialog, "navigation_bar_border_color")
            .commitAllowingStateLoss()
    }

    private fun editBorderAlpha(config: NavigationBarConfig) {
        NumberPickerDialog(this)
            .setTitle(getString(R.string.bottom_bar_border_alpha))
            .setMinValue(0)
            .setMaxValue(100)
            .setValue(config.borderAlpha.coerceIn(0, 100))
            .show {
                config.borderAlpha = it
                refreshEditDialog()
            }
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        if (dialogId == COLOR_DIALOG_BORDER) {
            editingConfig?.let {
                it.borderColor = color
                it.borderAlpha = it.borderAlpha.coerceIn(1, 100)
                refreshEditDialog()
            }
        }
    }

    override fun onDialogDismissed(dialogId: Int) = Unit

    private fun NavigationBarConfig.borderColorText(): String {
        return borderColor?.let { String.format("#%06X", 0xFFFFFF and it) }
            ?: getString(R.string.transparent)
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
                val config = NavigationBarConfig.fromJson(clipText).apply {
                    isBuiltin = false
                    isNight = isNightMode
                    if (id.startsWith("builtin_")) {
                        id = "custom_${System.currentTimeMillis()}"
                    }
                    updatedAt = System.currentTimeMillis()
                }
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

    private data class IconRequest(
        val requestCode: Int,
        val item: NavigationBarConfig.NavItem,
        val state: String
    )

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun applyConfig(config: NavigationBarConfig) {
        activeConfigId = config.id
        saveConfigs()
        if (AppConfig.isNightTheme != config.isNight) {
            AppConfig.isNightTheme = config.isNight
            ThemeConfig.applyDayNight(this)
        } else {
            NavigationBarConfig.applyConfig(this, config)
        }
        adapter.setItems(getFilteredConfigs())
        toastOnUi(getString(R.string.applied_nav_bar_config, config.name))
    }

    private fun exportConfig(config: NavigationBarConfig) {
        share(config.toJson(), getString(R.string.share_nav_bar_config))
    }

    private fun deleteConfig(config: NavigationBarConfig) {
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                configs.remove(config)
                if (activeConfigId == config.id) {
                    activeConfigId = getFilteredConfigs().firstOrNull()?.id
                }
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
                tvBuiltin.visibility = if (item.isBuiltin) View.VISIBLE else View.GONE

                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(item.updatedAt))
                var infoText = item.getLayoutModeText()
                if (item.layoutMode != NavigationBarConfig.LAYOUT_STANDARD) {
                    infoText += " · ${item.getEffectModeText()}"
                }
                infoText += " · ${getString(R.string.opacity)} ${item.opacity}%"
                infoText += " · $dateStr"

                val isActive = item.id == activeConfigId
                if (isActive) {
                    infoText = "${getString(R.string.current_applied)} · $infoText"
                }
                tvInfo.text = infoText

                tvApply.text = if (isActive) getString(R.string.applied) else getString(R.string.apply)
                tvApply.setTextColor(
                    if (isActive) accentColor else ContextCompat.getColor(context, R.color.primaryText)
                )
                tvEdit.visibility = if (item.isBuiltin) View.GONE else View.VISIBLE
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemNavBarConfigBinding) {
            binding.apply {
                tvApply.setOnClickListener {
                    getFilteredConfigs().getOrNull(holder.layoutPosition)?.let(::applyConfig)
                }
                tvEdit.setOnClickListener {
                    getFilteredConfigs().getOrNull(holder.layoutPosition)?.let(::editConfig)
                }
                tvMore.setOnClickListener {
                    getFilteredConfigs().getOrNull(holder.layoutPosition)?.let(::showMoreOptions)
                }
            }
        }
    }
}
