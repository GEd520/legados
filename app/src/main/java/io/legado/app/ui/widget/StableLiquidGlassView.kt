package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.qmdeve.liquidglass.Config
import com.qmdeve.liquidglass.LiquidGlass
import io.legado.app.utils.dpToPx

class StableLiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var sampleSource: ViewGroup? = null
    private var boundSource: ViewGroup? = null
    private var glass: LiquidGlass? = null
    private var config: Config? = null

    private var cornerRadius = 40f.dpToPx()
    private var refractionHeight = 20f.dpToPx()
    private var refractionOffset = 70f.dpToPx()
    private var tintAlpha = 0f
    private var tintColorRed = 1f
    private var tintColorGreen = 1f
    private var tintColorBlue = 1f
    private var blurRadius = 0.01f
    private var dispersion = 0.5f

    init {
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
    }

    fun bind(source: ViewGroup?) {
        sampleSource = source
        ensureGlass()
    }

    fun setCornerRadius(value: Float) {
        cornerRadius = value.coerceIn(0f, (height.takeIf { it > 0 } ?: Int.MAX_VALUE).toFloat() / 2f)
        applyConfig()
    }

    fun setRefractionHeight(value: Float) {
        refractionHeight = value.coerceIn(12f.dpToPx(), 50f.dpToPx())
        applyConfig()
    }

    fun setRefractionOffset(value: Float) {
        refractionOffset = value.coerceIn(20f.dpToPx(), 120f.dpToPx())
        applyConfig()
    }

    fun setTintAlpha(value: Float) {
        tintAlpha = value
        applyConfig()
    }

    fun setTintColorRed(value: Float) {
        tintColorRed = value
        applyConfig()
    }

    fun setTintColorGreen(value: Float) {
        tintColorGreen = value
        applyConfig()
    }

    fun setTintColorBlue(value: Float) {
        tintColorBlue = value
        applyConfig()
    }

    fun setDispersion(value: Float) {
        dispersion = value.coerceIn(0f, 1f)
        applyConfig()
    }

    fun setBlurRadius(value: Float) {
        blurRadius = value.coerceIn(0.01f, 50f)
        applyConfig()
    }

    fun setDraggableEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = Unit

    fun setElasticEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = Unit

    fun setTouchEffectEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = Unit

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureGlass()
    }

    override fun onDetachedFromWindow() {
        removeGlass()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            applyConfig()
        }
    }

    private fun ensureGlass() {
        val source = sampleSource ?: return
        if (!isAttachedToWindow) return
        if (glass == null) {
            val nextConfig = createConfig()
            val nextGlass = LiquidGlass(context, nextConfig)
            config = nextConfig
            glass = nextGlass
            addView(nextGlass, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        if (boundSource !== source) {
            runCatching {
                glass?.init(source)
                boundSource = source
            }.onFailure {
                rebuildGlass(source)
            }
        }
        applyConfig()
    }

    private fun applyConfig() {
        val source = sampleSource
        if (!isAttachedToWindow || source == null) return
        if (glass == null) {
            ensureGlass()
            return
        }
        runCatching {
            config?.configure(overrides())
            glass?.updateParameters()
            invalidate()
        }.onFailure {
            rebuildGlass(source)
        }
    }

    private fun rebuildGlass(source: ViewGroup) {
        removeGlass()
        sampleSource = source
        post { ensureGlass() }
    }

    private fun removeGlass() {
        glass?.let { runCatching { removeView(it) } }
        glass = null
        config = null
        boundSource = null
    }

    private fun createConfig(): Config {
        return Config().apply {
            configure(overrides())
        }
    }

    private fun overrides(): Config.Overrides {
        val width = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        return Config.Overrides()
            .noFilter()
            .contrast(0f)
            .whitePoint(0f)
            .chromaMultiplier(1f)
            .blurRadius(blurRadius)
            .cornerRadius(cornerRadius)
            .refractionHeight(refractionHeight)
            .refractionOffset(-refractionOffset)
            .tintAlpha(tintAlpha)
            .tintColorRed(tintColorRed)
            .tintColorGreen(tintColorGreen)
            .tintColorBlue(tintColorBlue)
            .dispersion(dispersion)
            .size(width, height)
    }
}
