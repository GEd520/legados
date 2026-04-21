package io.legado.app.constant

@Suppress("ConstPropertyName")
object ReadConstants {

    // 预下载并发数，控制同时预下载的章节数量
    const val PRE_DOWNLOAD_CONCURRENCY = 2

    // 预下载前延迟时间（毫秒），避免立即请求导致过于频繁
    const val PRE_DOWNLOAD_DELAY_MS = 1000L

    // 下载失败最大重试次数，超过此值则跳过该章节不再重试
    const val MAX_DOWNLOAD_FAIL_COUNT = 3

    // 向前预下载的最大章节数，用于预加载当前章节之前的章节
    const val BACKWARD_PRE_DOWNLOAD_RANGE = 5

    // 目录更新剩余章节阈值，剩余未读章节数低于此值才触发目录更新检查
    const val TOC_UPDATE_REMAINING_THRESHOLD = 3

    // 目录更新检查的最小间隔时间（10分钟，毫秒），防止频繁检查目录更新
    const val TOC_UPDATE_MIN_INTERVAL_MS = 600_000L

    // 滚动阅读模式下提前刷新页面的页数差阈值
    const val SCROLL_PAGE_UPDATE_THRESHOLD = 3

    // 音频播放速度最小值
    const val MIN_PLAY_SPEED = 0.5f

    // 音频播放速度最大值
    const val MAX_PLAY_SPEED = 3.0f

    // 缓存下载服务通知栏内容刷新间隔（毫秒）
    const val NOTIFICATION_UPDATE_INTERVAL_MS = 1000L

    // Web服务响应数据列表大小阈值，超过此值使用分块传输编码
    const val CHUNKED_RESPONSE_LIST_THRESHOLD = 3000

    // Web服务分块传输Pipe缓冲区大小（16KB）
    const val PIPE_BUFFER_SIZE = 16 * 1024

    // 书源校验时域名可达性检查总超时时间（毫秒）
    const val DOMAIN_CHECK_TIMEOUT_MS = 2000L

    // 书源校验时Socket连接超时时间（毫秒）
    const val SOCKET_CONNECT_TIMEOUT_MS = 1600

    // 书源校验目录时取的章节数量
    const val TOC_CHECK_CHAPTER_COUNT = 2

    // 阅读页面触摸区域划分：1/3位置
    const val TOUCH_AREA_THIRD = 0.33f

    // 阅读页面触摸区域划分：2/3位置
    const val TOUCH_AREA_TWO_THIRDS = 0.66f

    // 阅读页面右侧触摸区域起始偏移比例
    const val TOUCH_AREA_RIGHT_OFFSET = 0.36f

    // 书源导出时选中率阈值，低于此值使用选中项导出，高于此值使用过滤导出
    const val SOURCE_SELECTED_RATE_THRESHOLD = 0.3f

    // 书源编辑界面焦点切换后发送文本的延迟时间（毫秒）
    const val EDIT_FOCUS_DELAY_MS = 120L
}
