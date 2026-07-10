package io.legado.app.ui.book.read.config

import android.content.Context
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.text.StrokeTextView
import io.legado.app.utils.dpToPx

/**
 * 字重切换控件
 * 
 * 支持两种模式：
 * - 粗略模式：三个固定选项（正常/粗体/细体）
 * - 精细模式：SeekBar 进度条，支持 100~900 的字重值
 * 
 * 精细模式需要 Android 9 (API 28) 以上版本支持
 */
class TextFontWeightConverter(context: Context, attrs: AttributeSet?) :
    StrokeTextView(context, attrs) {

    private val spannableString = SpannableString(context.getString(R.string.font_weight_text))
    private var enabledSpan: ForegroundColorSpan = ForegroundColorSpan(context.accentColor)
    private var onChanged: (() -> Unit)? = null

    init {
        text = spannableString
        if (!isInEditMode) {
            upUi(ReadBookConfig.textBold)
        }
        setOnClickListener {
            showFontWeightDialog()
        }
    }

    fun upUi(type: Int) {
        spannableString.removeSpan(enabledSpan)
        if (AppConfig.textBoldMode == 0) {
            when (type) {
                0 -> spannableString.setSpan(enabledSpan, 0, 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                1 -> spannableString.setSpan(enabledSpan, 2, 3, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                2 -> spannableString.setSpan(enabledSpan, 4, 5, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }
        text = spannableString
    }

    private fun showFontWeightDialog() {
        var currentMode = AppConfig.textBoldMode
        val savedTextBold = ReadBookConfig.textBold
        var currentBoldValue = if (currentMode == 0) {
            savedTextBold
        } else {
            fineToCoarseWeight(savedTextBold.coerceIn(100, 900))
        }
        var currentTitleBoldValue = ReadBookConfig.titleBold.takeIf { it in 100..900 } ?: 700
        var tempTextValue = if (currentMode == 1) {
            savedTextBold.coerceIn(100, 900)
        } else {
            coarseToFineWeight(currentBoldValue)
        }
        var tempTitleValue = currentTitleBoldValue
        
        // 视图引用，用于后续切换
        var coarseView: LinearLayout? = null
        var fineView: LinearLayout? = null
        fun applyFontWeight() {
            if (currentMode == 1) {
                ReadBookConfig.textBold = tempTextValue
                ReadBookConfig.titleBold = tempTitleValue
            } else {
                ReadBookConfig.textBold = currentBoldValue
            }
            upUi(ReadBookConfig.textBold)
            onChanged?.invoke()
        }

        fun updateCoarseSelection() {
            val textColor = dialogPrimaryTextColor()
            updateSelection(coarseView ?: return, currentBoldValue, context.accentColor, textColor)
        }
        
        context.alert(titleResource = null) {
            customTitle {
                createTitleBar(
                    context.getString(R.string.text_font_weight_converter),
                    if (currentMode == 0) context.getString(R.string.text_bold_fine_mode) 
                    else context.getString(R.string.text_bold_coarse_mode)
                ) { button ->
                    // 切换模式逻辑
                    if (currentMode == 0) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                            showNotSupportedDialog()
                            return@createTitleBar
                        }
                        if (!AppConfig.textBoldFineTipShown) {
                            showFirstTimeTipDialog {
                                AppConfig.textBoldFineTipShown = true
                                currentMode = 1
                                AppConfig.textBoldMode = 1
                                tempTextValue = coarseToFineWeight(currentBoldValue)
                                applyFontWeight()
                                button.text = context.getString(R.string.text_bold_coarse_mode)
                                coarseView?.visibility = View.GONE
                                fineView?.visibility = View.VISIBLE
                            }
                        } else {
                            currentMode = 1
                            AppConfig.textBoldMode = 1
                            tempTextValue = coarseToFineWeight(currentBoldValue)
                            applyFontWeight()
                            button.text = context.getString(R.string.text_bold_coarse_mode)
                            coarseView?.visibility = View.GONE
                            fineView?.visibility = View.VISIBLE
                        }
                    } else {
                        currentMode = 0
                        AppConfig.textBoldMode = 0
                        currentBoldValue = fineToCoarseWeight(tempTextValue)
                        applyFontWeight()
                        button.text = context.getString(R.string.text_bold_fine_mode)
                        coarseView?.visibility = View.VISIBLE
                        fineView?.visibility = View.GONE
                        updateCoarseSelection()
                    }
                }
            }
            
            customView {
                // 创建包含两种模式的容器
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    
                    // 粗略模式视图
                    coarseView = createCoarseModeView(currentBoldValue) { newValue ->
                        currentBoldValue = newValue
                        applyFontWeight()
                    }.apply {
                        visibility = if (currentMode == 0) View.VISIBLE else View.GONE
                    }
                    
                    // 精细模式视图
                    fineView = createFineModeView(tempTextValue, tempTitleValue) { newTextValue, newTitleValue ->
                        tempTextValue = newTextValue
                        tempTitleValue = newTitleValue
                        applyFontWeight()
                    }.apply {
                        visibility = if (currentMode == 1) View.VISIBLE else View.GONE
                    }
                    
                    addView(coarseView)
                    addView(fineView)
                }
            }
        }
    }

    private fun coarseToFineWeight(value: Int): Int {
        return when (value) {
            1 -> 700
            2 -> 300
            else -> 400
        }
    }

    private fun fineToCoarseWeight(value: Int): Int {
        return when {
            value >= 600 -> 1
            value <= 350 -> 2
            else -> 0
        }
    }

    private fun createTitleBar(
        title: String, 
        buttonText: String, 
        onButtonClick: (TextView) -> Unit
    ): LinearLayout {
        val textColor = dialogPrimaryTextColor()
        val accentColor = context.accentColor
        
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            
            val titleTextView = TextView(context).apply {
                text = title
                textSize = 18f
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val button = TextView(context).apply {
                text = buttonText
                textSize = 14f
                setTextColor(accentColor)
                setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
                setOnClickListener {
                    onButtonClick(this)
                }
            }
            
            addView(titleTextView)
            addView(button)
        }
    }

    private fun createCoarseModeView(
        currentValue: Int,
        onValueChanged: (Int) -> Unit
    ): LinearLayout {
        val textColor = dialogPrimaryTextColor()
        val accentColor = context.accentColor
        
        return LinearLayout(context).apply linearLayout@{
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
            
            val items = context.resources.getStringArray(R.array.text_font_weight)
            items.forEachIndexed { index, text ->
                val itemView = TextView(context).apply {
                    this.text = text
                    textSize = 16f
                    setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                    setTextColor(if (index == currentValue) accentColor else textColor)
                    setOnClickListener {
                        onValueChanged(index)
                        updateSelection(this@linearLayout, index, accentColor, textColor)
                    }
                }
                addView(itemView)
            }
        }
    }

    private fun updateSelection(container: LinearLayout, selectedIndex: Int, accentColor: Int, normalColor: Int) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? TextView ?: continue
            child.setTextColor(if (i == selectedIndex) accentColor else normalColor)
        }
    }

    private fun createFineModeView(
        currentTextValue: Int,
        currentTitleValue: Int,
        onValueChanged: (Int, Int) -> Unit
    ): LinearLayout {
        val textColor = dialogPrimaryTextColor()
        val secondaryTextColor = dialogSecondaryTextColor()
        val accentColor = context.accentColor
        
        var textValue = currentTextValue
        var titleValue = currentTitleValue
        
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 8.dpToPx())
            
            val fontWeightNames = context.resources.getStringArray(R.array.text_font_weight_fine)
            
            // 正文标题
            val contentLabel = TextView(context).apply {
                text = context.getString(R.string.text_bold_content)
                textSize = 14f
                setTextColor(accentColor)
                setPadding(0, 0, 0, 8.dpToPx())
            }
            
            // 正文字重值显示
            val textValueTextView = TextView(context).apply {
                text = textValue.toString()
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(textColor)
            }
            
            // 正文字重名称
            val textWeightNameTextView = TextView(context).apply {
                text = getFontWeightName(textValue, fontWeightNames)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(textColor)
            }
            
            // 正文 SeekBar
            val textSeekBar = SeekBar(context).apply {
                max = 800
                progress = textValue - 100
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        textValue = progress + 100
                        textValueTextView.text = textValue.toString()
                        textWeightNameTextView.text = getFontWeightName(textValue, fontWeightNames)
                        onValueChanged(textValue, titleValue)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            
            // 正文标签容器
            val textLabelsContainer = createLabelsContainer(secondaryTextColor)
            
            // 分隔空间
            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    16.dpToPx()
                )
            }
            
            // 标题标题
            val titleLabel = TextView(context).apply {
                text = context.getString(R.string.text_bold_title)
                textSize = 14f
                setTextColor(accentColor)
                setPadding(0, 0, 0, 8.dpToPx())
            }
            
            // 标题字重值显示
            val titleValueTextView = TextView(context).apply {
                text = titleValue.toString()
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(textColor)
            }
            
            // 标题字重名称
            val titleWeightNameTextView = TextView(context).apply {
                text = getFontWeightName(titleValue, fontWeightNames)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(textColor)
            }
            
            // 标题 SeekBar
            val titleSeekBar = SeekBar(context).apply {
                max = 800
                progress = titleValue - 100
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        titleValue = progress + 100
                        titleValueTextView.text = titleValue.toString()
                        titleWeightNameTextView.text = getFontWeightName(titleValue, fontWeightNames)
                        onValueChanged(textValue, titleValue)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            
            // 标题标签容器
            val titleLabelsContainer = createLabelsContainer(secondaryTextColor)
            
            // 添加所有视图
            addView(contentLabel)
            addView(textValueTextView)
            addView(textWeightNameTextView)
            addView(textSeekBar)
            addView(textLabelsContainer)
            addView(spacer)
            addView(titleLabel)
            addView(titleValueTextView)
            addView(titleWeightNameTextView)
            addView(titleSeekBar)
            addView(titleLabelsContainer)
        }
    }
    
    private fun createLabelsContainer(textColor: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            
            val thinLabel = TextView(context).apply {
                text = context.getString(R.string.text_bold_thin)
                textSize = 12f
                setTextColor(textColor)
            }
            
            val boldLabel = TextView(context).apply {
                text = context.getString(R.string.text_bold_bold)
                textSize = 12f
                setTextColor(textColor)
            }
            
            addView(thinLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(boldLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.END
            })
        }
    }

    private fun dialogPrimaryTextColor(): Int {
        return ContextCompat.getColor(context, R.color.primaryText)
    }

    private fun dialogSecondaryTextColor(): Int {
        return ContextCompat.getColor(context, R.color.secondaryText)
    }

    private fun getFontWeightName(value: Int, names: Array<String>): String {
        return when {
            value <= 150 -> names.getOrElse(0) { "" }
            value <= 250 -> names.getOrElse(1) { "" }
            value <= 350 -> names.getOrElse(2) { "" }
            value <= 450 -> names.getOrElse(3) { "" }
            value <= 550 -> names.getOrElse(4) { "" }
            value <= 650 -> names.getOrElse(5) { "" }
            value <= 750 -> names.getOrElse(6) { "" }
            else -> names.getOrElse(7) { "" }
        }
    }

    private fun showNotSupportedDialog() {
        context.alert(
            titleResource = R.string.text_bold_not_supported_title,
            messageResource = R.string.text_bold_not_supported_message
        ) {
            okButton()
        }
    }

    private fun showFirstTimeTipDialog(onConfirmed: () -> Unit) {
        context.alert(
            titleResource = R.string.text_bold_fine_tip_title,
            messageResource = R.string.text_bold_fine_tip_message
        ) {
            okButton {
                onConfirmed()
            }
        }
    }

    fun onChanged(unit: () -> Unit) {
        onChanged = unit
    }
}
