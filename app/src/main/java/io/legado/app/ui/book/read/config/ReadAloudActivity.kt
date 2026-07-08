package io.legado.app.ui.book.read.config

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.SeekBar
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.graphics.ColorUtils as AndroidXColorUtils
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.databinding.ActivityReadAloudBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.BookCover
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.applyStatusBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ReadAloudActivity : BaseActivity<ActivityReadAloudBinding>(imageBg = false) {

    override val binding by viewBinding(ActivityReadAloudBinding::inflate)
    private val tocActivity = registerForActivityResult(TocActivityResult()) {
        it?.let {
            ReadBook.openChapter(it[0] as Int, it[1] as Int)
            updateBookInfo()
            updatePreviewText()
        }
    }
    private var downY = 0f
    private var downX = 0f
    private var collapseHandled = false
    private var lastCover: String? = null
    private var displayMode = DisplayMode.Immersive
    private var lastOriginalTextKey: String? = null
    private var lastOriginalParagraphIndex = -1
    private val originalParagraphViews = arrayListOf<AppCompatTextView>()

    override fun showReadAloudMiniBar(): Boolean = false

    private enum class DisplayMode {
        Immersive,
        Text
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        applyFallbackTheme()
        initData()
        initEvent()
        updateThemeFromCover()
    }

    override fun onResume() {
        super.onResume()
        updateBookInfo()
        updatePreviewText()
        updatePlayState()
        updateSkipActionState()
    }

    private fun initData() = binding.run {
        readAloudContent.applyStatusBarPadding(withInitialPadding = true)
        readAloudContent.applyNavigationBarPadding(withInitialPadding = true)
        updateBookInfo()
        updatePreviewText()
        seekTimer.max = 180
        seekTimer.progress = if (BaseReadAloudService.timeMinute > 0) BaseReadAloudService.timeMinute else AppConfig.ttsTimer
        updateSpeedText(AppConfig.ttsSpeechRate)
        updateTimerText(BaseReadAloudService.timeMinute)
        updatePlayState()
        updateSkipActionState()
        applyDisplayMode()
    }

    private fun initEvent() = binding.run {
        ivBack.setOnClickListener { finish() }
        llChapterQuick.setOnClickListener { openChapterList() }
        ivChapterQuick.setOnClickListener { openChapterList() }
        llTimerQuick.setOnClickListener { showTimerDialog() }
        ivTimerQuick.setOnClickListener { showTimerDialog() }
        ivMore.setOnClickListener { showDialogFragment<ReadAloudConfigDialog>() }
        ivPlayPause.setOnClickListener {
            if (BaseReadAloudService.pause) ReadAloud.resume(this@ReadAloudActivity)
            else ReadAloud.pause(this@ReadAloudActivity)
        }
        ivPlayPrev.setOnClickListener {
            if (getPrefBoolean("mediaButtonPerNext", false)) {
                ReadAloud.prevChapter(this@ReadAloudActivity)
            } else {
                ReadAloud.prevParagraph(this@ReadAloudActivity)
            }
        }
        ivPlayNext.setOnClickListener {
            if (getPrefBoolean("mediaButtonPerNext", false)) {
                ReadAloud.nextChapter(this@ReadAloudActivity)
            } else {
                ReadAloud.nextParagraph(this@ReadAloudActivity)
            }
        }
        timerBadge.setOnClickListener { showTimerDialog() }
        tvSpeedValue.setOnClickListener { showSpeedDialog() }
        tvModeImmersive.setOnClickListener {
            displayMode = DisplayMode.Immersive
            applyDisplayMode()
        }
        tvModeText.setOnClickListener {
            displayMode = DisplayMode.Text
            applyDisplayMode()
        }
        seekTimer.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateTimerText(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                ReadAloud.setTimer(this@ReadAloudActivity, seekBar.progress)
            }
        })
        readAloudScroll.setOnTouchListener { _, event ->
            if (displayMode == DisplayMode.Text) true else handleCollapseGesture(event)
        }
        originalTextScroll.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }

    private fun handleCollapseGesture(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.rawY
                downX = event.rawX
                collapseHandled = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - downY
                val dx = event.rawX - downX
                if (!collapseHandled && binding.readAloudScroll.scrollY == 0 && dy > 110f.dpToPx() && dy > abs(dx) * 1.3f) {
                    collapseHandled = true
                    finish()
                }
            }
        }
        return false
    }

    private fun applyDisplayMode() = binding.run {
        val immersive = displayMode == DisplayMode.Immersive
        val pageColor = (root.tag as? Int) ?: 0xFF6F5B86.toInt()
        val accent = AndroidXColorUtils.blendARGB(pageColor, 0xFFFFFFFF.toInt(), 0.72f)
        topSpacer.visibility = View.VISIBLE
        heroCard.visibility = if (immersive) View.VISIBLE else View.GONE
        tvPreview.visibility = if (immersive) View.VISIBLE else View.GONE
        fillSpacer.visibility = View.VISIBLE
        originalTextScroll.visibility = if (immersive) View.GONE else View.VISIBLE
        readAloudScroll.isNestedScrollingEnabled = immersive
        (paragraphProgressRow.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.topMargin = 8f.dpToPx().toInt()
            paragraphProgressRow.layoutParams = it
        }
        tvPreview.gravity = if (immersive) Gravity.CENTER else Gravity.CENTER_VERTICAL
        tvPreview.textSize = if (immersive) 22f else 20f
        tvPreview.maxLines = if (immersive) 4 else 9
        tvPreview.layoutParams = tvPreview.layoutParams.apply {
            height = (if (immersive) 128f else 246f).dpToPx().toInt()
        }
        tvModeImmersive.background = createRoundDrawable(
            if (immersive) accent else 0x00FFFFFF,
            14f
        )
        tvModeText.background = createRoundDrawable(
            if (immersive) 0x00FFFFFF else accent,
            14f
        )
        applyModeTextColor()
        updateParagraphProgress()
    }

    private fun applyModeTextColor() {
        binding.run {
            val pageColor = (root.tag as? Int) ?: return
            val textColor = 0xFFF7F1FF.toInt()
            val selectedText = AndroidXColorUtils.blendARGB(pageColor, 0xFF120D18.toInt(), 0.52f)
            val unselectedText = AndroidXColorUtils.setAlphaComponent(textColor, 188)
            tvModeImmersive.setTextColor(if (displayMode == DisplayMode.Immersive) selectedText else unselectedText)
            tvModeText.setTextColor(if (displayMode == DisplayMode.Text) selectedText else unselectedText)
            tvModeImmersive.typeface = if (displayMode == DisplayMode.Immersive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tvModeText.typeface = if (displayMode == DisplayMode.Text) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun openChapterList() {
        ReadBook.book?.let { tocActivity.launch(it.bookUrl) }
    }

    private fun updateBookInfo() = binding.run {
        val activeBookName = BaseReadAloudService.activeBookName
        val activeChapterTitle = BaseReadAloudService.activeChapterTitle
        val activeCover = BaseReadAloudService.activeBookCover
        if (lastCover != activeCover) {
            lastCover = activeCover
            if (activeCover != null) {
                ivCover.load(activeCover, activeBookName, BaseReadAloudService.activeBookAuthor, false)
            } else {
                ReadBook.book?.let { ivCover.load(it, false) }
            }
        }
        tvBookName.text = activeBookName ?: ReadBook.book?.name ?: ""
        tvChapterName.text = activeChapterTitle ?: ReadBook.book?.durChapterTitle ?: ""
    }

    private fun updatePreviewText() = binding.run {
        val paragraph = BaseReadAloudService.activePreviewText
            ?: ReadBook.curTextChapter?.paragraphs?.firstOrNull {
                ReadBook.durChapterPos in it.chapterIndices
            }?.text?.replace("\n", "")?.trim()
        tvPreview.text = paragraph?.takeIf { it.isNotEmpty() }
            ?: (BaseReadAloudService.activeChapterTitle ?: ReadBook.book?.durChapterTitle ?: "")
        updateParagraphProgress()
    }

    private fun updateParagraphProgress() = binding.run {
        val textChapter = ReadBook.curTextChapter
        val progress = if (BaseReadAloudService.lastTtsProgress > 0) {
            BaseReadAloudService.lastTtsProgress
        } else {
            ReadBook.durChapterPos
        }
        val pageSplit = getPrefBoolean("readAloudByPage")
        val paragraphs = textChapter?.getParagraphs(pageSplit)
            .orEmpty()
        val originalTexts = textChapter?.getNeedReadAloud(0, pageSplit, 0)
            ?.split("\n")
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val paragraphCount = paragraphs.size
        val paragraphIndex = if (paragraphCount > 0) {
            textChapter?.getParagraphNum(progress.coerceAtLeast(1), pageSplit)
                ?.coerceIn(1, paragraphCount) ?: 0
        } else {
            0
        }
        tvParagraphProgress.text = "$paragraphIndex/$paragraphCount"
        progressParagraph.progress = if (paragraphCount > 0 && paragraphIndex > 0) {
            (paragraphIndex * progressParagraph.max / paragraphCount).coerceIn(0, progressParagraph.max)
        } else {
            0
        }
        updateOriginalTextList(paragraphs, originalTexts, paragraphIndex)
    }

    private fun updateOriginalTextList(
        paragraphs: List<io.legado.app.ui.book.read.page.entities.TextParagraph>,
        originalTexts: List<String>,
        paragraphIndex: Int
    ) = binding.run {
        if (displayMode != DisplayMode.Text) return@run
        val pageSplit = getPrefBoolean("readAloudByPage")
        val textKey = buildString {
            append(ReadBook.durChapterIndex)
            append(':')
            append(pageSplit)
            append(':')
            append(paragraphs.size)
            append(':')
            append(originalTexts.size)
            append(':')
            append(paragraphs.firstOrNull()?.chapterPosition ?: -1)
            append(':')
            append(paragraphs.lastOrNull()?.chapterPosition ?: -1)
        }
        if (lastOriginalTextKey != textKey) {
            lastOriginalTextKey = textKey
            lastOriginalParagraphIndex = -1
            originalParagraphViews.clear()
            llOriginalText.removeAllViews()
            paragraphs.forEachIndexed { index, paragraph ->
                val paragraphView = AppCompatTextView(this@ReadAloudActivity).apply {
                    text = cleanOriginalParagraphText(originalTexts.getOrNull(index) ?: paragraph.text)
                    gravity = Gravity.CENTER
                    includeFontPadding = true
                    setLineSpacing(2f.dpToPx(), 1f)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setOnClickListener {
                        ReadBook.openChapter(ReadBook.durChapterIndex, paragraph.chapterPosition)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = if (index == 0) 0 else 14.dpToPx()
                    }
                }
                originalParagraphViews.add(paragraphView)
                llOriginalText.addView(paragraphView)
            }
        }
        applyOriginalTextHighlight(paragraphIndex)
    }

    private fun applyOriginalTextHighlight(paragraphIndex: Int) = binding.run {
        if (originalParagraphViews.isEmpty()) return@run
        val activeIndex = (paragraphIndex - 1).coerceIn(0, originalParagraphViews.lastIndex)
        if (lastOriginalParagraphIndex == activeIndex) return@run
        val textColor = 0xFFF7F1FF.toInt()
        originalParagraphViews.forEachIndexed { index, view ->
            val current = index == activeIndex
            view.setTextColor(AndroidXColorUtils.setAlphaComponent(textColor, if (current) 240 else 116))
            view.textSize = if (current) 20f else 18f
            view.typeface = if (current) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            view.maxLines = if (current) Int.MAX_VALUE else 2
            view.ellipsize = if (current) null else android.text.TextUtils.TruncateAt.END
            view.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }
        lastOriginalParagraphIndex = activeIndex
        originalTextScroll.post {
            val target = originalParagraphViews.getOrNull(activeIndex) ?: return@post
            val targetScrollY = target.top - (originalTextScroll.height / 2) + (target.height / 2)
            originalTextScroll.smoothScrollTo(0, targetScrollY.coerceAtLeast(0))
        }
    }

    private fun cleanOriginalParagraphText(text: String): String {
        return text
            .replace("\uFFFC", "")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\uFEFF", "")
            .replace("\n", "")
            .trim()
    }

    private fun adjustSpeed(delta: Int) {
        AppConfig.ttsSpeechRate = (AppConfig.ttsSpeechRate + delta).coerceIn(0, 45)
        updateSpeedText(AppConfig.ttsSpeechRate)
        val shouldResume = BaseReadAloudService.isRun && !BaseReadAloudService.pause
        if (shouldResume) ReadAloud.pause(this)
        ReadAloud.upTtsSpeechRate(this)
        if (shouldResume) ReadAloud.resume(this)
    }

    private fun applySpeed(value: Int) {
        AppConfig.ttsSpeechRate = value.coerceIn(0, 45)
        updateSpeedText(AppConfig.ttsSpeechRate)
        val shouldResume = BaseReadAloudService.isRun && !BaseReadAloudService.pause
        if (shouldResume) ReadAloud.pause(this)
        ReadAloud.upTtsSpeechRate(this)
        if (shouldResume) ReadAloud.resume(this)
    }

    @SuppressLint("SetTextI18n")
    private fun showSpeedDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22f.dpToPx().toInt(), 18f.dpToPx().toInt(), 22f.dpToPx().toInt(), 10f.dpToPx().toInt())
        }
        val followSys = AppCompatCheckBox(this).apply {
            text = getString(R.string.flow_sys)
            isChecked = AppConfig.ttsFlowSys
        }
        val valueText = AppCompatTextView(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            textSize = 18f
            text = "${((if (AppConfig.ttsFlowSys) AppConfig.defaultSpeechRate else AppConfig.ttsSpeechRate) + 5) / 10f}x"
            setPadding(0, 0, 0, 12f.dpToPx().toInt())
        }
        val seekBar = AppCompatSeekBar(this@ReadAloudActivity).apply {
            max = 45
            progress = if (AppConfig.ttsFlowSys) AppConfig.defaultSpeechRate else AppConfig.ttsSpeechRate
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnSeekBarChangeListener(object : SeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    valueText.text = "${(progress + 5) / 10f}x"
                }
            })
        }
        followSys.setOnCheckedChangeListener { _, isChecked ->
            AppConfig.ttsFlowSys = isChecked
            seekBar.isEnabled = !isChecked
            if (isChecked) {
                seekBar.progress = AppConfig.defaultSpeechRate
                valueText.text = "${(AppConfig.defaultSpeechRate + 5) / 10f}x"
            }
            val shouldResume = BaseReadAloudService.isRun && !BaseReadAloudService.pause
            if (shouldResume) ReadAloud.pause(this)
            ReadAloud.upTtsSpeechRate(this)
            if (shouldResume) ReadAloud.resume(this)
        }
        seekBar.isEnabled = !AppConfig.ttsFlowSys
        container.addView(followSys)
        container.addView(valueText)
        container.addView(seekBar)
        AlertDialog.Builder(this)
            .setTitle(R.string.read_aloud_speed)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                AppConfig.ttsFlowSys = followSys.isChecked
                applySpeed(seekBar.progress)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTimerDialog() {
        val times = intArrayOf(0, 5, 10, 15, 30, 60, 90, 180)
        selector(getString(R.string.set_timer), times.map { "$it min" }) { _, index ->
            ReadAloud.setTimer(this@ReadAloudActivity, times[index])
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateSpeedText(value: Int) {
        binding.tvSpeedValue.text = "${(value + 5) / 10f}x"
    }

    private fun updateTimerText(value: Int) {
        binding.tvTimer.text = getString(R.string.timer_m, if (value < 0) 0 else value)
    }

    private fun updatePlayState() {
        if (BaseReadAloudService.pause) {
            binding.ivPlayPause.setImageResource(R.drawable.ic_play_24dp)
            binding.ivPlayPause.background = null
            binding.ivPlayPause.setColorFilter(0xFFFFFFFF.toInt())
        } else {
            binding.ivPlayPause.setImageResource(R.drawable.ic_pause_24dp)
            binding.ivPlayPause.background = null
            binding.ivPlayPause.setColorFilter(0xFFFFFFFF.toInt())
        }
    }

    private fun updateSkipActionState() {
        val useChapter = getPrefBoolean("mediaButtonPerNext", false)
        binding.ivPlayPrev.contentDescription = getString(
            if (useChapter) R.string.previous_chapter else R.string.read_aloud_prev_paragraph
        )
        binding.ivPlayNext.contentDescription = getString(
            if (useChapter) R.string.next_chapter else R.string.read_aloud_next_paragraph
        )
    }

    override fun observeLiveBus() {
        observeEvent<Int>(EventBus.ALOUD_STATE) {
            updatePlayState()
            updateBookInfo()
            updatePreviewText()
            if (it == Status.STOP) finish()
        }
        observeEvent<Int>(EventBus.READ_ALOUD_DS) {
            binding.seekTimer.progress = it
            updateTimerText(it)
        }
        observeEvent<Int>(EventBus.TTS_PROGRESS) {
            if (BaseReadAloudService.shouldIgnoreProgressSync(
                    ReadBook.durChapterIndex,
                    BaseReadAloudService.lastTtsChapterIndex
                )
            ) {
                return@observeEvent
            }
            updateBookInfo()
            updatePreviewText()
        }
    }

    private fun applyFallbackTheme() {
        applyTheme(
            baseColor = 0xFF665185.toInt(),
            textColor = 0xFFF7F1FF.toInt()
        )
    }

    private fun updateThemeFromCover() {
        val cover = BaseReadAloudService.activeBookCover
            ?: ReadBook.book?.let { BookCover.getDisplayCover(it) }
            ?: return
        updateBlurBackground()
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = runCatching {
                ImageLoader.loadBitmap(this@ReadAloudActivity, cover).submit().get()
            }.getOrNull()
            bitmap?.let {
                val dominant = extractDominantColor(it)
                withContext(Dispatchers.Main) {
                    applyTheme(dominant, 0xFFF7F1FF.toInt())
                    updatePlayState()
                }
            }
        }
    }

    private fun updateBlurBackground() {
        val activeCover = BaseReadAloudService.activeBookCover
        if (activeCover != null) {
            BookCover.loadBlur(this, activeCover, false, null)
                .into(binding.ivBlurBackground)
            return
        }
        ReadBook.book?.let { book ->
            BookCover.loadBlur(this, BookCover.getDisplayCover(book), sourceOrigin = book.origin)
                .into(binding.ivBlurBackground)
        }
    }

    private fun applyTheme(baseColor: Int, textColor: Int) = binding.run {
        val pageColor = AndroidXColorUtils.blendARGB(baseColor, 0xFF22172D.toInt(), 0.32f)
        val secondary = AndroidXColorUtils.setAlphaComponent(textColor, 190)
        root.setBackgroundColor(0xFF000000.toInt())
        root.tag = pageColor
        vBackgroundMask.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                AndroidXColorUtils.setAlphaComponent(pageColor, 182),
                AndroidXColorUtils.setAlphaComponent(0xFF140E1C.toInt(), 220)
            )
        )
        heroCard.radius = 12f.dpToPx()
        heroCard.setCardBackgroundColor(AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 20))
        coverMask.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                AndroidXColorUtils.setAlphaComponent(0xFF1A1224.toInt(), 0),
                AndroidXColorUtils.setAlphaComponent(0xFF1A1224.toInt(), 88),
                AndroidXColorUtils.setAlphaComponent(0xFF1A1224.toInt(), 228)
            )
        ).apply {
            cornerRadius = 12f.dpToPx()
        }
        timerBadge.background = createRoundDrawable(AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 22), 18f)
        tvSpeedValue.background = createRoundDrawable(AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 18), 18f)
        ivPlayPrev.background = null
        ivPlayNext.background = null
        ivChapterQuick.background = null
        ivTimerQuick.background = null

        listOf(tvPageTitle, tvChapterName, tvPreview, tvTimer, tvSpeedValue, tvTimerLabelLeft, tvTimerLabelRight, tvParagraphProgress).forEach { it.setTextColor(textColor) }
        listOf(tvBookName, tvChapterQuick, tvTimerQuick).forEach {
            it.setTextColor(secondary)
        }
        ivBack.setColorFilter(textColor)
        ivMore.setColorFilter(textColor)
        ivTimer.setColorFilter(secondary)
        ivChapterQuick.setColorFilter(secondary)
        ivTimerQuick.setColorFilter(secondary)
        ivPlayPrev.setColorFilter(textColor)
        ivPlayNext.setColorFilter(textColor)
        modeSwitch.background = createRoundDrawable(AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 33), 18f)
        applyModeTextColor()
    }

    private fun extractDominantColor(bitmap: Bitmap): Int {
        val stepX = (bitmap.width / 18).coerceAtLeast(1)
        val stepY = (bitmap.height / 18).coerceAtLeast(1)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                red += android.graphics.Color.red(pixel)
                green += android.graphics.Color.green(pixel)
                blue += android.graphics.Color.blue(pixel)
                count++
            }
        }
        if (count == 0L) return 0xFF665185.toInt()
        return android.graphics.Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    private fun createRoundDrawable(fillColor: Int, radiusDp: Float, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp.dpToPx()
            setColor(fillColor)
            strokeColor?.let { setStroke(1.dpToPx(), it) }
        }
    }
}
