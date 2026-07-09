package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ItemTextBinding
import io.legado.app.databinding.PopupActionMenuBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.gone
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import kotlin.math.max
import kotlin.math.min

/**
 * 文本操作菜单
 * 
 * 功能说明：
 * 1. 长按文本后显示的弹出菜单，提供复制、分享、朗读、书签、替换、搜索等操作
 * 2. 支持集成系统文本处理菜单（Android 6.0+），如翻译、搜索等第三方应用
 * 3. 支持展开/收起更多菜单项
 * 4. 继承自PopupWindow，以弹出窗口形式显示
 * 
 * 使用场景：
 * - 阅读界面长按文本选择后显示
 * - 提供对选中文本的各种操作功能
 */
@SuppressLint("RestrictedApi")
class TextActionMenu(private val context: Context, private val callBack: CallBack) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private companion object {
        private const val PRIMARY_MENU_LIMIT = 7
    }

    /** 菜单布局绑定对象 */
    private val binding = PopupActionMenuBinding.inflate(LayoutInflater.from(context))
    
    /** 菜单项适配器，用于显示菜单项列表 */
    private val adapter = Adapter(context).apply {
        setHasStableIds(true)
    }
    
    /** 所有菜单项列表（包括自定义和系统菜单项） */
    private var menuItems: List<MenuItemImpl> = emptyList()
    
    /** 可见菜单项列表（前7项） */
    private val visibleMenuItems = arrayListOf<MenuItemImpl>()
    
    /** 更多菜单项列表（第7项之后的菜单项） */
    private val moreMenuItems = arrayListOf<MenuItemImpl>()
    
    /** 是否展开文本菜单，从配置中读取 */
    private val expandTextMenu get() = context.getPrefBoolean(PreferKey.expandTextMenu)
    
    /** 隐藏的菜单项ID集合，每次都从配置中读取 */
    private val hiddenMenuItemIds: Set<Int>
        get() = TextMenuConfig.getHiddenMenuItemIds(context)

    private var lastShowRequest: ShowRequest? = null
    private var popupY: Int = 0

    init {
        @SuppressLint("InflateParams")
        contentView = binding.root

        // 设置弹出窗口属性
        isTouchable = true      // 可触摸
        isOutsideTouchable = false  // 点击外部不关闭
        isFocusable = false     // 不获取焦点
        elevation = dp(8).toFloat()
        animationStyle = R.style.TextActionMenuAnimation

        // 设置适配器
        binding.recyclerView.adapter = adapter
        binding.recyclerViewMore.adapter = adapter
        
        // 菜单消失时的回调
        setOnDismissListener {
            // 如果不是展开模式，恢复默认状态
            if (!context.getPrefBoolean(PreferKey.expandTextMenu)) {
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.gone()
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visible()
            }
        }
        
        // 更多按钮点击事件：切换显示更多菜单项
        binding.ivMenuMore.setOnClickListener {
            if (binding.recyclerView.isVisible) {
                // 显示更多菜单项
                binding.ivMenuMore.setImageResource(R.drawable.ic_arrow_back)
                adapter.setItems(moreMenuItems)
                binding.recyclerView.gone()
                binding.recyclerViewMore.visible()
                updatePopupPosition(keepY = true)
            } else {
                // 返回主菜单项
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.gone()
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visible()
                updatePopupPosition(keepY = true)
            }
        }
        
        // 加载菜单项并设置到适配器
        reloadMenuItems()
        
        // 根据配置决定初始显示状态
        if (expandTextMenu) {
            // 展开模式：显示所有菜单项，隐藏更多按钮
            adapter.setItems(menuItems)
            binding.ivMenuMore.gone()
        } else {
            // 折叠模式：只显示前7项，按需显示更多按钮
            adapter.setItems(visibleMenuItems)
            binding.ivMenuMore.isVisible = moreMenuItems.isNotEmpty()
        }
    }
    
    /**
     * 重新加载菜单项
     * 从配置中读取隐藏的菜单项，重新构建菜单列表
     */
    private fun reloadMenuItems() {
        // 构建菜单项
        val myMenu = MenuBuilder(context)      // 自定义菜单
        val otherMenu = MenuBuilder(context)    // 系统菜单（Android 6.0+）
        SupportMenuInflater(context).inflate(R.menu.content_select_action, myMenu)
        applyBuiltInMenuIcons(myMenu)
        
        // Android 6.0+ 支持系统文本处理菜单
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            onInitializeMenu(otherMenu)
        }
        
        // 合并自定义菜单和系统菜单
        val allMenuItems = myMenu.visibleItems + otherMenu.visibleItems
        
        // 过滤掉被隐藏的菜单项
        menuItems = allMenuItems.filter { it.itemId !in hiddenMenuItemIds }
        
        // 清空旧数据
        visibleMenuItems.clear()
        moreMenuItems.clear()
        
        // 将菜单项分为可见项（前6项）和更多项（第6项之后）
        if (menuItems.size > PRIMARY_MENU_LIMIT) {
            visibleMenuItems.addAll(menuItems.subList(0, PRIMARY_MENU_LIMIT))
            moreMenuItems.addAll(menuItems.subList(PRIMARY_MENU_LIMIT, menuItems.size))
        } else {
            // 如果菜单项少于7个，全部显示在主菜单
            visibleMenuItems.addAll(menuItems)
        }
    }

    private fun applyBuiltInMenuIcons(menu: Menu) {
        menu.findItem(R.id.menu_replace)?.setIcon(R.drawable.ic_translate)
        menu.findItem(R.id.menu_copy)?.setIcon(R.drawable.ic_copy)
        menu.findItem(R.id.menu_bookmark)?.setIcon(R.drawable.ic_bookmark)
        menu.findItem(R.id.menu_aloud)?.setIcon(R.drawable.ic_volume_up)
        menu.findItem(R.id.menu_dict)?.setIcon(R.drawable.ic_translate)
        menu.findItem(R.id.menu_web_search)?.setIcon(R.drawable.ic_search)
        menu.findItem(R.id.menu_text_menu_config)?.setIcon(R.drawable.ic_settings)
        menu.findItem(R.id.menu_highlight_rule_config)?.setIcon(R.drawable.ic_magic_star)
        menu.findItem(R.id.menu_search_content)?.setIcon(R.drawable.ic_search_hint)
        menu.findItem(R.id.menu_browser)?.setIcon(R.drawable.ic_web)
        menu.findItem(R.id.menu_share_str)?.setIcon(R.drawable.ic_share)
    }

    /**
     * 更新菜单显示状态
     * 根据配置决定是展开显示所有菜单项，还是折叠显示前7项
     */
    fun upMenu() {
        // 重新加载菜单项，确保使用最新的配置
        reloadMenuItems()
        
        if (expandTextMenu) {
            // 展开模式：显示所有菜单项，隐藏更多按钮
            adapter.setItems(menuItems)
            binding.ivMenuMore.gone()
        } else {
            // 折叠模式：只显示前7项，按需显示更多按钮
            adapter.setItems(visibleMenuItems)
            binding.ivMenuMore.isVisible = moreMenuItems.isNotEmpty()
        }
    }

    /**
     * 显示文本操作菜单
     * 
     * @param view 父视图
     * @param windowHeight 窗口高度
     * @param startX 选择起始点X坐标
     * @param startTopY 选择起始点顶部Y坐标
     * @param startBottomY 选择起始点底部Y坐标
     * @param endX 选择结束点X坐标
     * @param endBottomY 选择结束点底部Y坐标
     * 
     * 显示策略：
     * 1. 展开模式：优先在起始点上方显示，空间不足则在下方显示
     * 2. 折叠模式：需要测量菜单高度，确保菜单不会超出屏幕
     */
    fun show(
        view: View,
        windowHeight: Int,
        startX: Int,
        startTopY: Int,
        startBottomY: Int,
        endX: Int,
        endBottomY: Int
    ) {
        lastShowRequest = ShowRequest(
            view = view,
            windowHeight = windowHeight,
            startX = startX,
            startTopY = startTopY,
            startBottomY = startBottomY,
            endX = endX,
            endBottomY = endBottomY
        )
        upMenu()
        val position = calculatePopupPosition(lastShowRequest ?: return)
        popupY = position.y
        showAtLocation(view, Gravity.TOP or Gravity.START, position.x, position.y)
    }

    /**
     * 菜单项适配器
     * 用于在RecyclerView中显示菜单项列表
     */
    private fun updatePopupPosition(keepY: Boolean = false) {
        val request = lastShowRequest ?: return
        if (!isShowing) return
        val position = calculatePopupPosition(request)
        update(position.x, if (keepY) popupY else position.y, -1, -1)
    }

    private fun calculatePopupPosition(request: ShowRequest): PopupPosition {
        val margin = dp(12)
        val gap = dp(12)
        val windowWidth = request.view.rootView.width
            .takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels
        val maxPopupWidth = (windowWidth - margin * 2).coerceAtLeast(dp(160))

        constrainMoreMenuHeight(request, margin, gap)
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(maxPopupWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(
                (request.windowHeight - margin * 2).coerceAtLeast(dp(120)),
                View.MeasureSpec.AT_MOST
            )
        )

        val popupWidth = min(contentView.measuredWidth, maxPopupWidth)
        val popupHeight = min(
            contentView.measuredHeight,
            (request.windowHeight - margin * 2).coerceAtLeast(dp(120))
        )
        val anchorX = if (binding.recyclerViewMore.isVisible) {
            windowWidth - popupWidth - margin
        } else {
            request.startX
        }
        val x = anchorX.coerceIn(
            margin,
            (windowWidth - popupWidth - margin).coerceAtLeast(margin)
        )

        val selectTop = request.startTopY
        val selectBottom = max(request.startBottomY, request.endBottomY)
        val spaceAbove = (selectTop - margin - gap).coerceAtLeast(0)
        val spaceBelow = (request.windowHeight - selectBottom - margin - gap).coerceAtLeast(0)
        val y = when {
            spaceAbove >= popupHeight -> selectTop - popupHeight - gap
            spaceBelow >= popupHeight -> selectBottom + gap
            spaceAbove >= spaceBelow -> margin
            else -> (request.windowHeight - popupHeight - margin).coerceAtLeast(selectBottom + gap)
        }

        return PopupPosition(x, y)
    }

    private fun constrainMoreMenuHeight(request: ShowRequest, margin: Int, gap: Int) {
        if (!binding.recyclerViewMore.isVisible) {
            binding.recyclerViewMore.layoutParams = binding.recyclerViewMore.layoutParams.apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            return
        }
        val maxHeight = max(
            request.startTopY - margin - gap,
            max(
                request.windowHeight - request.startBottomY - margin - gap,
                request.windowHeight - request.endBottomY - margin - gap
            )
        ).coerceAtLeast(dp(160))
        val contentHeight = (moreMenuItems.size * dp(48)).coerceAtLeast(dp(48))
        binding.recyclerViewMore.layoutParams = binding.recyclerViewMore.layoutParams.apply {
            width = dp(160)
            height = min(contentHeight, maxHeight)
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    private data class PopupPosition(val x: Int, val y: Int)

    private data class ShowRequest(
        val view: View,
        val windowHeight: Int,
        val startX: Int,
        val startTopY: Int,
        val startBottomY: Int,
        val endX: Int,
        val endBottomY: Int
    )

    inner class Adapter(context: Context) :
        RecyclerAdapter<MenuItemImpl, ItemTextBinding>(context) {

        override fun getItemId(position: Int): Long {
            return getItem(position)?.let { item ->
                if (item.itemId != Menu.NONE) {
                    item.itemId.toLong()
                } else {
                    item.intent?.component?.flattenToShortString()?.hashCode()?.toLong()
                        ?: item.title.hashCode().toLong()
                }
            } ?: -1L
        }

        override fun getViewBinding(parent: ViewGroup): ItemTextBinding {
            return ItemTextBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTextBinding,
            item: MenuItemImpl,
            payloads: MutableList<Any>
        ) {
            with(binding) {
                textView.text = item.title
                val isMoreList = this@TextActionMenu.binding.recyclerViewMore.isVisible
                textView.gravity = if (isMoreList) {
                    Gravity.CENTER_VERTICAL or Gravity.START
                } else {
                    Gravity.CENTER
                }
                textView.layoutParams = textView.layoutParams.apply {
                    width = if (isMoreList) {
                        ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                }
                if (isMoreList) {
                    item.icon?.let { icon ->
                        val size = 18.dpToPx()
                        icon.setBounds(0, 0, size, size)
                        textView.setCompoundDrawables(icon, null, null, null)
                        textView.compoundDrawablePadding = 6.dpToPx()
                    } ?: textView.setCompoundDrawables(null, null, null, null)
                } else {
                    textView.setCompoundDrawables(null, null, null, null)
                }
            }
        }

        /**
         * 注册菜单项点击监听器
         */
        override fun registerListener(holder: ItemViewHolder, binding: ItemTextBinding) {
            // 点击事件：执行菜单项操作
            holder.itemView.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    // 先尝试回调处理，如果回调返回false则自己处理
                    if (!callBack.onMenuItemSelected(it.itemId)) {
                        onMenuItemSelected(it)
                    }
                }
                // 操作完成后通知回调
                callBack.onMenuActionFinally()
            }
            
            // 长按事件：切换朗读模式（朗读选中内容 vs 从选择位置开始朗读）
            holder.itemView.setOnLongClickListener {
                if (AppConfig.contentSelectSpeakMod == 0) {
                    AppConfig.contentSelectSpeakMod = 1
                    context.toastOnUi("切换为从选择的地方开始一直朗读")
                } else {
                    AppConfig.contentSelectSpeakMod = 0
                    context.toastOnUi("切换为朗读选择内容")
                }
                true
            }
        }
    }

    /**
     * 处理菜单项点击事件
     * 处理复制、分享、浏览器搜索等基础操作
     * 
     * @param item 被点击的菜单项
     */
    private fun onMenuItemSelected(item: MenuItemImpl) {
        when (item.itemId) {
            // 复制文本到剪贴板
            R.id.menu_copy -> context.sendToClip(callBack.selectedText)
            
            // 分享文本
            R.id.menu_share_str -> context.share(callBack.selectedText)
            
            // 使用浏览器打开或搜索
            R.id.menu_browser -> {
                kotlin.runCatching {
                    val intent = if (callBack.selectedText.isAbsUrl()) {
                        // 如果是URL，直接打开
                        Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(callBack.selectedText)
                        }
                    } else {
                        // 否则使用搜索引擎搜索
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, callBack.selectedText)
                        }
                    }
                    context.startActivity(intent)
                }.onFailure {
                    it.printOnDebug()
                    context.toastOnUi(it.localizedMessage ?: "ERROR")
                }
            }

            // 其他菜单项：系统文本处理菜单（Android 6.0+）
            else -> item.intent?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    kotlin.runCatching {
                        // 将选中的文本传递给目标应用
                        it.putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                        context.startActivity(it)
                    }.onFailure { e ->
                        AppLog.put("执行文本菜单操作出错\n$e", e, true)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntent(): Intent {
        return Intent()
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedActivities(): List<ResolveInfo> {
        return context.packageManager
            .queryIntentActivities(createProcessTextIntent(), 0)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo): Intent {
        return createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)
    }

    /**长按文字菜单
     * 首先设置一个足够大的菜单项排序值
     * 确保你的"PROCESS_TEXT"菜单项显示在
     * 剪切、复制、粘贴等标准选择菜单项之后。
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun onInitializeMenu(menu: Menu) {
        kotlin.runCatching {
            val hiddenItems = TextMenuConfig.getHiddenProcessTextItems(context)
            var menuItemOrder = 100
            for (resolveInfo in getSupportedActivities()) {
                val packageName = resolveInfo.activityInfo.packageName
                val className = resolveInfo.activityInfo.name
                val itemKey = TextMenuConfig.getProcessTextItemKey(packageName, className)
                if (itemKey !in hiddenItems) {
                    menu.add(
                        Menu.NONE, Menu.NONE,
                        menuItemOrder++, resolveInfo.loadLabel(context.packageManager)
                    ).apply {
                        intent = createProcessTextIntentForResolveInfo(resolveInfo)
                        icon = kotlin.runCatching {
                            resolveInfo.loadIcon(context.packageManager)
                        }.getOrNull()
                    }
                }
            }
        }.onFailure {
            context.toastOnUi("获取文字操作菜单出错:${it.localizedMessage}")
        }
    }

    /**
     * 文本操作菜单回调接口
     */
    interface CallBack {
        /** 获取当前选中的文本 */
        val selectedText: String

        /**
         * 菜单项被选中时的回调
         * @param itemId 菜单项ID
         * @return true表示已处理，false表示未处理需要菜单自己处理
         */
        fun onMenuItemSelected(itemId: Int): Boolean

        /**
         * 菜单操作完成后的回调
         * 用于执行清理工作，如关闭菜单、取消文本选择等
         */
        fun onMenuActionFinally()
    }
}
