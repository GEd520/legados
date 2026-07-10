package io.legado.app.ui.book.read.config

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.annotation.ColorInt
import androidx.core.widget.doAfterTextChanged
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogHighlightRuleEditBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.observeEvent
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.file.HandleFileContract

class HighlightRuleEditDialog @JvmOverloads constructor(
    private val sourceRule: HighlightRule? = null,
    private val defaultGroup: String? = null,
    private val onSave: (HighlightRule) -> Unit = {},
) : HighlightRuleBottomSheetFragment(R.layout.dialog_highlight_rule_edit, true),
    ColorPickerDialogListener {

    private val binding by viewBinding(DialogHighlightRuleEditBinding::bind)
    private lateinit var editingRule: HighlightRule
    private lateinit var groupItems: List<String>
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private var accentColor = 0
    private var isRegexMode = false

    private val selectImageResult = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val rawPath = RealPathUtil.getPath(requireContext(), uri) ?: uri.toString()
            val savedPath = TextLine.copyBgImageToInternal(requireContext(), rawPath)
            binding.etBgImage.setText(savedPath ?: rawPath)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initTheme()
        editingRule = sourceRule?.copy() ?: HighlightRule(
            group = defaultGroup ?: HighlightRuleGroupStore.DEFAULT_GROUP
        )
        groupItems = HighlightRuleGroupStore.load(requireContext())

        binding.tvPageTitle.text =
            getString(if (sourceRule == null) R.string.highlight_rule_add else R.string.highlight_rule_edit)

        binding.spGroup.adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_text_common,
            groupItems
        ).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        binding.spTarget.adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_text_common,
            listOf("作用于全部", "作用于标题", "作用于正文")
        ).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        binding.spUnderlineMode.adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_text_common,
            listOf("无", "实线下划线", "虚线下划线", "波浪下划线", "标题强调条", "自定义SVG")
        ).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        binding.spBgImageFit.adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_text_common,
            listOf("平铺", "拉伸填充", "居中裁剪")
        ).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }

        bindData()
        bindEvents()
        updatePreview()
    }

    override fun observeLiveBus() {
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) {
            if (it.contains(1) || it.contains(2)) {
                initTheme()
                updatePreview()
            }
        }
    }

    private fun initTheme() {
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        primaryTextColor = requireContext().getPrimaryTextColor(isLight)
        secondaryTextColor = requireContext().getSecondaryTextColor(isLight)
        accentColor = requireContext().accentColor

        val cardBg = if (isLight) {
            ColorUtils.blendColors(bg, 0xFFFFFFFF.toInt(), 0.7f)
        } else {
            ColorUtils.blendColors(bg, 0xFFFFFFFF.toInt(), 0.08f)
        }

        val cardStrokeColor = if (isLight) {
            ColorUtils.blendColors(0xFF000000.toInt(), bg, 0.88f)
        } else {
            ColorUtils.blendColors(0xFFFFFFFF.toInt(), bg, 0.85f)
        }

        val inputStrokeColor = if (isLight) {
            ColorUtils.blendColors(0xFF000000.toInt(), bg, 0.82f)
        } else {
            ColorUtils.blendColors(0xFFFFFFFF.toInt(), bg, 0.80f)
        }

        val inputBgColor = if (isLight) {
            ColorUtils.blendColors(bg, 0xFFFFFFFF.toInt(), 0.5f)
        } else {
            ColorUtils.blendColors(bg, 0xFFFFFFFF.toInt(), 0.06f)
        }

        val density = resources.displayMetrics.density

        binding.sheetContainer.background?.mutate()?.setTint(bg)
        binding.tvPageTitle.setTextColor(primaryTextColor)

        binding.ivBack.background?.mutate()?.setTint(cardBg)
        binding.ivBack.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)

        binding.tvSaveAction.background?.mutate()?.setTint(accentColor)
        binding.tvSaveAction.setTextColor(
            if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        )

        binding.switchEnable.trackTintList = android.content.res.ColorStateList.valueOf(accentColor)
        binding.switchEnable.thumbTintList = android.content.res.ColorStateList.valueOf(accentColor)

        val cardDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f * density
            setColor(cardBg)
            setStroke((1f * density).toInt().coerceAtLeast(1), cardStrokeColor)
        }
        binding.cardInfo.background = cardDrawable
        binding.cardStyle.background = makeCardDrawable(cardBg, cardStrokeColor, 24f, density)
        binding.cardPreview.background = makeCardDrawable(cardBg, cardStrokeColor, 24f, density)

        binding.etPattern.setTextColor(primaryTextColor)
        binding.etPattern.setHintTextColor(secondaryTextColor)
        binding.etName.setTextColor(primaryTextColor)
        binding.etName.setHintTextColor(secondaryTextColor)
        binding.etTextColor.setTextColor(primaryTextColor)
        binding.etTextColor.setHintTextColor(secondaryTextColor)
        binding.etUnderlineColor.setTextColor(primaryTextColor)
        binding.etUnderlineColor.setHintTextColor(secondaryTextColor)
        binding.etUnderlineWidth.setTextColor(primaryTextColor)
        binding.etUnderlineOffset.setHintTextColor(secondaryTextColor)
        binding.etUnderlineOffset.setTextColor(primaryTextColor)
        binding.etSvgPath.setTextColor(primaryTextColor)
        binding.etSvgPath.setHintTextColor(secondaryTextColor)
        binding.etBgImage.setTextColor(primaryTextColor)
        binding.etBgImage.setHintTextColor(secondaryTextColor)
        binding.tvBgImagePick.setTextColor(primaryTextColor)
        binding.etSampleText.setTextColor(primaryTextColor)
        binding.etSampleText.setHintTextColor(secondaryTextColor)

        binding.tvRegexToggle.setTextColor(primaryTextColor)
        binding.tvRegexToggle.background?.mutate()?.setTint(bg)
        binding.tvWidthMinus.setTextColor(primaryTextColor)
        binding.tvWidthPlus.setTextColor(primaryTextColor)
        binding.tvOffsetMinus.setTextColor(primaryTextColor)
        binding.tvOffsetPlus.setTextColor(primaryTextColor)

        val inputBg = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        val previewBg = makeInputDrawable(inputBgColor, inputStrokeColor, 16f, density)
        binding.etPattern.background = inputBg
        binding.etName.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.spGroup.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.spTarget.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etTextColor.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.spUnderlineMode.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etUnderlineColor.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etSvgPath.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.tvPreview.background = previewBg
        binding.etBgImage.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.tvBgImagePick.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etSampleText.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.spBgImageFit.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.tvWidthMinus.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.tvWidthPlus.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etUnderlineWidth.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.tvOffsetMinus.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.tvOffsetPlus.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etUnderlineOffset.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
    }

    private fun makeCardDrawable(
        fillColor: Int, strokeColor: Int, cornerDp: Float, density: Float
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerDp * density
        setColor(fillColor)
        setStroke((1f * density).toInt().coerceAtLeast(1), strokeColor)
    }

    private fun makeInputDrawable(
        fillColor: Int, strokeColor: Int, cornerDp: Float, density: Float
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerDp * density
        setColor(fillColor)
        setStroke((1f * density).toInt().coerceAtLeast(1), strokeColor)
    }

    private fun bindData() {
        binding.switchEnable.isChecked = editingRule.enabled
        binding.etName.setText(editingRule.name)
        binding.etPattern.setText(editingRule.pattern)
        binding.etTextColor.setText(editingRule.textColor?.toHexColor().orEmpty())
        binding.etUnderlineColor.setText(editingRule.underlineColor?.toHexColor().orEmpty())
        binding.etUnderlineWidth.setText(editingRule.underlineWidth.toString())
        binding.etUnderlineOffset.setText(editingRule.underlineOffset.formatDistance())
        binding.etSvgPath.setText(editingRule.underlineSvgPath.orEmpty())
        binding.etBgImage.setText(editingRule.bgImage ?: editingRule.backgroundColor?.toHexColor().orEmpty())
        binding.etSampleText.setText(editingRule.sampleText.ifBlank { editingRule.normalizedSampleText() })
        binding.spBgImageFit.setSelection(editingRule.bgImageFit.coerceIn(0, 2))
        binding.sbBgImageScale.progress = (editingRule.bgImageScale.coerceIn(0.1f, 5f) * 10).toInt()
        binding.tvBgImageScale.text = "${editingRule.bgImageScale.coerceIn(0.1f, 5f).formatScale()}x"
        binding.spUnderlineMode.setSelection(editingRule.underlineMode.coerceIn(0, 5))
        val groupIndex = groupItems.indexOf(editingRule.group).takeIf { it >= 0 } ?: 0
        binding.spGroup.setSelection(groupIndex)
        binding.spTarget.setSelection(editingRule.targetScope.coerceIn(0, 2))
        
        updateColorPreview(binding.viewTextColorPreview, editingRule.textColor)
        updateColorPreview(binding.viewUnderlineColorPreview, editingRule.underlineColor)
        updateBgImagePreview()
        
        updateSvgPathVisibility(editingRule.underlineMode)
    }

    private fun bindEvents() {
        binding.sheetContainer.setOnClickListener { }
        binding.ivBack.setOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.tvSaveAction.setOnClickListener {
            saveRule()
        }
        binding.llTextColor.setOnClickListener {
            showColorPicker(1, editingRule.textColor ?: Color.BLACK)
        }
        binding.llUnderlineColor.setOnClickListener {
            showColorPicker(2, editingRule.underlineColor ?: Color.BLACK)
        }
        binding.tvRegexToggle.setOnClickListener {
            isRegexMode = !isRegexMode
            updateRegexToggle()
        }
        binding.tvWidthMinus.setOnClickListener {
            adjustWidth(-0.5f)
        }
        binding.tvWidthPlus.setOnClickListener {
            adjustWidth(0.5f)
        }
        binding.tvOffsetMinus.setOnClickListener {
            adjustOffset(-1f)
        }
        binding.tvOffsetPlus.setOnClickListener {
            adjustOffset(1f)
        }
        binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            editingRule.enabled = isChecked
        }
        binding.etName.doAfterTextChanged {
            editingRule.name = it?.toString().orEmpty()
        }
        binding.etPattern.doAfterTextChanged {
            editingRule.pattern = it?.toString().orEmpty()
            updatePreview()
        }
        binding.etTextColor.doAfterTextChanged {
            editingRule.textColor = parseColorOrNull(it?.toString().orEmpty())
            updateColorPreview(binding.viewTextColorPreview, editingRule.textColor)
            updatePreview()
        }
        binding.etUnderlineColor.doAfterTextChanged {
            editingRule.underlineColor = parseColorOrNull(it?.toString().orEmpty())
            updateColorPreview(binding.viewUnderlineColorPreview, editingRule.underlineColor)
            updatePreview()
        }
        binding.etUnderlineWidth.doAfterTextChanged {
            editingRule.underlineWidth = it?.toString()?.toFloatOrNull()?.coerceIn(0.1f, 10f) ?: 1f
            updatePreview()
        }
        binding.etUnderlineOffset.doAfterTextChanged {
            editingRule.underlineOffset = it?.toString()?.toFloatOrNull()?.coerceIn(0f, 20f) ?: 2f
            updatePreview()
        }
        binding.etSvgPath.doAfterTextChanged {
            editingRule.underlineSvgPath = it?.toString().orEmpty()
            updatePreview()
        }
        binding.etBgImage.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            val color = parseColorOrNull(value)
            editingRule.backgroundColor = color
            editingRule.bgImage = value.takeIf { text -> text.isNotBlank() && color == null }
            updateBgImagePreview()
            updatePreview()
        }
        binding.etSampleText.doAfterTextChanged {
            editingRule.sampleText = it?.toString().orEmpty()
            updatePreview()
        }
        binding.viewBgImagePreview.setOnClickListener {
            showBackgroundColorPicker()
        }
        binding.tvBgImagePick.setOnClickListener {
            showBgImagePicker()
        }
        binding.spBgImageFit.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    editingRule.bgImageFit = position
                    updatePreview()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        binding.sbBgImageScale.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val scale = (progress.coerceAtLeast(1) / 10f).coerceIn(0.1f, 5f)
                    editingRule.bgImageScale = scale
                    binding.tvBgImageScale.text = "${scale.formatScale()}x"
                    if (fromUser) updatePreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
        )
        binding.spUnderlineMode.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    editingRule.underlineMode = position
                    updateSvgPathVisibility(position)
                    updatePreview()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        binding.spGroup.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    editingRule.group = groupItems.getOrElse(position) { HighlightRuleGroupStore.DEFAULT_GROUP }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        binding.spTarget.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    editingRule.targetScope = position.coerceIn(0, 2)
                    updatePreview()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
    }

    private fun updateRegexToggle() {
        if (isRegexMode) {
            binding.tvRegexToggle.setTextColor(accentColor)
        } else {
            binding.tvRegexToggle.setTextColor(primaryTextColor)
        }
    }

    private fun adjustWidth(delta: Float) {
        val current = binding.etUnderlineWidth.text?.toString()?.toFloatOrNull() ?: 1f
        val newValue = (current + delta).coerceIn(0.1f, 10f)
        binding.etUnderlineWidth.setText(String.format("%.1f", newValue))
    }

    private fun adjustOffset(delta: Float) {
        val current = binding.etUnderlineOffset.text?.toString()?.toFloatOrNull() ?: 2f
        val newValue = (current + delta).coerceIn(0f, 20f)
        binding.etUnderlineOffset.setText(newValue.formatDistance())
    }

    private fun updateColorPreview(view: View, color: Int?) {
        val drawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 8f * resources.displayMetrics.density
            setColor(color ?: 0xFFDDDDDD.toInt())
            setStroke(2 * resources.displayMetrics.density.toInt(), 0xFFBBBBBB.toInt())
        }
        view.background = drawable
    }

    private fun updateSvgPathVisibility(mode: Int) {
        binding.llSvgPath.visibility = if (mode == 5) View.VISIBLE else View.GONE
    }

    private fun updateBgImagePreview() {
        editingRule.backgroundColor?.let { color ->
            binding.viewBgImagePreview.setBackgroundColor(color)
            return
        }
        val path = editingRule.bgImage.orEmpty()
        if (path.isBlank()) {
            binding.viewBgImagePreview.setBackgroundColor(Color.TRANSPARENT)
            return
        }
        val bitmap = TextLine.getBgBitmap(path)
        if (bitmap != null) {
            val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)
            binding.viewBgImagePreview.background = drawable
        } else {
            binding.viewBgImagePreview.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun showBgImagePicker() {
        val bgImages = try {
            requireContext().assets.list("bg")?.toList().orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        val options = mutableListOf<String>()
        val assetItems = mutableListOf<String>()
        options.add("从手机选择图片")
        options.add("选择背景颜色")
        bgImages.forEach { img ->
            options.add(img)
            assetItems.add(img)
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("选择背景图片")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    selectImageResult.launch {
                        mode = HandleFileContract.IMAGE
                        title = "选择背景图片"
                    }
                } else if (which == 1) {
                    showBackgroundColorPicker()
                } else {
                    val selected = "assets://bg/${assetItems[which - 2]}"
                    editingRule.backgroundColor = null
                    editingRule.bgImage = selected
                    binding.etBgImage.setText(selected)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showBackgroundColorPicker() {
        showColorPicker(
            3,
            editingRule.backgroundColor
                ?: parseColorOrNull(binding.etBgImage.text?.toString().orEmpty())
                ?: 0x66FFFF00
        )
    }

    private fun saveRule() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val pattern = binding.etPattern.text?.toString()?.trim().orEmpty()
        if (pattern.isBlank()) {
            toastOnUi(R.string.highlight_rule_pattern_required)
            return
        }
        val regexError = validatePattern(pattern)
        if (regexError != null) {
            binding.tvPatternError.visibility = View.VISIBLE
            binding.tvPatternError.text = regexError
            return
        }
        val bgInput = readBackgroundInput()
        editingRule = editingRule.copy(
            id = editingRule.id.ifBlank { System.currentTimeMillis().toString() },
            name = name.ifBlank { pattern },
            pattern = pattern,
            sampleText = binding.etSampleText.text?.toString().orEmpty(),
            group = groupItems.getOrElse(binding.spGroup.selectedItemPosition) {
                HighlightRuleGroupStore.DEFAULT_GROUP
            },
            targetScope = binding.spTarget.selectedItemPosition.coerceIn(0, 2),
            enabled = binding.switchEnable.isChecked,
            textColor = parseColorOrNull(binding.etTextColor.text?.toString().orEmpty()),
            underlineMode = binding.spUnderlineMode.selectedItemPosition,
            underlineColor = parseColorOrNull(binding.etUnderlineColor.text?.toString().orEmpty()),
            underlineWidth = binding.etUnderlineWidth.text?.toString()?.toFloatOrNull()?.coerceIn(0.1f, 10f) ?: 1f,
            underlineOffset = binding.etUnderlineOffset.text?.toString()?.toFloatOrNull()?.coerceIn(0f, 20f) ?: 2f,
            underlineSvgPath = binding.etSvgPath.text?.toString().orEmpty().takeIf { binding.spUnderlineMode.selectedItemPosition == 5 }.orEmpty(),
            backgroundColor = bgInput.color,
            bgImage = bgInput.imagePath,
            bgImageFit = binding.spBgImageFit.selectedItemPosition,
            bgImageScale = (binding.sbBgImageScale.progress.coerceAtLeast(1) / 10f).coerceIn(0.1f, 5f)
        )
        onSave(editingRule)
        dismissAllowingStateLoss()
    }

    private fun updatePreview() {
        val pattern = binding.etPattern.text?.toString().orEmpty()
        binding.tvPatternError.visibility = View.GONE
        val patternError = validatePattern(pattern)
        if (patternError != null && pattern.isNotBlank()) {
            binding.tvPatternError.visibility = View.VISIBLE
            binding.tvPatternError.text = patternError
        }
        val bgInput = readBackgroundInput()
        binding.tvPreview.text = HighlightRulePreview.build(
            editingRule.copy(
                name = binding.etName.text?.toString().orEmpty(),
                pattern = pattern,
                sampleText = binding.etSampleText.text?.toString().orEmpty(),
                group = groupItems.getOrElse(binding.spGroup.selectedItemPosition) {
                    HighlightRuleGroupStore.DEFAULT_GROUP
                },
                targetScope = binding.spTarget.selectedItemPosition.coerceIn(0, 2),
                textColor = parseColorOrNull(binding.etTextColor.text?.toString().orEmpty()),
                underlineMode = binding.spUnderlineMode.selectedItemPosition,
                underlineColor = parseColorOrNull(binding.etUnderlineColor.text?.toString().orEmpty()),
                underlineWidth = binding.etUnderlineWidth.text?.toString()?.toFloatOrNull()?.coerceIn(0.1f, 10f) ?: 1f,
                underlineOffset = binding.etUnderlineOffset.text?.toString()?.toFloatOrNull()?.coerceIn(0f, 20f) ?: 2f,
                underlineSvgPath = binding.etSvgPath.text?.toString().orEmpty(),
                backgroundColor = bgInput.color,
                bgImage = bgInput.imagePath,
                bgImageFit = binding.spBgImageFit.selectedItemPosition,
                bgImageScale = (binding.sbBgImageScale.progress.coerceAtLeast(1) / 10f).coerceIn(0.1f, 5f)
            )
        )
    }

    private fun validatePattern(pattern: String): String? {
        if (pattern.isBlank()) return null
        return kotlin.runCatching { Regex(pattern) }.exceptionOrNull()?.localizedMessage
    }

    private fun Float.formatScale(): String {
        return if (this == this.toInt().toFloat()) {
            this.toInt().toString()
        } else {
            String.format("%.1f", this)
        }
    }

    private fun Float.formatDistance(): String {
        return if (this == this.toInt().toFloat()) {
            this.toInt().toString()
        } else {
            String.format("%.1f", this)
        }
    }

    private fun parseColorOrNull(value: String): Int? {
        val text = value.trim()
        if (text.isEmpty()) return null
        return kotlin.runCatching {
            val normalized = if (text.startsWith("#")) text else "#$text"
            Color.parseColor(normalized)
        }.getOrNull()
    }

    private fun readBackgroundInput(): BackgroundInput {
        val value = binding.etBgImage.text?.toString().orEmpty()
        val color = parseColorOrNull(value)
        return BackgroundInput(
            color = color,
            imagePath = value.takeIf { it.isNotBlank() && color == null }
        )
    }

    private fun Int.toHexColor(): String = String.format("#%08X", this)

    private data class BackgroundInput(
        val color: Int?,
        val imagePath: String?,
    )

    private fun showColorPicker(dialogId: Int, currentColor: Int) {
        val dialog = ColorPickerDialog.newBuilder()
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setColor(currentColor)
            .setShowAlphaSlider(false)
            .setAllowPresets(true)
            .setAllowCustom(true)
            .setDialogId(dialogId)
            .create()
        dialog.setColorPickerDialogListener(this)
        requireActivity().supportFragmentManager
            .beginTransaction()
            .add(dialog, "color_picker_$dialogId")
            .commitAllowingStateLoss()
    }

    override fun onColorSelected(dialogId: Int, @ColorInt color: Int) {
        when (dialogId) {
            1 -> {
                editingRule.textColor = color
                binding.etTextColor.setText(color.toHexColor())
                updateColorPreview(binding.viewTextColorPreview, color)
                updatePreview()
            }
            2 -> {
                editingRule.underlineColor = color
                binding.etUnderlineColor.setText(color.toHexColor())
                updateColorPreview(binding.viewUnderlineColorPreview, color)
                updatePreview()
            }
            3 -> {
                editingRule.backgroundColor = color
                editingRule.bgImage = null
                binding.etBgImage.setText(color.toHexColor())
                binding.viewBgImagePreview.setBackgroundColor(color)
                updatePreview()
            }
        }
    }

    override fun onDialogDismissed(dialogId: Int) {
        // no-op
    }
}
