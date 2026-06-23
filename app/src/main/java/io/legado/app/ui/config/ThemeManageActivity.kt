package io.legado.app.ui.config

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ActivityThemeManageBinding
import io.legado.app.databinding.ItemThemeConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MenuExtensions
import io.legado.app.utils.applyTint
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getClipText
import io.legado.app.utils.hexString
import io.legado.app.utils.readUri
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ThemeManageActivity : BaseActivity<ActivityThemeManageBinding>(),
    ColorPickerDialogListener {

    override val binding by viewBinding(ActivityThemeManageBinding::inflate)
    private val adapter by lazy { Adapter(this) }
    private val selectedPositions = mutableSetOf<Int>()
    private var isMultiSelectMode = false
    private var isNightThemeTab = false
    private var editingTheme: ThemeConfig.Config? = null
    private var editingThemeIndex = -1
    private var editingDialog: LinearLayout? = null

    private val selectBackgroundImage = registerForActivityResult(HandleFileContract()) { result ->
        val uri = result.uri ?: return@registerForActivityResult
        val config = editingTheme ?: return@registerForActivityResult
        saveBackgroundImage(uri, config)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        initTabs()
        initData()
        updateSummary()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(
            if (isMultiSelectMode) R.menu.theme_list_multi else R.menu.theme_list,
            menu
        )
        menu.applyTint(this)
        updateActionTextColor(menu.findItem(R.id.menu_import))
        return true
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_import -> importFromClipboard()
            R.id.menu_select_all -> selectAllOrClear()
            R.id.menu_to_top -> toTopSelected()
            R.id.menu_export -> exportSelected()
            R.id.menu_delete -> deleteSelected()
            else -> return super.onCompatOptionsItemSelected(item)
        }
        return true
    }

    private fun initView() = binding.run {
        recyclerView.layoutManager = LinearLayoutManager(this@ThemeManageActivity)
        recyclerView.addItemDecoration(VerticalDivider(this@ThemeManageActivity))
        recyclerView.adapter = adapter
        tvAddTheme.setOnClickListener {
            showAddOptions()
        }
    }

    private fun updateActionTextColor(item: MenuItem?) {
        if (item == null) return
        binding.titleBar.toolbar.post {
            val title = item.title?.toString() ?: return@post
            val color = MenuExtensions.getMenuColor(this, binding.titleBar.topBarTheme)
            findActionTextView(binding.titleBar.toolbar, title)?.setTextColor(color)
        }
    }

    private fun findActionTextView(view: View, title: String): TextView? {
        if (view is TextView && view.text?.toString() == title) {
            return view
        }
        return (view as? ViewGroup)
            ?.children
            ?.firstNotNullOfOrNull { findActionTextView(it, title) }
    }

    private fun initTabs() = binding.run {
        isNightThemeTab = AppConfig.isNightTheme
        updateTabSelection()
        tabDay.setOnClickListener {
            if (isNightThemeTab) {
                isNightThemeTab = false
                exitMultiSelectMode()
                updateTabSelection()
                initData()
                updateSummary()
            }
        }
        tabNight.setOnClickListener {
            if (!isNightThemeTab) {
                isNightThemeTab = true
                exitMultiSelectMode()
                updateTabSelection()
                initData()
                updateSummary()
            }
        }
    }

    private fun updateTabSelection() = binding.run {
        val activeColor = accentColor
        val primaryTextColor = ContextCompat.getColor(this@ThemeManageActivity, R.color.primaryText)
        val daySelected = !isNightThemeTab
        tvTabDay.setTextColor(if (daySelected) activeColor else primaryTextColor)
        tabDay.background = if (daySelected) {
            ContextCompat.getDrawable(this@ThemeManageActivity, R.drawable.bg_theme_tab_selected)
        } else {
            null
        }
        tvTabNight.setTextColor(if (!daySelected) activeColor else primaryTextColor)
        tabNight.background = if (!daySelected) {
            ContextCompat.getDrawable(this@ThemeManageActivity, R.drawable.bg_theme_tab_selected)
        } else {
            null
        }
    }

    private fun updateSummary() = binding.run {
        val filteredThemes = getFilteredThemes()
        tvSummary.text = if (filteredThemes.isEmpty()) {
            val themeType = if (isNightThemeTab) getString(R.string.night) else getString(R.string.day)
            getString(R.string.theme_summary_empty, themeType)
        } else {
            getString(R.string.theme_summary)
        }
    }

    private fun getFilteredThemes(): List<ThemeConfig.Config> {
        return ThemeConfig.configList.filter { it.isNightTheme == isNightThemeTab }
    }

    private fun initData() {
        adapter.setItems(getFilteredThemes())
    }

    private fun showAddOptions() {
        val items = listOf(
            getString(R.string.manual_config),
            getString(R.string.import_str)
        )
        selector(items = items) { _, index ->
            when (index) {
                0 -> editTheme(null)
                1 -> importFromClipboard()
            }
        }
    }

    private fun importFromClipboard() {
        getClipText()?.let { clipText ->
            val count = ThemeConfig.addConfig(clipText)
            if (count > 0) {
                initData()
                updateSummary()
                toastOnUi(R.string.import_success)
            } else {
                toastOnUi(R.string.import_failed)
            }
        } ?: toastOnUi(R.string.clipboard_empty)
    }

    private fun enterMultiSelectMode(position: Int) {
        isMultiSelectMode = true
        selectedPositions.clear()
        selectedPositions.add(position)
        binding.titleBar.title = getString(R.string.selected, selectedPositions.size)
        invalidateOptionsMenu()
        adapter.notifyDataSetChanged()
    }

    private fun exitMultiSelectMode() {
        if (!isMultiSelectMode) return
        isMultiSelectMode = false
        selectedPositions.clear()
        binding.titleBar.setTitle(R.string.theme_list)
        invalidateOptionsMenu()
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
        binding.titleBar.title = getString(R.string.selected, selectedPositions.size)
    }

    private fun selectAllOrClear() {
        if (selectedPositions.size == adapter.itemCount) {
            selectedPositions.clear()
        } else {
            selectedPositions.clear()
            for (i in 0 until adapter.itemCount) {
                selectedPositions.add(i)
            }
        }
        if (selectedPositions.isEmpty()) {
            exitMultiSelectMode()
        } else {
            binding.titleBar.title = getString(R.string.selected, selectedPositions.size)
            adapter.notifyDataSetChanged()
        }
    }

    private fun exportSelected() {
        val filteredThemes = getFilteredThemes()
        val configs = selectedPositions.sorted().mapNotNull { filteredThemes.getOrNull(it) }
        share(GSON.toJson(configs), getString(R.string.theme_list))
        exitMultiSelectMode()
    }

    private fun deleteSelected() {
        if (selectedPositions.isEmpty()) {
            toastOnUi(R.string.select_theme)
            return
        }
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                val filteredThemes = getFilteredThemes()
                selectedPositions.sortedDescending().forEach { filteredIndex ->
                    filteredThemes.getOrNull(filteredIndex)?.let { config ->
                        findThemeIndex(config).takeIf { it >= 0 }?.let(ThemeConfig::delConfig)
                    }
                }
                exitMultiSelectMode()
                initData()
                updateSummary()
            }
            noButton()
        }
    }

    private fun toTopSelected() {
        if (selectedPositions.isEmpty()) {
            toastOnUi(R.string.select_theme)
            return
        }
        val filteredThemes = getFilteredThemes()
        val originalPositions = selectedPositions.sorted().mapNotNull { filteredIndex ->
            filteredThemes.getOrNull(filteredIndex)?.let(::findThemeIndex)?.takeIf { it >= 0 }
        }
        ThemeConfig.toTopConfigs(originalPositions)
        exitMultiSelectMode()
        initData()
    }

    private fun editTheme(position: Int?) {
        val source = position?.let { getFilteredThemes().getOrNull(it) }
        val config = source?.copy() ?: newThemeConfig()
        editingTheme = config
        editingThemeIndex = source?.let(::findThemeIndex) ?: -1
        val root = buildThemeEditView(config)
        editingDialog = root
        alert(if (source == null) R.string.add_theme else R.string.edit_theme) {
            customView {
                ScrollView(this@ThemeManageActivity).apply {
                    addView(root)
                }
            }
            okButton {
                val name = root.findViewWithTag<EditText>("themeName")
                    ?.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                if (name.isBlank()) {
                    toastOnUi(R.string.input_is_empty)
                    return@okButton
                }
                config.themeName = name
                if (!isValidThemeConfig(config)) {
                    toastOnUi(R.string.wrong_format)
                    return@okButton
                }
                saveEditedTheme(config)
            }
            cancelButton()
            onDismiss {
                editingTheme = null
                editingThemeIndex = -1
                editingDialog = null
            }
        }
    }

    private fun newThemeConfig(): ThemeConfig.Config {
        return ThemeConfig.getDurConfig(this).copy(
            themeName = getNextThemeName(),
            isNightTheme = isNightThemeTab
        )
    }

    private fun buildThemeEditView(config: ThemeConfig.Config): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            addView(EditText(context).apply {
                tag = "themeName"
                hint = getString(R.string.theme_name)
                setText(config.themeName)
                setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    48.dp
                )
            })
            addView(colorRow(getString(R.string.primary), config.primaryColor, COLOR_PRIMARY))
            addView(colorRow(getString(R.string.accent_color), config.accentColor, COLOR_ACCENT))
            addView(colorRow(getString(R.string.background_color), config.backgroundColor, COLOR_BACKGROUND))
            addView(colorRow(getString(R.string.bottom_background_color), config.bottomBackground, COLOR_BOTTOM_BACKGROUND))
            addView(switchRow(getString(R.string.imm_navigation_bar_s), config.transparentNavBar) {
                config.transparentNavBar = !config.transparentNavBar
                refreshThemeEditDialog()
            })
            addView(optionRow(getString(R.string.background_image), displayPath(config.backgroundImgPath)) {
                editBackgroundPath(config)
            })
            addView(optionRow(getString(R.string.background_image_blurring), "${config.backgroundImgBlur}") {
                editBackgroundBlur(config)
            })
        }
    }

    private fun colorRow(title: String, value: String, dialogId: Int): View {
        return colorOptionRow(title, value.uppercase(Locale.ROOT)) {
            val color = runCatching { value.toColorInt() }
                .getOrDefault(ContextCompat.getColor(this, R.color.default_primary))
            val dialog = ColorPickerDialog.newBuilder()
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setColor(color)
                .setShowAlphaSlider(false)
                .setAllowPresets(true)
                .setAllowCustom(true)
                .setDialogId(dialogId)
                .create()
            dialog.setColorPickerDialogListener(this)
            supportFragmentManager
                .beginTransaction()
                .add(dialog, "theme_color_$dialogId")
                .commitAllowingStateLoss()
        }
    }

    private fun colorOptionRow(title: String, value: String, onClick: () -> Unit): View {
        return optionRow(title, value, onClick).also { row ->
            val valueView = (row as? LinearLayout)?.getChildAt(1) as? TextView ?: return@also
            val color = runCatching { value.toColorInt() }.getOrNull() ?: return@also
            val swatch = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 5.dp.toFloat()
                    setColor(color)
                    setStroke(1.dp, ContextCompat.getColor(context, R.color.secondaryText))
                }
                layoutParams = LinearLayout.LayoutParams(28.dp, 22.dp).apply {
                    marginStart = 10.dp
                }
            }
            row.addView(swatch)
            valueView.maxWidth = 118.dp
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
                text = value.ifBlank { getString(R.string.not_available) }
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.secondaryText))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
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
            setOnClickListener { onClick() }
        }
    }

    private fun editBackgroundPath(config: ThemeConfig.Config) {
        val actions = mutableListOf(
            getString(R.string.select_image),
            getString(R.string.input_path_or_url)
        )
        if (!config.backgroundImgPath.isNullOrBlank()) {
            actions.add(getString(R.string.clear))
        }
        selector(getString(R.string.background_image), actions) { _, index ->
            when (index) {
                0 -> selectBackgroundImage.launch {
                    mode = HandleFileContract.IMAGE
                    title = getString(R.string.background_image)
                }
                1 -> alertInputBackgroundPath(config)
                2 -> {
                    config.backgroundImgPath = null
                    refreshThemeEditDialog()
                }
            }
        }
    }

    private fun alertInputBackgroundPath(config: ThemeConfig.Config) {
        alert(R.string.background_image) {
            val editText = EditText(this@ThemeManageActivity).apply {
                hint = getString(R.string.input_path_or_url)
                setText(config.backgroundImgPath.orEmpty())
                setSingleLine(false)
                minLines = 2
            }
            customView { editText }
            okButton {
                config.backgroundImgPath = editText.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
                refreshThemeEditDialog()
            }
            cancelButton()
        }
    }

    private fun saveBackgroundImage(uri: Uri, config: ThemeConfig.Config) {
        kotlin.runCatching {
            readUri(uri) { fileDoc, inputStream ->
                val key = if (config.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage
                val suffix = when {
                    fileDoc.name.contains(".9.png", true) -> ".9.png"
                    fileDoc.name.contains(".") -> "." + fileDoc.name.substringAfterLast(".")
                    else -> ".jpg"
                }
                val fileName = "theme_${System.currentTimeMillis()}$suffix"
                val file = FileUtils.createFileIfNotExist(externalFiles, key, fileName)
                FileOutputStream(file).use { output ->
                    inputStream.copyTo(output)
                }
                config.backgroundImgPath = file.absolutePath
            }
        }.onSuccess {
            refreshThemeEditDialog()
        }.onFailure {
            toastOnUi(it.localizedMessage)
        }
    }

    private fun displayPath(path: String?): String {
        if (path.isNullOrBlank()) return getString(R.string.select_image)
        return path.substringAfterLast('/').substringAfterLast('\\').ifBlank { path }
    }

    private fun editBackgroundBlur(config: ThemeConfig.Config) {
        NumberPickerDialog(this)
            .setTitle(getString(R.string.background_image_blurring))
            .setMinValue(0)
            .setMaxValue(25)
            .setValue(config.backgroundImgBlur.coerceIn(0, 25))
            .show {
                config.backgroundImgBlur = it
                refreshThemeEditDialog()
            }
    }

    private fun refreshThemeEditDialog() {
        val config = editingTheme ?: return
        val root = editingDialog ?: return
        root.findViewWithTag<EditText>("themeName")
            ?.text
            ?.toString()
            ?.trim()
            ?.let { config.themeName = it }
        root.removeAllViews()
        buildThemeEditView(config).let { rebuilt ->
            while (rebuilt.childCount > 0) {
                val child = rebuilt.getChildAt(0)
                rebuilt.removeViewAt(0)
                root.addView(child)
            }
        }
    }

    private fun saveEditedTheme(config: ThemeConfig.Config) {
        val exactIndex = findThemeIndex(config)
        val targetIndex = when {
            editingThemeIndex >= 0 -> editingThemeIndex
            exactIndex >= 0 -> exactIndex
            else -> -1
        }
        if (targetIndex >= 0) {
            ThemeConfig.configList[targetIndex] = config
        } else {
            ThemeConfig.configList.add(config)
        }
        ThemeConfig.save()
        initData()
        updateSummary()
        val current = ThemeConfig.getDurConfig(this)
        if (current.themeName == config.themeName && current.isNightTheme == config.isNightTheme) {
            ThemeConfig.applyConfig(this, config)
            recreateAfterThemeApplied()
        }
        toastOnUi(R.string.success)
    }

    private fun delete(position: Int) {
        val config = getFilteredThemes().getOrNull(position) ?: return
        val originalIndex = findThemeIndex(config)
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

    private fun share(position: Int) {
        val config = getFilteredThemes().getOrNull(position) ?: return
        share(GSON.toJson(config), getString(R.string.theme_list))
    }

    private fun applyTheme(config: ThemeConfig.Config) {
        ThemeConfig.applyConfig(this, config)
        isNightThemeTab = config.isNightTheme
        updateTabSelection()
        adapter.notifyDataSetChanged()
        toastOnUi(getString(R.string.applied_theme_config, config.themeName))
        recreateAfterThemeApplied()
    }

    private fun recreateAfterThemeApplied() {
        binding.root.post {
            if (!isFinishing && !isDestroyed) {
                recreate()
            }
        }
    }

    private fun findThemeIndex(config: ThemeConfig.Config): Int {
        return ThemeConfig.configList.indexOfFirst {
            it.themeName == config.themeName && it.isNightTheme == config.isNightTheme
        }
    }

    private fun getNextThemeName(): String {
        val base = getString(R.string.add_theme)
        val usedNames = ThemeConfig.configList
            .filter { it.isNightTheme == isNightThemeTab }
            .map { it.themeName }
            .toSet()
        if (!usedNames.contains(base)) return base
        for (index in 2..999) {
            val name = "$base $index"
            if (!usedNames.contains(name)) return name
        }
        return "$base ${System.currentTimeMillis()}"
    }

    private fun isValidThemeConfig(config: ThemeConfig.Config): Boolean {
        return runCatching {
            config.primaryColor.toColorInt()
            config.accentColor.toColorInt()
            config.backgroundColor.toColorInt()
            config.bottomBackground.toColorInt()
        }.isSuccess
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        val config = editingTheme ?: return
        val hex = "#${color.hexString}".uppercase(Locale.ROOT)
        when (dialogId) {
            COLOR_PRIMARY -> config.primaryColor = hex
            COLOR_ACCENT -> config.accentColor = hex
            COLOR_BACKGROUND -> config.backgroundColor = hex
            COLOR_BOTTOM_BACKGROUND -> config.bottomBackground = hex
        }
        refreshThemeEditDialog()
    }

    override fun onDialogDismissed(dialogId: Int) = Unit

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
                tvBuiltin.visibility = View.GONE
                val bgColor = parseThemeColor(
                    item.backgroundColor,
                    if (item.isNightTheme) R.color.default_night_background else R.color.default_background
                )
                val primaryColor = parseThemeColor(
                    item.primaryColor,
                    if (item.isNightTheme) R.color.default_night_primary else R.color.default_primary
                )
                previewContainer.elevation = 8.dp.toFloat()
                previewContainer.translationZ = 2.dp.toFloat()
                previewContainer.background = previewBackgroundDrawable(item, bgColor)
                previewPrimary.background = GradientDrawable().apply {
                    cornerRadius = 4f
                    setColor(primaryColor)
                }
                previewBar1.background = GradientDrawable().apply {
                    cornerRadius = 2f
                    setColor(primaryColor)
                    alpha = 77
                }
                previewBar2.background = GradientDrawable().apply {
                    cornerRadius = 2f
                    setColor(primaryColor)
                    alpha = 51
                }
                val currentConfig = ThemeConfig.getDurConfig(context)
                val isCurrentTheme = item.themeName == currentConfig.themeName &&
                    item.isNightTheme == currentConfig.isNightTheme
                ivCurrent.visibility = if (isCurrentTheme && !isMultiSelectMode) View.VISIBLE else View.GONE
                val themeType = if (item.isNightTheme) getString(R.string.night) else getString(R.string.day)
                tvInfo.text = if (isCurrentTheme) {
                    "${getString(R.string.current_applied)} | $themeType"
                } else {
                    themeType
                }
                tvApply.text = if (isCurrentTheme) getString(R.string.applied) else getString(R.string.apply_theme)
                tvApply.setTextColor(
                    if (isCurrentTheme) accentColor else ContextCompat.getColor(context, R.color.primaryText)
                )
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
                    if (isMultiSelectMode) {
                        toggleSelection(holder.layoutPosition)
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
                        getFilteredThemes().getOrNull(holder.layoutPosition)?.let(::applyTheme)
                    }
                }
                tvEdit.setOnClickListener {
                    if (!isMultiSelectMode) {
                        editTheme(holder.layoutPosition)
                    }
                }
                tvMore.setOnClickListener {
                    if (!isMultiSelectMode) {
                        showMoreOptions(holder.layoutPosition)
                    }
                }
            }
        }

        private fun showMoreOptions(position: Int) {
            val items = listOf(
                getString(R.string.apply_theme),
                getString(R.string.edit),
                getString(R.string.export_str),
                getString(R.string.delete)
            )
            selector(items = items) { _, index ->
                when (index) {
                    0 -> getFilteredThemes().getOrNull(position)?.let(::applyTheme)
                    1 -> editTheme(position)
                    2 -> share(position)
                    3 -> delete(position)
                }
            }
        }
    }

    private fun parseThemeColor(value: String, fallback: Int): Int {
        return runCatching { Color.parseColor(value) }
            .getOrDefault(ContextCompat.getColor(this, fallback))
    }

    private fun previewBackgroundDrawable(item: ThemeConfig.Config, fallbackColor: Int): Drawable {
        val path = item.backgroundImgPath
        val image = when {
            path.isNullOrBlank() -> null
            path.startsWith("http", ignoreCase = true) -> null
            File(path).exists() -> Drawable.createFromPath(path)
            else -> null
        }
        return image ?: GradientDrawable().apply {
            cornerRadius = 10f
            setColor(fallbackColor)
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val COLOR_PRIMARY = 401
        private const val COLOR_ACCENT = 402
        private const val COLOR_BACKGROUND = 403
        private const val COLOR_BOTTOM_BACKGROUND = 404
    }
}
