package io.legado.app.ui.config

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityThemeManageBinding
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.applyUiBodyTypefaceDeep
import io.legado.app.lib.theme.applyUiLabelStyle
import io.legado.app.lib.theme.applyUiSectionTitleStyle
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.model.ImageProvider
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.SvgUtils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BubbleManageActivity : BaseActivity<ActivityThemeManageBinding>(), ColorPickerDialogListener {

    override val binding by viewBinding(ActivityThemeManageBinding::inflate)

    private val adapter = Adapter()
    private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    private var editingConfig: BubblePackageManager.Config? = null
    private var editingRoot: LinearLayout? = null
    private var svgCursorPosition: Int = 0
    private val importPackage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let(::importZip)
    }
    private val svgEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra("text")?.let { text ->
                editingConfig = editingConfig?.copy(svgTemplate = text)
                svgCursorPosition = result.data?.getIntExtra("cursorPosition", text.length) ?: text.length
                refreshEditDialog()
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        loadPackages()
    }

    override fun onResume() {
        super.onResume()
        loadPackages()
    }

    private fun initView() = binding.run {
        titleBar.title = getString(R.string.bubble_manage)
        tabContainer.visibility = View.GONE
        tvSummary.text = getString(R.string.bubble_manage_summary)
        tvSummary.setTextColor(themeSecondaryTextColor())
        recyclerView.layoutManager = LinearLayoutManager(this@BubbleManageActivity)
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        tvAddTheme.text = getString(R.string.add)
        tvAddTheme.setTextColor(themePrimaryTextColor())
        tvAddTheme.background = UiCorner.actionSelector(
            ContextCompat.getColor(this@BubbleManageActivity, R.color.background_card),
            ContextCompat.getColor(this@BubbleManageActivity, R.color.background_menu),
            UiCorner.actionRadius(this@BubbleManageActivity)
        )
        tvAddTheme.setOnClickListener { showAddActions() }
        root.applyUiBodyTypefaceDeep(uiTypeface())
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_HELP, 0, R.string.help).apply {
            setIcon(R.drawable.ic_help)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_HELP -> {
                showBubbleHelp()
                true
            }
            else -> super.onCompatOptionsItemSelected(item)
        }
    }

    private fun loadPackages() {
        adapter.items = BubblePackageManager.loadEntries()
    }

    private fun showAddActions() {
        selector(getString(R.string.add), listOf("手动创建", "导入 zip")) { _, index ->
            when (index) {
                0 -> showEditDialog(null)
                1 -> importPackage.launch {
                    mode = HandleFileContract.FILE
                    title = "导入 zip"
                    allowExtensions = arrayOf("zip")
                }
            }
        }
    }

    private fun showActions(entry: BubblePackageManager.Entry) {
        val actions = if (entry.source == BubblePackageManager.Source.BUILTIN) {
            listOf(Action.APPLY)
        } else {
            listOf(Action.APPLY, Action.EDIT, Action.DELETE)
        }
        selector(entry.config.name, actions.map { it.title }) { _, index ->
            when (actions[index]) {
                Action.APPLY -> applyEntry(entry)
                Action.EDIT -> showEditDialog(entry)
                Action.DELETE -> confirmDelete(entry)
            }
        }
    }

    private fun showEditDialog(entry: BubblePackageManager.Entry?) {
        if (entry?.source == BubblePackageManager.Source.BUILTIN) return
        editingConfig = (entry?.config ?: BubblePackageManager.builtinConfig().copy(
            name = "自定义段评气泡",
            dirName = "",
            updatedAt = System.currentTimeMillis()
        )).copy(dirName = entry?.dirName.orEmpty())
        val root = buildEditView()
        editingRoot = root
        alert(if (entry == null) R.string.add else R.string.edit) {
            customView { root }
            okButton {
                captureEditFields()
                val config = editingConfig ?: return@okButton
                val next = config.copy(
                    name = root.findViewWithTag<EditText>(TAG_NAME)?.text?.toString().orEmpty(),
                    dirName = entry?.dirName.orEmpty()
                )
                runCatching {
                    requireValidSvg(next)
                    BubblePackageManager.addOrUpdate(next, entry)
                }.onSuccess {
                    if (entry?.dirName == BubblePackageManager.activeDirName()) {
                        notifyBubbleChanged()
                    }
                    loadPackages()
                }.onFailure {
                    toastOnUi(it.localizedMessage ?: getString(R.string.error))
                }
            }
            cancelButton()
        }
    }

    private fun buildEditView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2.dp, 2.dp, 2.dp, 4.dp)
            populateEditView(this)
            applyUiBodyTypefaceDeep(this@BubbleManageActivity.uiTypeface())
        }
    }

    private fun populateEditView(root: LinearLayout) {
        val config = editingConfig ?: return
        root.addView(PackageManageUi.nameInput(this, config.name, getString(R.string.bubble_name)).apply { tag = TAG_NAME })
        root.addView(PackageManageUi.optionRow(this, getString(R.string.bubble_size_scale), "%.1f".format(Locale.ROOT, config.sizeScale)) {
            showSizeScalePicker()
        })
        root.addView(colorRow(getString(R.string.bubble_day_normal), colorOrDefault(config.dayNormalColor, false), COLOR_DAY_NORMAL))
        root.addView(colorRow(getString(R.string.bubble_day_emphasis), colorOrDefault(config.dayEmphasisColor, true), COLOR_DAY_EMPHASIS))
        root.addView(colorRow(getString(R.string.bubble_night_normal), colorOrDefault(config.nightNormalColor, false), COLOR_NIGHT_NORMAL))
        root.addView(colorRow(getString(R.string.bubble_night_emphasis), colorOrDefault(config.nightEmphasisColor, true), COLOR_NIGHT_EMPHASIS))
        root.addView(PackageManageUi.optionRow(this, "SVG...", "点击编辑，支持 \${color} 和 \${num}") {
            openSvgEditor()
        })
    }

    private fun captureEditFields() {
        val config = editingConfig ?: return
        val root = editingRoot ?: return
        editingConfig = config.copy(
            name = root.findViewWithTag<EditText>(TAG_NAME)?.text?.toString() ?: config.name
        )
    }

    private fun openSvgEditor() {
        captureEditFields()
        val svg = editingConfig?.svgTemplate.orEmpty()
        svgEditLauncher.launch(Intent(this, CodeEditActivity::class.java).apply {
            putExtra("text", svg)
            putExtra("title", "SVG 模板")
            putExtra("cursorPosition", svgCursorPosition.coerceIn(0, svg.length))
        })
    }

    private fun showSizeScalePicker() {
        captureEditFields()
        val config = editingConfig ?: return
        NumberPickerDialog(this, isDecimalMode = true)
            .setTitle(getString(R.string.bubble_size_scale))
            .setMinValue((BubblePackageManager.MIN_SIZE_SCALE * 10).toInt())
            .setMaxValue((BubblePackageManager.MAX_SIZE_SCALE * 10).toInt())
            .setValue((config.sizeScale.coerceIn(BubblePackageManager.MIN_SIZE_SCALE, BubblePackageManager.MAX_SIZE_SCALE) * 10).toInt())
            .show {
                captureEditFields()
                val latest = editingConfig ?: config
                editingConfig = latest.copy(
                    sizeScale = (it / 10f).coerceIn(BubblePackageManager.MIN_SIZE_SCALE, BubblePackageManager.MAX_SIZE_SCALE)
                )
                refreshEditDialog()
            }
    }

    private fun colorRow(title: String, value: String, target: Int): View {
        return PackageManageUi.optionRow(this, title, value.uppercase(Locale.ROOT), value.toColorInt()) {
            ColorPickerDialog.newBuilder()
                .setColor(value.toColorInt())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(target)
                .show(this)
        }
    }

    private fun refreshEditDialog() {
        val root = editingRoot ?: return
        root.removeAllViews()
        populateEditView(root)
    }

    private fun confirmDelete(entry: BubblePackageManager.Entry) {
        alert(R.string.delete) {
            setMessage(getString(R.string.sure_del))
            okButton {
                BubblePackageManager.deleteLocal(entry)
                notifyBubbleChanged()
                loadPackages()
            }
            cancelButton()
        }
    }

    private fun applyEntry(entry: BubblePackageManager.Entry) {
        BubblePackageManager.apply(entry)
        notifyBubbleChanged()
        loadPackages()
        toastOnUi(R.string.success)
    }

    private fun importZip(uri: Uri) {
        lifecycleScope.launch {
            kotlin.runCatching {
                val file = externalFiles.getFile("bubbleImports", "import_${System.currentTimeMillis()}.zip")
                file.parentFile?.mkdirs()
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                } ?: throw IllegalArgumentException(getString(R.string.file_not_exist))
                withContext(Dispatchers.IO) { BubblePackageManager.importZip(file) }
            }.onSuccess {
                toastOnUi(R.string.import_success)
                loadPackages()
            }.onFailure {
                toastOnUi(it.localizedMessage)
            }
        }
    }

    private fun notifyBubbleChanged() {
        ImageProvider.clear()
        postEvent(EventBus.REFRESH_BOOK_INFO, false)
    }

    private fun requireValidSvg(config: BubblePackageManager.Config) {
        val color = config.dayNormalColor?.takeIf { it.isNotBlank() }
            ?: BubblePackageManager.DEFAULT_NORMAL_COLOR
        color.toColorInt()
        val svg = config.svgTemplate
            .replace("\${color}", color)
            .replace("\${num}", "1")
        require(SvgUtils.createBitmap(ByteArrayInputStream(svg.toByteArray()), 128, 128) != null) {
            getString(R.string.error_decode_bitmap)
        }
    }

    private fun previewBitmap(config: BubblePackageManager.Config) = runCatching {
        val color = config.dayEmphasisColor?.takeIf { it.isNotBlank() }
            ?: BubblePackageManager.DEFAULT_EMPHASIS_COLOR
        val svg = config.svgTemplate
            .replace("\${color}", color)
            .replace("\${num}", "12")
        SvgUtils.createBitmap(ByteArrayInputStream(svg.toByteArray()), 128, 128)
    }.getOrNull()

    override fun onColorSelected(dialogId: Int, color: Int) {
        captureEditFields()
        val config = editingConfig ?: return
        val hex = String.format(Locale.ROOT, "#%06X", color and 0x00FFFFFF)
        editingConfig = when (dialogId) {
            COLOR_DAY_NORMAL -> config.copy(dayNormalColor = hex)
            COLOR_DAY_EMPHASIS -> config.copy(dayEmphasisColor = hex)
            COLOR_NIGHT_NORMAL -> config.copy(nightNormalColor = hex)
            COLOR_NIGHT_EMPHASIS -> config.copy(nightEmphasisColor = hex)
            else -> config
        }
        refreshEditDialog()
    }

    override fun onDialogDismissed(dialogId: Int) = Unit

    private fun colorOrDefault(value: String?, emphasis: Boolean): String {
        val fallback = if (emphasis) {
            BubblePackageManager.DEFAULT_EMPHASIS_COLOR
        } else {
            BubblePackageManager.DEFAULT_NORMAL_COLOR
        }
        val normalized = value?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("#")) it else "#$it" }
            ?: fallback
        return runCatching {
            normalized.toColorInt()
            normalized
        }.getOrDefault(fallback)
    }

    private fun showBubbleHelp() {
        alert(getString(R.string.help), """
            段评气泡用于把规则里的 dp 图片转成原生 SVG 气泡。

            接入格式：
            <img src="dp:12,{&quot;pclick&quot;:&quot;...&quot;,&quot;status&quot;:&quot;normal&quot;}">

            dp: 后面的数字会替换 SVG 模板里的 ${'$'}{num}。
            status 可选：normal 使用常规色，emphasis 使用强调色；不写 status 时默认 normal。
            SVG 模板支持 ${'$'}{color} 和 ${'$'}{num} 两个占位。
            内置气泡只读；需要自定义时请通过添加创建新气泡。
        """.trimIndent()) {
            okButton()
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {
        private var activeDirName: String = BubblePackageManager.activeDirName()

        var items: List<BubblePackageManager.Entry> = emptyList()
            set(value) {
                field = value
                activeDirName = BubblePackageManager.activeDirName()
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(createItemView(parent))
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        override fun onViewRecycled(holder: Holder) {
            holder.cancelPreview()
            super.onViewRecycled(holder)
        }

        private fun createItemView(parent: ViewGroup): LinearLayout {
            return LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(12.dp, 10.dp, 12.dp, 10.dp)
                minimumHeight = BUBBLE_ITEM_MIN_HEIGHT_DP.dp
                background = UiCorner.panelRounded(
                    this@BubbleManageActivity,
                    ContextCompat.getColor(parent.context, R.color.background_card),
                    UiCorner.panelRadius(this@BubbleManageActivity)
                )
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dp
                }
            }
        }

        inner class Holder(private val itemRoot: LinearLayout) : RecyclerView.ViewHolder(itemRoot) {
            private var previewJob: Job? = null
            private val preview = ImageView(itemRoot.context).apply {
                layoutParams = LinearLayout.LayoutParams(BUBBLE_PREVIEW_BOX_DP.dp, BUBBLE_PREVIEW_BOX_DP.dp)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(6.dp, 6.dp, 6.dp, 6.dp)
                background = UiCorner.opaqueRounded(
                    ContextCompat.getColor(itemRoot.context, R.color.background_menu),
                    UiCorner.actionRadius(this@BubbleManageActivity)
                )
            }
            private val textBox = LinearLayout(itemRoot.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = 12.dp
                    rightMargin = 12.dp
                }
            }
            private val title = TextView(itemRoot.context).apply {
                applyUiSectionTitleStyle(this@BubbleManageActivity)
                setTextColor(themePrimaryTextColor())
            }
            private val info = TextView(itemRoot.context).apply {
                applyUiLabelStyle(this@BubbleManageActivity)
                setTextColor(themeSecondaryTextColor())
            }
            private val action = TextView(itemRoot.context).apply {
                gravity = android.view.Gravity.CENTER
                minWidth = 62.dp
                minHeight = 36.dp
                background = UiCorner.actionSelector(
                    Color.TRANSPARENT,
                    ContextCompat.getColor(itemRoot.context, R.color.background_menu),
                    UiCorner.actionRadius(this@BubbleManageActivity)
                )
                setTextColor(themePrimaryTextColor())
                typeface = this@BubbleManageActivity.uiTypeface()
            }

            init {
                textBox.addView(title)
                textBox.addView(info)
                itemRoot.addView(preview)
                itemRoot.addView(textBox)
                itemRoot.addView(action)
            }

            fun bind(entry: BubblePackageManager.Entry) {
                val active = activeDirName == entry.dirName
                val previewKey = buildPreviewKey(entry)
                title.text = entry.config.name
                title.setTextColor(themePrimaryTextColor())
                info.text = buildString {
                    if (active) append("${getString(R.string.applied)} · ")
                    append(sourceLabel(entry.source))
                    append(" · ")
                    append(if (entry.config.updatedAt > 0L) dateFormat.format(Date(entry.config.updatedAt)) else "内置")
                }
                info.setTextColor(themeSecondaryTextColor())
                preview.setTag(R.id.bubble_preview_key, previewKey)
                preview.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
                previewJob?.cancel()
                previewJob = lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        previewBitmap(entry.config)
                    }
                    if (preview.getTag(R.id.bubble_preview_key) != previewKey) {
                        return@launch
                    }
                    if (bitmap != null) {
                        preview.setImageBitmap(bitmap)
                    } else {
                        preview.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
                    }
                }
                action.text = if (active) getString(R.string.applied) else getString(R.string.apply)
                action.setTextColor(if (active) accentColor else themePrimaryTextColor())
                action.setOnClickListener { applyEntry(entry) }
                itemRoot.setOnClickListener { showActions(entry) }
            }

            fun cancelPreview() {
                previewJob?.cancel()
                previewJob = null
                preview.setTag(R.id.bubble_preview_key, null)
            }

            private fun buildPreviewKey(entry: BubblePackageManager.Entry): String {
                val config = entry.config
                return buildString {
                    append(entry.dirName)
                    append('#')
                    append(config.updatedAt)
                    append('#')
                    append(config.svgTemplate.hashCode())
                    append('#')
                    append(config.dayEmphasisColor)
                }
            }
        }
    }

    private fun sourceLabel(source: BubblePackageManager.Source): String {
        return when (source) {
            BubblePackageManager.Source.BUILTIN -> "内置"
            BubblePackageManager.Source.LOCAL -> "本地"
        }
    }

    private fun themePrimaryTextColor(): Int {
        return ContextCompat.getColor(this, R.color.primaryText)
    }

    private fun themeSecondaryTextColor(): Int {
        return ContextCompat.getColor(this, R.color.secondaryText)
    }

    private enum class Action(val title: String) {
        APPLY("应用"),
        EDIT("编辑"),
        DELETE("删除")
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private companion object {
        private const val MENU_HELP = 0x6802
        private const val COLOR_DAY_NORMAL = 0x6811
        private const val COLOR_DAY_EMPHASIS = 0x6812
        private const val COLOR_NIGHT_NORMAL = 0x6813
        private const val COLOR_NIGHT_EMPHASIS = 0x6814
        private const val TAG_NAME = "name"
        private const val BUBBLE_PREVIEW_BOX_DP = 64
        private const val BUBBLE_ITEM_MIN_HEIGHT_DP = 86
    }
}
