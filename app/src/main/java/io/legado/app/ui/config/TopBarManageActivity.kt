package io.legado.app.ui.config

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityTopBarManageBinding
import io.legado.app.databinding.ItemTopBarConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.image.ImageCropContract
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.ui.widget.applyTopBarConfig
import io.legado.app.utils.ImageCropHelper
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getClipText
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TopBarManageActivity : BaseActivity<ActivityTopBarManageBinding>(), ColorPickerDialogListener {

    override val binding by viewBinding(ActivityTopBarManageBinding::inflate)

    private val adapter by lazy { Adapter(this) }
    private var entries: List<TopBarConfig.Entry> = emptyList()
    private var isNightMode = false
    private var editingEntry: TopBarConfig.Entry? = null
    private var pendingConfig: TopBarConfig.Config? = null
    private var editingDialog: LinearLayout? = null
    private var pendingWallpaperCropRequest: ImageCropHelper.Request? = null

    private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    private val importPackage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let(::importPackage)
    }

    private val exportPackage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { toastOnUi(R.string.success) }
    }

    private val selectWallpaper = registerForActivityResult(HandleFileContract()) {
        it.uri?.let(::startWallpaperCrop)
    }

    private val cropWallpaper = registerForActivityResult(ImageCropContract()) { result ->
        pendingWallpaperCropRequest = null
        if (result.isNullOrBlank()) return@registerForActivityResult
        if (File(result).exists()) {
            pendingConfig?.wallpaperPath = result
            refreshEditDialog()
        } else {
            toastOnUi(getString(R.string.image_crop_failed, getString(R.string.error_decode_bitmap)))
        }
    }

    companion object {
        private const val PREF_KEY_IS_NIGHT = "topBarIsNight"
        private const val COLOR_BACKGROUND = 5101
        private const val COLOR_TAG_BAR = 5102
        private const val COLOR_TAG_SELECTED = 5103
        private const val REQUEST_WALLPAPER = 5104
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        initTabs()
        loadPackages()
    }

    private fun initView() = binding.run {
        titleBar.title = getString(R.string.top_bar_manage)
        recyclerView.layoutManager = LinearLayoutManager(this@TopBarManageActivity)
        recyclerView.addItemDecoration(VerticalDivider(this@TopBarManageActivity))
        recyclerView.adapter = adapter
        tvAddConfig.setOnClickListener { showAddOptions() }
    }

    private fun initTabs() = binding.run {
        isNightMode = getPrefBoolean(PREF_KEY_IS_NIGHT, AppConfig.isNightTheme)
        updateTabSelection()
        tabDay.setOnClickListener {
            if (isNightMode) {
                isNightMode = false
                putPrefBoolean(PREF_KEY_IS_NIGHT, isNightMode)
                updateTabSelection()
                loadPackages()
            }
        }
        tabNight.setOnClickListener {
            if (!isNightMode) {
                isNightMode = true
                putPrefBoolean(PREF_KEY_IS_NIGHT, isNightMode)
                updateTabSelection()
                loadPackages()
            }
        }
    }

    private fun updateTabSelection() {
        val activeColor = accentColor
        val primaryTextColor = ContextCompat.getColor(this, R.color.primaryText)
        binding.apply {
            tvTabDay.setTextColor(if (!isNightMode) activeColor else primaryTextColor)
            tabDay.background = if (!isNightMode) {
                ContextCompat.getDrawable(this@TopBarManageActivity, R.drawable.bg_theme_tab_selected)
            } else {
                null
            }
            tvTabNight.setTextColor(if (isNightMode) activeColor else primaryTextColor)
            tabNight.background = if (isNightMode) {
                ContextCompat.getDrawable(this@TopBarManageActivity, R.drawable.bg_theme_tab_selected)
            } else {
                null
            }
        }
    }

    private fun loadPackages() {
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    TopBarConfig.loadEntries(this@TopBarManageActivity, isNightMode)
                }
            }.onSuccess {
                entries = it
                adapter.setItems(entries)
                updateSummary()
            }.onFailure {
                binding.tvSummary.text = it.localizedMessage
            }
        }
    }

    private fun updateSummary() {
        val themeType = if (isNightMode) getString(R.string.night) else getString(R.string.day)
        binding.tvSummary.text = getString(R.string.top_bar_summary, themeType)
    }

    private fun showAddOptions() {
        selector(
            getString(R.string.add_top_bar_config),
            listOf(
                getString(R.string.manual_config),
                getString(R.string.top_bar_import_zip),
                getString(R.string.top_bar_import_clipboard)
            )
        ) { _, index ->
            when (index) {
                0 -> showEditDialog(null)
                1 -> importPackage.launch {
                    mode = HandleFileContract.FILE
                    title = getString(R.string.top_bar_import_zip)
                    allowExtensions = arrayOf("zip")
                }
                2 -> importFromClipboard()
            }
        }
    }

    private fun showEditDialog(entry: TopBarConfig.Entry?) {
        if (entry?.dirName == TopBarConfig.DEFAULT_DIR_NAME) {
            toastOnUi(R.string.navigation_bar_default_readonly)
            return
        }
        val base = entry ?: TopBarConfig.Entry(
            config = TopBarConfig.defaultConfig(this, isNightMode).copy(name = nextPackageName()),
            source = TopBarConfig.Source.LOCAL,
            dirName = ""
        )
        editingEntry = base
        pendingConfig = base.config.copy()
        val root = buildEditView()
        editingDialog = root
        alert(if (entry == null) R.string.add else R.string.edit) {
            customView { root }
            okButton { saveEditingPackage() }
            cancelButton()
        }
    }

    private fun buildEditView(): LinearLayout {
        val config = pendingConfig!!
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            addView(EditText(context).apply {
                tag = "name"
                hint = getString(R.string.top_bar_name)
                setText(config.name)
                setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    48.dp
                )
            })
            addView(optionRow(getString(R.string.top_bar_style), styleLabel(config.style)) {
                selector(
                    getString(R.string.top_bar_style),
                    listOf(getString(R.string.default_top_bar), getString(R.string.regular_top_bar))
                ) { _, index ->
                    config.style = if (index == 1) TopBarConfig.STYLE_REGULAR else TopBarConfig.STYLE_DEFAULT
                    if (config.style == TopBarConfig.STYLE_REGULAR) {
                        config.backgroundColor = config.backgroundColor ?: TopBarConfig.defaultBackgroundColor(config.isNightMode)
                        config.cornerScale = config.cornerScale ?: 1f
                        config.tagBarColor = config.tagBarColor ?: Color.WHITE
                        if (config.tagBarAlpha == 100) config.tagBarAlpha = 0
                    }
                    refreshEditDialog()
                }
            })
            if (config.style == TopBarConfig.STYLE_REGULAR) {
                addView(optionRow(getString(R.string.corner_scale), cornerScaleLabel(config.cornerScale)) {
                    showCornerScalePicker(config.cornerScale ?: 1f) {
                        config.cornerScale = it
                    }
                })
                val backgroundColor = config.backgroundColor ?: TopBarConfig.defaultBackgroundColor(config.isNightMode)
                addView(optionRow(getString(R.string.top_bar_background_color), colorLabel(config.backgroundColor), backgroundColor) {
                    showColorOptions(COLOR_BACKGROUND, backgroundColor)
                })
                addView(optionRow(getString(R.string.wallpaper), wallpaperLabel(config.wallpaperPath)) {
                    showWallpaperSelector()
                })
                addView(optionRow(getString(R.string.top_bar_wallpaper_alpha), "${config.wallpaperAlpha}%") {
                    showPercentPicker(getString(R.string.top_bar_wallpaper_alpha), config.wallpaperAlpha) {
                        config.wallpaperAlpha = it
                    }
                })
                addView(optionRow(getString(R.string.top_bar_filter_default), filterDefaultLabel(config.expandFiltersByDefault)) {
                    selector(
                        getString(R.string.top_bar_filter_default),
                        listOf(
                            getString(R.string.top_bar_filter_default_collapsed),
                            getString(R.string.top_bar_filter_default_expanded)
                        )
                    ) { _, index ->
                        config.expandFiltersByDefault = index == 1
                        refreshEditDialog()
                    }
                })
            }
            val tagBarColor = config.tagBarColor ?: defaultTagBarColor()
            addView(optionRow(getString(R.string.top_bar_tag_bar_color), colorLabel(config.tagBarColor), tagBarColor) {
                showColorOptions(COLOR_TAG_BAR, tagBarColor)
            })
            addView(optionRow(getString(R.string.tag_bar_opacity), "${config.tagBarAlpha}%") {
                showPercentPicker(getString(R.string.tag_bar_opacity), config.tagBarAlpha) {
                    config.tagBarAlpha = it
                }
            })
            val selectedColor = config.tagSelectedColor ?: defaultSelectedColor()
            addView(optionRow(getString(R.string.top_bar_tag_selected_color), colorLabel(config.tagSelectedColor), selectedColor) {
                showColorOptions(COLOR_TAG_SELECTED, selectedColor)
            })
            addView(optionRow(getString(R.string.tag_selected_opacity), "${config.tagSelectedAlpha}%") {
                showPercentPicker(getString(R.string.tag_selected_opacity), config.tagSelectedAlpha) {
                    config.tagSelectedAlpha = it
                }
            })
        }
    }

    private fun optionRow(title: String, value: String, onClick: () -> Unit): View {
        return optionRow(title, value, null, onClick)
    }

    private fun optionRow(title: String, value: String, colorPreview: Int?, onClick: () -> Unit): View {
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
            colorPreview?.let { color ->
                addView(View(context).apply {
                    setBackgroundColor(color)
                    layoutParams = LinearLayout.LayoutParams(20.dp, 20.dp).apply { marginEnd = 8.dp }
                })
            }
            addView(TextView(context).apply {
                text = value
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.secondaryText))
            })
            setOnClickListener { onClick() }
        }
    }

    private fun refreshEditDialog() {
        val root = editingDialog ?: return
        root.removeAllViews()
        val rebuilt = buildEditView()
        while (rebuilt.childCount > 0) {
            val child = rebuilt.getChildAt(0)
            rebuilt.removeView(child)
            root.addView(child)
        }
    }

    private fun showWallpaperSelector() {
        val hasWallpaper = !pendingConfig?.wallpaperPath.isNullOrBlank()
        val actions = buildList {
            add(getString(R.string.select_image))
            if (hasWallpaper) add(getString(R.string.delete))
        }
        selector(getString(R.string.wallpaper), actions) { _, index ->
            if (index == 0) {
                selectWallpaper.launch {
                    mode = HandleFileContract.IMAGE
                    title = getString(R.string.wallpaper)
                }
            } else {
                pendingConfig?.wallpaperPath = null
                refreshEditDialog()
            }
        }
    }

    private fun startWallpaperCrop(uri: Uri) {
        val metrics = resources.displayMetrics
        val request = ImageCropHelper.buildRequest(
            context = this,
            sourceUri = uri,
            requestCode = REQUEST_WALLPAPER,
            aspectWidth = metrics.widthPixels.coerceAtLeast(1),
            aspectHeight = (220 * metrics.density).toInt().coerceAtLeast(1),
            dirName = "topBarWallpapers",
            prefix = "top_bar",
            targetWidth = 1600
        )
        pendingWallpaperCropRequest = request
        cropWallpaper.launch(request.params)
    }

    private fun showPercentPicker(title: String, value: Int, apply: (Int) -> Unit) {
        NumberPickerDialog(this)
            .setTitle(title)
            .setMinValue(0)
            .setMaxValue(100)
            .setValue(value.coerceIn(0, 100))
            .show {
                apply(it.coerceIn(0, 100))
                refreshEditDialog()
            }
    }

    private fun showCornerScalePicker(value: Float, apply: (Float) -> Unit) {
        NumberPickerDialog(this, isDecimalMode = true)
            .setTitle(getString(R.string.corner_scale))
            .setMinValue(0)
            .setMaxValue(30)
            .setValue((value.coerceIn(0f, 3f) * 10).toInt())
            .show {
                apply((it / 10f).coerceIn(0f, 3f))
                refreshEditDialog()
            }
    }

    private fun showColorOptions(target: Int, color: Int) {
        selector(
            items = listOf(
                getString(R.string.top_bar_follow_theme),
                getString(R.string.top_bar_primary_color),
                getString(R.string.accent_color),
                getString(R.string.custom)
            )
        ) { _, index ->
            val config = pendingConfig ?: return@selector
            when (index) {
                0 -> {
                    when (target) {
                        COLOR_BACKGROUND -> config.backgroundColor = null
                        COLOR_TAG_BAR -> config.tagBarColor = null
                        COLOR_TAG_SELECTED -> config.tagSelectedColor = null
                    }
                    refreshEditDialog()
                }
                1 -> {
                    when (target) {
                        COLOR_BACKGROUND -> config.backgroundColor = primaryColor
                        COLOR_TAG_BAR -> config.tagBarColor = primaryColor
                        COLOR_TAG_SELECTED -> config.tagSelectedColor = primaryColor
                    }
                    refreshEditDialog()
                }
                2 -> {
                    when (target) {
                        COLOR_BACKGROUND -> config.backgroundColor = accentColor
                        COLOR_TAG_BAR -> config.tagBarColor = accentColor
                        COLOR_TAG_SELECTED -> config.tagSelectedColor = accentColor
                    }
                    refreshEditDialog()
                }
                3 -> showColorPicker(target, color)
            }
        }
    }

    private fun showColorPicker(target: Int, color: Int) {
        ColorPickerDialog.newBuilder()
            .setDialogId(target)
            .setColor(color)
            .setShowAlphaSlider(false)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .show(this)
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        val config = pendingConfig ?: return
        when (dialogId) {
            COLOR_BACKGROUND -> config.backgroundColor = color
            COLOR_TAG_BAR -> config.tagBarColor = color
            COLOR_TAG_SELECTED -> config.tagSelectedColor = color
        }
        refreshEditDialog()
    }

    override fun onDialogDismissed(dialogId: Int) = Unit

    private fun saveEditingPackage() {
        val config = pendingConfig ?: return
        val name = editingDialog?.findViewWithTag<EditText>("name")?.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            toastOnUi(R.string.input_is_empty)
            return
        }
        val oldEntry = editingEntry
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    TopBarConfig.addOrUpdate(config.copy(name = name), oldEntry)
                }
            }.onSuccess {
                if (oldEntry?.dirName == TopBarConfig.DEFAULT_DIR_NAME ||
                    it.dirName == TopBarConfig.activeDirName(it.config.isNightMode)
                ) {
                    TopBarConfig.apply(it)
                    postEvent(EventBus.TOP_BAR_CHANGED, it.config.isNightMode)
                    binding.titleBar.applyTopBarConfig()
                }
                toastOnUi(R.string.success)
                loadPackages()
            }.onFailure {
                toastOnUi(it.localizedMessage)
            }
        }
    }

    private fun applyPackage(entry: TopBarConfig.Entry) {
        TopBarConfig.apply(entry)
        postEvent(EventBus.TOP_BAR_CHANGED, entry.config.isNightMode)
        binding.titleBar.applyTopBarConfig()
        loadPackages()
        toastOnUi(getString(R.string.applied_top_bar_config, entry.config.name))
    }

    private fun exportPackage(entry: TopBarConfig.Entry) {
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) { TopBarConfig.exportZip(entry) }
            }.onSuccess { zip ->
                exportPackage.launch {
                    mode = HandleFileContract.EXPORT
                    title = getString(R.string.export_str)
                    fileData = HandleFileContract.FileData(zip.name, zip, "application/zip")
                }
            }.onFailure {
                toastOnUi(it.localizedMessage)
            }
        }
    }

    private fun importPackage(uri: Uri) {
        lifecycleScope.launch {
            kotlin.runCatching {
                val file = externalFiles.getFile("topBarImports", "import_${System.currentTimeMillis()}.zip")
                file.parentFile?.mkdirs()
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                } ?: throw IllegalArgumentException(getString(R.string.file_not_exist))
                withContext(Dispatchers.IO) { TopBarConfig.importZip(file) }
            }.onSuccess {
                toastOnUi(R.string.import_success)
                loadPackages()
            }.onFailure {
                toastOnUi(it.localizedMessage)
            }
        }
    }

    private fun importFromClipboard() {
        val clipText = getClipText()
        if (clipText.isNullOrBlank()) {
            toastOnUi(R.string.clipboard_empty)
            return
        }
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) { TopBarConfig.importJson(clipText, isNightMode) }
            }.onSuccess {
                toastOnUi(R.string.import_success)
                loadPackages()
            }.onFailure {
                toastOnUi(R.string.import_failed)
            }
        }
    }

    private fun deletePackage(entry: TopBarConfig.Entry) {
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { TopBarConfig.deleteLocal(entry) }
                    postEvent(EventBus.TOP_BAR_CHANGED, entry.config.isNightMode)
                    loadPackages()
                }
            }
            noButton()
        }
    }

    private fun showActions(entry: TopBarConfig.Entry) {
        val actions = buildList {
            add(Action.APPLY)
            if (entry.dirName != TopBarConfig.DEFAULT_DIR_NAME) {
                add(Action.EDIT)
                add(Action.EXPORT)
                add(Action.SHARE_JSON)
                if (entry.dirName != TopBarConfig.activeDirName(entry.config.isNightMode)) {
                    add(Action.DELETE)
                }
            } else {
                add(Action.IMPORT_CLIPBOARD)
            }
        }
        selector(entry.config.name, actions.map { getString(it.titleRes) }) { _, index ->
            when (actions[index]) {
                Action.APPLY -> applyPackage(entry)
                Action.EDIT -> showEditDialog(entry)
                Action.EXPORT -> exportPackage(entry)
                Action.SHARE_JSON -> share(entry.config.toJson(), getString(R.string.share_top_bar_config))
                Action.DELETE -> deletePackage(entry)
                Action.IMPORT_CLIPBOARD -> importFromClipboard()
            }
        }
    }

    private fun colorLabel(color: Int?): String {
        return when {
            color == null -> getString(R.string.top_bar_follow_theme)
            color == accentColor -> getString(R.string.accent_color)
            color == primaryColor -> getString(R.string.top_bar_primary_color)
            else -> "#${Integer.toHexString(color).takeLast(6).uppercase(Locale.ROOT)}"
        }
    }

    private fun cornerScaleLabel(value: Float?): String {
        return String.format(Locale.ROOT, "%.1f", (value ?: 1f).coerceIn(0f, 3f))
    }

    private fun filterDefaultLabel(expanded: Boolean): String {
        return getString(
            if (expanded) R.string.top_bar_filter_default_expanded
            else R.string.top_bar_filter_default_collapsed
        )
    }

    private fun wallpaperLabel(path: String?): String {
        return if (path.isNullOrBlank()) {
            getString(R.string.top_bar_wallpaper_unselected)
        } else {
            getString(R.string.top_bar_wallpaper_selected)
        }
    }

    private fun styleLabel(style: String): String {
        return getString(
            when (style) {
                TopBarConfig.STYLE_REGULAR -> R.string.regular_top_bar
                else -> R.string.default_top_bar
            }
        )
    }

    private fun defaultTagBarColor(): Int = ContextCompat.getColor(this, R.color.background_menu)

    private fun defaultSelectedColor(): Int = primaryColor

    private fun nextPackageName(): String {
        val base = getString(R.string.custom_top_bar)
        val usedNames = entries.map { it.config.name }.toSet()
        if (base !in usedNames) return base
        for (index in 2..999) {
            val name = "$base $index"
            if (name !in usedNames) return name
        }
        return "$base ${System.currentTimeMillis()}"
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private enum class Action(val titleRes: Int) {
        APPLY(R.string.apply),
        EDIT(R.string.edit),
        EXPORT(R.string.export_str),
        SHARE_JSON(R.string.share_top_bar_config),
        DELETE(R.string.delete),
        IMPORT_CLIPBOARD(R.string.top_bar_import_clipboard)
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<TopBarConfig.Entry, ItemTopBarConfigBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemTopBarConfigBinding {
            return ItemTopBarConfigBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTopBarConfigBinding,
            item: TopBarConfig.Entry,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                tvName.text = item.config.name
                tvBuiltin.visibility = if (item.dirName == TopBarConfig.DEFAULT_DIR_NAME) View.VISIBLE else View.GONE
                val isActive = item.dirName == TopBarConfig.activeDirName(item.config.isNightMode)
                tvInfo.text = buildInfoText(item, isActive)
                tvApply.text = if (isActive) getString(R.string.applied) else getString(R.string.apply)
                tvApply.setTextColor(
                    if (isActive) accentColor else ContextCompat.getColor(context, R.color.primaryText)
                )
                tvEdit.visibility = if (item.dirName == TopBarConfig.DEFAULT_DIR_NAME) View.GONE else View.VISIBLE
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTopBarConfigBinding) {
            binding.apply {
                tvApply.setOnClickListener {
                    entries.getOrNull(holder.layoutPosition)?.let(::applyPackage)
                }
                tvEdit.setOnClickListener {
                    entries.getOrNull(holder.layoutPosition)?.let(::showEditDialog)
                }
                tvMore.setOnClickListener {
                    entries.getOrNull(holder.layoutPosition)?.let(::showActions)
                }
                root.setOnClickListener {
                    entries.getOrNull(holder.layoutPosition)?.let(::showActions)
                }
            }
        }
    }

    private fun buildInfoText(entry: TopBarConfig.Entry, isActive: Boolean): String {
        return buildString {
            if (isActive) {
                append(getString(R.string.current_applied))
                append(" · ")
            }
            append(styleLabel(entry.config.style))
            if (entry.config.style == TopBarConfig.STYLE_REGULAR) {
                append(" · ")
                append(getString(R.string.corner_scale))
                append(" ")
                append(cornerScaleLabel(entry.config.cornerScale))
                if (!entry.config.wallpaperPath.isNullOrBlank()) {
                    append(" · ")
                    append(getString(R.string.wallpaper))
                }
            }
            append(" · ")
            append(getString(R.string.tag_bar_opacity))
            append(" ")
            append(entry.config.tagBarAlpha)
            append("%")
            if (entry.config.updatedAt > 0) {
                append(" · ")
                append(dateFormat.format(Date(entry.config.updatedAt)))
            }
        }
    }
}
