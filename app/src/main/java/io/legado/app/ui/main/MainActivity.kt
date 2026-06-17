@file:Suppress("DEPRECATION")

package io.legado.app.ui.main

import android.os.Bundle
import android.os.Build
import android.text.format.DateUtils
import android.graphics.Outline
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.get
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ActivityMainBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.elevation
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.about.CrashLogsDialog
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.style1.BookshelfFragment1
import io.legado.app.ui.main.bookshelf.style2.BookshelfFragment2
import io.legado.app.ui.main.explore.ExploreFragment
import io.legado.app.ui.main.my.MyFragment
import io.legado.app.ui.main.rss.RssFragment
import io.legado.app.ui.widget.StableLiquidGlassView
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.text.BadgeView
import io.legado.app.utils.isCreated
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.invisible
import io.legado.app.utils.visible
import io.legado.app.utils.startActivity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding
import kotlin.coroutines.resume
import androidx.core.graphics.drawable.toDrawable
import io.legado.app.help.update.AppUpdate
import io.legado.app.ui.about.UpdateDialog
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getPrefInt
import kotlin.time.Duration.Companion.hours

/**
 * 主界面
 */
@Suppress("PrivatePropertyName")
class MainActivity : VMBaseActivity<ActivityMainBinding, MainViewModel>(),
    BottomNavigationView.OnNavigationItemSelectedListener,
    BottomNavigationView.OnNavigationItemReselectedListener,
    MainViewModel.CallBack {

    override val binding by viewBinding(ActivityMainBinding::inflate)
    override val viewModel by viewModels<MainViewModel>()
    private val idBookshelf = 0
    private val idBookshelf1 = 11
    private val idBookshelf2 = 12
    private val idExplore = 1
    private val idRss = 2
    private val idMy = 3
    private var exitTime: Long = 0
    private var bookshelfReselected: Long = 0
    private var exploreReselected: Long = 0
    private var pagePosition = 0
    private val fragmentMap = hashMapOf<Int, Fragment>()
    private var bottomMenuCount = 4
    private var bottomNavigationConfigSignature: String? = null
    private var bottomNavigationInset = 0
    private val EXIT_INTERVAL = 2000L
    private val realPositions = arrayOf(idBookshelf, idExplore, idRss, idMy)
    private val adapter by lazy {
        TabFragmentPageAdapter(supportFragmentManager)
    }
    private var onUpBooksBadgeView: BadgeView? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        upBottomMenu()
        initView()
        upHomePage()
        onBackPressedDispatcher.addCallback(this) {
            if (pagePosition != 0) {
                binding.viewPagerMain.currentItem = 0
                return@addCallback
            }
            (fragmentMap[getFragmentId(0)] as? BookshelfFragment2)?.let {
                if (it.back()) {
                    return@addCallback
                }
            }
            if (System.currentTimeMillis() - exitTime > EXIT_INTERVAL) {
                toastOnUi(R.string.double_click_exit)
                exitTime = System.currentTimeMillis()
            } else {
                if (BaseReadAloudService.pause) {
                    finish()
                } else {
                    moveTaskToBack(true)
                }
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        lifecycleScope.launch {
            //隐私协议
            if (!privacyPolicy()) return@launch
            //版本更新
            upVersion()
            //设置本地密码
            setLocalPassword()
            notifyAppCrash()
            //备份同步
            backupSync()
            //设置回调
            viewModel.setActivityCallback(this@MainActivity)
            //自动更新书源
            binding.viewPagerMain.postDelayed(1000) {
                viewModel.ruleSubsUp()
            }
            //自动更新书籍
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook") ?: false
            if (AppConfig.autoRefreshBook && !isAutoRefreshedBook) {
                //每次进入书架后5秒自动更新书籍目录
                binding.viewPagerMain.postDelayed(5000) {
                    viewModel.upAllBookToc()
                }
            }
            binding.viewPagerMain.postDelayed(3000) {
                viewModel.postLoad()
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean = binding.run {
        when (item.itemId) {
            R.id.menu_bookshelf ->
                viewPagerMain.setCurrentItem(0, false)

            R.id.menu_discovery ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)

            R.id.menu_rss ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idRss), false)

            R.id.menu_my_config ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        refreshBottomNavigationConfig()
    }

    override fun onNavigationItemReselected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_bookshelf -> {
                if (System.currentTimeMillis() - bookshelfReselected > 300) {
                    bookshelfReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.gotoTop()
                }
            }

            R.id.menu_discovery -> {
                if (System.currentTimeMillis() - exploreReselected > 300) {
                    exploreReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[1] as? ExploreFragment)?.compressExplore()
                }
            }
        }
    }

    private fun initView() = binding.run {
        viewPagerMain.setEdgeEffectColor(primaryColor)
        viewPagerMain.offscreenPageLimit = 3
        viewPagerMain.adapter = adapter
        viewPagerMain.addOnPageChangeListener(PageChangeCallback())
        bottomNavigationView.setOnNavigationItemSelectedListener(this@MainActivity)
        bottomNavigationView.setOnNavigationItemReselectedListener(this@MainActivity)
        refreshBottomNavigationConfig(force = true)
        if (AppConfig.isEInkMode) {
            bottomNavigationView.setBackgroundResource(R.drawable.bg_eink_border_top)
        }
        bottomNavigationGlass.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            bottomNavigationInset = windowInsets.navigationBarHeight
            view.bottomPadding = 0
            refreshBottomNavigationConfig(force = true)
            windowInsets
        }
    }

    private fun refreshBottomNavigationConfig(force: Boolean = false) {
        val signature = NavigationBarConfig.currentSignature(this, AppConfig.isNightTheme)
        if (!force && bottomNavigationConfigSignature == signature) {
            return
        }
        bottomNavigationConfigSignature = signature
        ThemeConfig.applyTheme(this)
        applyNavigationBarPackage()
    }

    fun mainContentBottomPadding(): Int {
        val bottomNav = binding.bottomNavigationGlass
        val layoutParams = bottomNav.layoutParams as? FrameLayout.LayoutParams
        val navHeight = bottomNav.height.takeIf { it > 0 } ?: bottomNav.minimumHeight
        val bottomMargin = layoutParams?.bottomMargin ?: 0
        return navHeight + bottomMargin
    }

    private fun refreshMainContentBottomPadding() {
        val bottomPadding = mainContentBottomPadding()
        fragmentMap.values.forEach { fragment ->
            if (fragment.view != null) {
                (fragment as? MainFragmentInterface)?.updateMainBottomPadding(bottomPadding)
            }
        }
    }

    private fun applyNavigationBarPackage() = binding.run {
        val config = NavigationBarConfig.activeConfig(this@MainActivity, AppConfig.isNightTheme)
        val bgColor = resolveNavigationBarBackground(config)
        val hasCustomIcons = NavigationBarConfig.applyToMenu(
            bottomNavigationView.menu,
            this@MainActivity,
            AppConfig.isNightTheme
        )
        if (hasCustomIcons) {
            bottomNavigationView.itemIconTintList = null
        } else {
            bottomNavigationView.restoreThemeIconTint()
        }
        bottomNavigationView.itemBackground = Color.TRANSPARENT.toDrawable()
        applyBottomNavigationShell(config, bgColor)
        val textIsDark = ColorUtils.isColorLight(bgColor)
        bottomNavigationView.itemTextColor = io.legado.app.lib.theme.Selector.colorBuild()
            .setDefaultColor(getSecondaryTextColor(textIsDark))
            .setSelectedColor(accentColor)
            .create()
        bottomNavigationView.post {
            applyBottomNavigationSelectedIndicator(config, bgColor)
            refreshMainContentBottomPadding()
        }
    }

    private fun resolveNavigationBarBackground(config: NavigationBarConfig): Int {
        if (config.isBuiltin) {
            return bottomBackground
        }
        val baseColor = if (AppConfig.isNightTheme) {
            getPrefInt(PreferKey.cNBBackground, getCompatColor(R.color.default_night_bottom_background))
        } else {
            getPrefInt(PreferKey.cBBackground, getCompatColor(R.color.default_bottom_background))
        }
        return NavigationBarConfig.resolveBottomColor(baseColor, config)
    }

    private fun applyBottomNavigationShell(config: NavigationBarConfig, bgColor: Int) = binding.run {
        val floating = config.layoutMode == NavigationBarConfig.LAYOUT_FLOATING
        val standard = config.layoutMode == NavigationBarConfig.LAYOUT_STANDARD
        val horizontalMargin = if (floating) 20.dpToPx() else 0
        val topMargin = 0
        val bottomMargin = if (floating) 10.dpToPx() + bottomNavigationInset else 0
        val shellHeight = if (floating) 48.dpToPx() else 50.dpToPx() + if (standard) bottomNavigationInset else 0
        bottomNavigationGlass.layoutParams = (bottomNavigationGlass.layoutParams as FrameLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = shellHeight
            gravity = Gravity.BOTTOM
            setMargins(horizontalMargin, topMargin, horizontalMargin, bottomMargin)
        }
        bottomNavigationView.layoutParams = (bottomNavigationView.layoutParams as FrameLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
            setMargins(0, 0, 0, 0)
        }
        bottomNavigationGlass.minimumHeight = shellHeight
        bottomNavigationView.minimumHeight = if (floating) 48.dpToPx() else 50.dpToPx()
        bottomNavigationView.itemIconSize = if (floating) 23.dpToPx() else 22.dpToPx()
        bottomNavigationView.setPadding(
            if (floating) 6.dpToPx() else 0,
            0,
            if (floating) 6.dpToPx() else 0,
            if (standard) bottomNavigationInset else 0
        )
        bottomNavigationView.alpha = 1f
        bottomNavigationView.elevation = 0f
        bottomNavigationGlass.elevation = if (floating) {
            when (config.effectMode) {
                NavigationBarConfig.EFFECT_SOLID -> 8.dpToPx().toFloat()
                NavigationBarConfig.EFFECT_FROSTED -> 14.dpToPx().toFloat()
                else -> 12.dpToPx().toFloat()
            }
        } else {
            0f
        }
        bottomNavigationView.setBackgroundColor(Color.TRANSPARENT)
        bottomNavigationView.background = Color.TRANSPARENT.toDrawable()
        val liquid = !standard && config.effectMode != NavigationBarConfig.EFFECT_SOLID
        applyBottomNavigationGlassOutline(bottomNavigationGlass, if (floating) 24f.dpToPx() else 0f)
        if (liquid) {
            bottomNavigationGlassView.visible()
            setupBottomLiquidGlass(bottomNavigationGlassView, config, if (floating) 24f.dpToPx() else 0f)
            bottomNavigationShellOverlay.background = createLiquidGlassShellDrawable(
                glassLevel = config.opacity.coerceIn(0, 100) / 100f,
                cornerRadius = if (floating) 24f.dpToPx() else 0f,
                effectMode = config.effectMode
            )
        } else {
            bottomNavigationGlassView.invisible()
            bottomNavigationShellOverlay.background = createBottomNavigationShellDrawable(config, bgColor)
        }
    }

    private fun applyBottomNavigationGlassOutline(view: View, cornerRadius: Float) {
        view.clipToOutline = cornerRadius > 0f
        view.outlineProvider = if (cornerRadius > 0f) {
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
        } else {
            ViewOutlineProvider.BOUNDS
        }
    }

    private fun setupBottomLiquidGlass(
        liquidGlassView: StableLiquidGlassView,
        config: NavigationBarConfig,
        cornerRadius: Float
    ) {
        val level = config.opacity.coerceIn(0, 100) / 100f
        val frosted = config.effectMode == NavigationBarConfig.EFFECT_FROSTED
        liquidGlassView.bind(binding.contentContainer)
        liquidGlassView.setCornerRadius(cornerRadius)
        liquidGlassView.setRefractionHeight(if (frosted) 10f.dpToPx() else (14f + level * 10f).dpToPx())
        liquidGlassView.setRefractionOffset(if (frosted) 30f.dpToPx() else (42f + level * 18f).dpToPx())
        liquidGlassView.setBlurRadius(if (frosted) 22f + level * 20f else 8f + level * 14f)
        liquidGlassView.setDispersion(if (frosted) 0.06f else 0.24f + level * 0.24f)
        liquidGlassView.setTintAlpha(if (frosted) 0.08f + level * 0.12f else 0.025f + level * 0.045f)
        liquidGlassView.setTintColorRed(1f)
        liquidGlassView.setTintColorGreen(1f)
        liquidGlassView.setTintColorBlue(1f)
        liquidGlassView.setDraggableEnabled(false)
        liquidGlassView.setElasticEnabled(false)
        liquidGlassView.setTouchEffectEnabled(false)
        liquidGlassView.invalidate()
    }

    private fun createLiquidGlassShellDrawable(
        glassLevel: Float,
        cornerRadius: Float,
        effectMode: String
    ): GradientDrawable {
        val baseColor = bottomBackground
        val isLight = ColorUtils.isColorLight(baseColor)
        val surfaceColor = if (isLight) Color.WHITE else Color.rgb(22, 24, 28)
        val fallbackBoost = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) 0.08f else 0f
        val frosted = effectMode == NavigationBarConfig.EFFECT_FROSTED
        val startAlpha = if (frosted) {
            (0.26f + glassLevel * 0.24f + fallbackBoost).coerceIn(0f, 0.62f)
        } else {
            (0.10f + glassLevel * 0.10f + fallbackBoost * 0.55f).coerceIn(0f, 0.28f)
        }
        val centerAlpha = if (frosted) {
            (0.20f + glassLevel * 0.18f + fallbackBoost * 0.65f).coerceIn(0f, 0.48f)
        } else {
            (0.05f + glassLevel * 0.07f + fallbackBoost * 0.35f).coerceIn(0f, 0.20f)
        }
        val endAlpha = if (frosted) {
            (0.16f + glassLevel * 0.15f + fallbackBoost * 0.45f).coerceIn(0f, 0.40f)
        } else {
            (0.04f + glassLevel * 0.06f + fallbackBoost * 0.30f).coerceIn(0f, 0.16f)
        }
        val strokeAlpha = if (frosted) {
            (0.20f + glassLevel * 0.16f).coerceIn(0f, 0.44f)
        } else {
            (0.22f + glassLevel * 0.18f).coerceIn(0f, 0.46f)
        }
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.withAlpha(surfaceColor, startAlpha),
                ColorUtils.withAlpha(surfaceColor, centerAlpha),
                ColorUtils.withAlpha(surfaceColor, endAlpha)
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            setCornerRadius(cornerRadius)
            setStroke(1.dpToPx(), ColorUtils.withAlpha(surfaceColor, strokeAlpha))
        }
    }

    private fun createBottomNavigationShellDrawable(config: NavigationBarConfig, bgColor: Int): Drawable {
        val standard = config.layoutMode == NavigationBarConfig.LAYOUT_STANDARD
        val radius = when {
            standard -> 0f
            else -> 24f.dpToPx()
        }
        val strokeColor = config.borderColor?.let {
            ColorUtils.withAlpha(it, config.borderAlpha.coerceIn(0, 100) / 100f)
        }
        if (!standard && config.effectMode != NavigationBarConfig.EFFECT_SOLID) {
            return createBottomNavigationGlassDrawable(config, bgColor, radius, strokeColor)
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(bgColor)
            setStroke(
                if (!standard && strokeColor != null) 1.dpToPx() else 0,
                strokeColor ?: Color.TRANSPARENT
            )
        }
    }

    private fun createBottomNavigationGlassDrawable(
        config: NavigationBarConfig,
        bgColor: Int,
        radius: Float,
        strokeColor: Int?
    ): Drawable {
        val opacityFactor = config.opacity.coerceIn(0, 100) / 100f
        val glassBase = glassBaseColor(bgColor, config.effectMode, opacityFactor)
        val body = roundedGradient(
            radius = radius,
            colors = bottomNavigationMaterialColors(glassBase, config.effectMode)
        )
        val mist = roundedGradient(
            radius = radius,
            colors = intArrayOf(
                Color.TRANSPARENT,
                adjustAlpha(
                    if (ColorUtils.isColorLight(glassBase)) Color.WHITE else Color.rgb(90, 110, 136),
                    opacityFactor * if (config.effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.34f else 0.08f
                ),
                Color.TRANSPARENT
            )
        )
        val highlight = roundedGradient(
            radius = radius,
            colors = intArrayOf(
                adjustAlpha(
                    getCompatColor(R.color.glass_bar_highlight),
                    opacityFactor * if (config.effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.36f else 1.00f
                ),
                adjustAlpha(Color.WHITE, opacityFactor * if (config.effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.06f else 0.20f),
                Color.TRANSPARENT,
                adjustAlpha(
                    getCompatColor(R.color.glass_overlay),
                    opacityFactor * if (config.effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.18f else 0.72f
                )
            )
        )
        val bottomShade = roundedGradient(
            radius = radius,
            colors = intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                adjustAlpha(
                    if (ColorUtils.isColorLight(glassBase)) Color.rgb(20, 34, 54) else Color.BLACK,
                    opacityFactor * if (config.effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.06f else 0.18f
                )
            )
        )
        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke(
                1.dpToPx(),
                adjustAlpha(strokeColor ?: getCompatColor(R.color.glass_stroke), opacityFactor)
            )
        }
        val shadow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(adjustAlpha(getCompatColor(R.color.glass_bar_shadow), opacityFactor))
        }
        return LayerDrawable(arrayOf(shadow, body, mist, highlight, bottomShade, border)).apply {
            val shadowInset = 2.dpToPx()
            setLayerInset(0, shadowInset, 2.dpToPx(), shadowInset, 0)
            setLayerInset(1, 0, 0, 0, 1.dpToPx())
            setLayerInset(2, 2.dpToPx(), 1.dpToPx(), 2.dpToPx(), 3.dpToPx())
            setLayerInset(3, 1.dpToPx(), 1.dpToPx(), 1.dpToPx(), 2.dpToPx())
            setLayerInset(4, 1.dpToPx(), 2.dpToPx(), 1.dpToPx(), 1.dpToPx())
            setLayerInset(5, 0, 0, 0, 1.dpToPx())
        }
    }

    private fun glassBaseColor(bgColor: Int, effectMode: String, opacityFactor: Float): Int {
        val baseRgb = Color.rgb(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
        val light = ColorUtils.isColorLight(baseRgb)
        val materialTint = when (effectMode) {
            NavigationBarConfig.EFFECT_FROSTED -> if (light) Color.WHITE else Color.rgb(52, 58, 68)
            else -> getCompatColor(R.color.glass_bar)
        }
        val ratio = if (effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.62f else 0.42f
        val alpha = opacityFactor * if (effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.90f else 0.52f
        val rgb = ColorUtils.blendColors(baseRgb, materialTint, ratio)
        return ColorUtils.withAlpha(rgb, alpha.coerceIn(0f, 1f))
    }

    private fun roundedGradient(radius: Float, colors: IntArray): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        return ColorUtils.withAlpha(
            Color.rgb(Color.red(color), Color.green(color), Color.blue(color)),
            (Color.alpha(color) / 255f * factor).coerceIn(0f, 1f)
        )
    }

    private fun bottomNavigationMaterialColors(bgColor: Int, effectMode: String): IntArray {
        val alpha = Color.alpha(bgColor) / 255f
        val rgb = Color.rgb(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
        return if (effectMode == NavigationBarConfig.EFFECT_FROSTED) {
            val frost = if (ColorUtils.isColorLight(rgb)) Color.WHITE else Color.rgb(62, 70, 82)
            intArrayOf(
                ColorUtils.blendColors(
                    ColorUtils.withAlpha(rgb, (alpha * 0.98f).coerceIn(0f, 1f)),
                    ColorUtils.withAlpha(frost, (alpha * 0.38f).coerceIn(0f, 1f)),
                    0.48f
                ),
                ColorUtils.blendColors(
                    ColorUtils.withAlpha(rgb, (alpha * 0.94f).coerceIn(0f, 1f)),
                    ColorUtils.withAlpha(frost, (alpha * 0.25f).coerceIn(0f, 1f)),
                    0.36f
                ),
                ColorUtils.withAlpha(rgb, (alpha * 0.86f).coerceIn(0f, 1f))
            )
        } else {
            val highlight = if (ColorUtils.isColorLight(rgb)) Color.WHITE else Color.rgb(56, 74, 96)
            intArrayOf(
                ColorUtils.blendColors(
                    ColorUtils.withAlpha(rgb, (alpha * 0.76f).coerceIn(0f, 1f)),
                    ColorUtils.withAlpha(highlight, 0.34f),
                    0.58f
                ),
                ColorUtils.withAlpha(rgb, (alpha * 0.56f).coerceIn(0f, 1f)),
                ColorUtils.blendColors(
                    ColorUtils.withAlpha(rgb, (alpha * 0.40f).coerceIn(0f, 1f)),
                    ColorUtils.withAlpha(Color.WHITE, 0.10f),
                    0.18f
                )
            )
        }
    }

    private fun applyBottomNavigationSelectedIndicator(config: NavigationBarConfig, bgColor: Int) = binding.run {
        val menuView = bottomNavigationView.getChildAt(0) as? ViewGroup ?: return@run
        val visibleItems = NavigationBarConfig.items
            .filter { bottomNavigationView.menu.findItem(it.menuId)?.isVisible == true }
        visibleItems.forEachIndexed { index, _ ->
            val child = menuView.getChildAt(index) ?: return@forEachIndexed
            child.background = Color.TRANSPARENT.toDrawable()
            child.setPadding(0, 3.dpToPx(), 0, 3.dpToPx())
        }
    }

    /**
     * 用户隐私与协议
     */
    private suspend fun privacyPolicy(): Boolean = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.privacyPolicyOk) {
            block.resume(true)
            return@sc
        }
        val privacyPolicy = String(assets.open("privacyPolicy.md").readBytes())
        alert(getString(R.string.privacy_policy), privacyPolicy) {
            positiveButton(R.string.agree) {
                LocalConfig.privacyPolicyOk = true
                block.resume(true)
            }
            negativeButton(R.string.refuse) {
                finish()
                block.resume(false)
            }
        }
    }

    /**
     * 版本更新日志
     */
    private suspend fun upVersion() = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.versionCode == appInfo.versionCode) {
            if (AppConfig.autoUpdateVariant) {
                if (LocalConfig.lastCheckUpdate + 24.hours.inWholeMilliseconds < System.currentTimeMillis()) {
                    AppUpdate.giteeUpdate.check(lifecycleScope)
                        .onSuccess {
                            showDialogFragment(
                                UpdateDialog(it)
                            )
                        }
                    LocalConfig.lastCheckUpdate = System.currentTimeMillis()
                }
            }
            block.resume(null)
            return@sc
        }
        LocalConfig.versionCode = appInfo.versionCode
        if (LocalConfig.isFirstOpenApp) {
            val help = String(assets.open("web/help/md/appHelp.md").readBytes())
            val dialog = TextDialog(getString(R.string.help), help, TextDialog.Mode.MD)
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else if (!BuildConfig.DEBUG) {
            val log = String(assets.open("web/help/md/updateLog.md").readBytes())
            val dialog = TextDialog(getString(R.string.update_log), log, TextDialog.Mode.MD, "updateLog")
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else {
            block.resume(null)
        }
    }

    /**
     * 设置本地密码
     */
    private suspend fun setLocalPassword() = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.password != null) {
            block.resume(null)
            return@sc
        }
        alert(R.string.set_local_password, R.string.set_local_password_summary) {
            val editTextBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "password"
            }
            customView {
                editTextBinding.root
            }
            onDismiss {
                block.resume(null)
            }
            okButton {
                LocalConfig.password = editTextBinding.editView.text.toString()
            }
            cancelButton {
                LocalConfig.password = ""
            }
        }
    }

    private fun notifyAppCrash() {
        if (!LocalConfig.appCrash || BuildConfig.DEBUG) {
            return
        }
        LocalConfig.appCrash = false
        alert(getString(R.string.draw), "检测到阅读发生了崩溃，是否打开崩溃日志以便报告问题？") {
            yesButton {
                showDialogFragment<CrashLogsDialog>()
            }
            noButton()
        }
    }

    /**
     * 备份同步
     */
    private fun backupSync() {
        if (!AppConfig.autoCheckNewBackup) {
            return
        }
        lifecycleScope.launch {
            val lastBackupFile =
                withContext(IO) { AppWebDav.lastBackUp().getOrNull() } ?: return@launch
            if (lastBackupFile.lastModify - LocalConfig.lastBackup > DateUtils.MINUTE_IN_MILLIS) {
                LocalConfig.lastBackup = lastBackupFile.lastModify
                alert(R.string.restore, R.string.webdav_after_local_restore_confirm) {
                    cancelButton()
                    okButton {
                        viewModel.restoreWebDav(lastBackupFile.displayName)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (AppConfig.autoRefreshBook) {
            outState.putBoolean("isAutoRefreshedBook", true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    /**
     * 如果重启太快fragment不会重建,这里更新一下书架的排序
     */
    override fun recreate() {
        (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.run {
            upSort()
        }
        super.recreate()
    }

    override fun observeLiveBus() {
        viewModel.onUpBooksLiveData.observe(this) {
            if (onUpBooksBadgeView == null) {
                onUpBooksBadgeView = binding.bottomNavigationView.addBadgeView(0)
            }
            onUpBooksBadgeView!!.setBadgeCount(it)
        }
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
        observeEvent<Boolean>(EventBus.NAVIGATION_BAR_CHANGED) {
            if (it == AppConfig.isNightTheme) {
                refreshBottomNavigationConfig(force = true)
            }
        }
        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            binding.apply {
                if (it) {
                    bottomNavigationView.menu.clear()
                    bottomNavigationView.inflateMenu(R.menu.main_bnv)
                    refreshBottomNavigationConfig(force = true)
                    onUpBooksBadgeView = null
                }
                upBottomMenu()
                if (it) {
                    viewPagerMain.setCurrentItem(bottomMenuCount - 1, false)
                }
            }
        }
        observeEvent<String>(PreferKey.threadCount) {
            viewModel.upPool()
        }
    }

    private fun upBottomMenu() {
        val showDiscovery = AppConfig.showDiscovery
        val showRss = AppConfig.showRSS
        binding.bottomNavigationView.menu.let { menu ->
            menu.findItem(R.id.menu_discovery).isVisible = showDiscovery
            menu.findItem(R.id.menu_rss).isVisible = showRss
        }
        var index = 0
        if (showDiscovery) {
            index++
            realPositions[index] = idExplore
        }
        if (showRss) {
            index++
            realPositions[index] = idRss
        }
        index++
        realPositions[index] = idMy
        bottomMenuCount = index + 1
        adapter.notifyDataSetChanged()
    }

    private fun upHomePage() {
        when (AppConfig.defaultHomePage) {
            "bookshelf" -> {}
            "explore" -> if (AppConfig.showDiscovery) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)
            }

            "rss" -> if (AppConfig.showRSS) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idRss), false)
            }

            "my" -> binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
        }
    }

    private fun getFragmentId(position: Int): Int {
        val id = realPositions[position]
        if (id == idBookshelf) {
            return if (AppConfig.bookGroupStyle == 1) idBookshelf2 else idBookshelf1
        }
        return id
    }

    private inner class PageChangeCallback : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            pagePosition = position
            binding.bottomNavigationView.menu[realPositions[position]].isChecked = true
            val config = NavigationBarConfig.activeConfig(this@MainActivity, AppConfig.isNightTheme)
            applyBottomNavigationSelectedIndicator(config, bottomBackground)
        }

    }

    @Suppress("DEPRECATION")
    private inner class TabFragmentPageAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        private fun getId(position: Int): Int {
            return getFragmentId(position)
        }

        override fun getItemPosition(any: Any): Int {
            val position = (any as MainFragmentInterface).position
                ?: return POSITION_NONE
            val fragmentId = getId(position)
            if ((fragmentId == idBookshelf1 && any is BookshelfFragment1)
                || (fragmentId == idBookshelf2 && any is BookshelfFragment2)
                || (fragmentId == idExplore && any is ExploreFragment)
                || (fragmentId == idRss && any is RssFragment)
                || (fragmentId == idMy && any is MyFragment)
            ) {
                return POSITION_UNCHANGED
            }
            return POSITION_NONE
        }

        override fun getItem(position: Int): Fragment {
            return when (getId(position)) {
                idBookshelf1 -> BookshelfFragment1(position)
                idBookshelf2 -> BookshelfFragment2(position)
                idExplore -> ExploreFragment(position)
                idRss -> RssFragment(position)
                else -> MyFragment(position)
            }
        }

        override fun getCount(): Int {
            return bottomMenuCount
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as Fragment
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as Fragment
            }
            fragmentMap[getId(position)] = fragment
            return fragment
        }

    }

    override fun openImportUi(type:Int, source: String) {
        when (type) {
            0 -> showDialogFragment(
                ImportBookSourceDialog(source)
            )
            1 -> showDialogFragment(
                ImportRssSourceDialog(source)
            )
            2 -> showDialogFragment(
                ImportReplaceRuleDialog(source)
            )
        }
    }

}
