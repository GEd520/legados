package io.legado.app.data.repository.debug

import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.help.config.AppConfig
import io.legado.app.model.debug.DebugLogScope
import io.legado.app.model.debug.FlowLogItem
import io.legado.app.model.debug.SourceType
import io.legado.app.model.debug.FlowStage
import io.legado.app.model.debug.JsExecutionRecord
import io.legado.app.model.debug.JsExecutionContext
import io.legado.app.model.debug.RuleExecutionTree
import io.legado.app.model.debug.RuleType
import io.legado.app.model.debug.BookDataFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 流程日志记录器
 * 
 * 负责记录源规则执行的完整流程，包括：
 * - 网络请求阶段
 * - 规则解析阶段（含规则执行路径树）
 * - 字段提取阶段
 * - 数据替换阶段
 * - JS执行环境状态
 * 
 * 特性：
 * - 异步记录日志，不阻塞主流程
 * - 最多保留500条日志，超过自动删除最旧的
 * - 按书源URL分组管理请求ID
 * - 支持按阶段、书源、操作类型过滤
 */
object FlowLogRecorder {

     // 最大日志数量限制
    private const val MAX_LOG_COUNT = 500
    private const val UPDATE_DEBOUNCE_MS = 100L
    // 日志列表，使用MutableSharedFlow实现响应式更新，设置缓冲区避免丢失更新
    private val _logs = MutableSharedFlow<List<FlowLogItem>>(
        replay = 1, // 保留最新的1个值，新订阅者可以立即收到
        extraBufferCapacity = 64// 缓冲区大小，减少丢失更新的风险
    )
    val logs: SharedFlow<List<FlowLogItem>> = _logs.asSharedFlow()
    
     // 请求会话映射：书源URL -> 请求ID
    private val logDeque = ArrayDeque<FlowLogItem>()
    private val pendingUpdate = AtomicBoolean(false)

    private val requestSessions = ConcurrentHashMap<String, String>()
    
    // 操作类型映射：书源URL -> 操作类型（搜索/详情/目录/正文）
    private val operationMap = ConcurrentHashMap<String, String>()

    val isEnabled: Boolean get() = AppConfig.debugLogFloatingBall

    private fun sourceTypeOf(source: BaseSource?): SourceType {
        return if (source is RssSource) SourceType.RSS else SourceType.BOOK
    }

    /**
     * 设置当前书源的操作类型
     * 
     * @param sourceUrl 书源URL
     * @param operation 操作类型（搜索/发现/详情/目录/正文）
     */
    fun setOperation(sourceUrl: String, operation: String) {
        if (!isEnabled) return
        operationMap[sourceUrl] = operation
    }

    /**
     * 获取当前书源的操作类型
     */
    private fun getOperation(sourceUrl: String?): String? {
        return sourceUrl?.let { operationMap[it] }
    }

    /**
     * 开始新的调试会话
     * 
     * @param sourceUrl 书源URL
     * @param sourceName 书源名称
     * @return 请求ID
     */
    fun startSession(sourceUrl: String, sourceName: String? = null): String {
        if (!isEnabled) return ""
        val requestId = UUID.randomUUID().toString()
        requestSessions[sourceUrl] = requestId
        return requestId
    }

    /**
     * 获取或创建请求ID
     * 
     * @param sourceUrl 书源URL
     * @return 请求ID，如果没有则创建新的
     */
    fun getOrCreateRequestId(sourceUrl: String): String {
        if (!isEnabled) return ""
        return requestSessions.getOrPut(sourceUrl) {
            UUID.randomUUID().toString()
        }
    }

    /**
     * 结束调试会话
     * 
     * @param sourceUrl 书源URL
     */
    fun endSession(sourceUrl: String) {
        requestSessions.remove(sourceUrl)
        operationMap.remove(sourceUrl)
    }

    /**
     * 记录网络请求日志
     * 
     * @param source 书源对象
     * @param message 日志消息
     * @param url 请求URL
     * @param method 请求方法（GET/POST等）
     * @param statusCode 响应状态码
     * @param duration 请求耗时（毫秒）
     * @param detail 详细信息（如响应体）
     * @param error 错误信息
     */
    fun logNetwork(
        source: BaseSource?,
        message: String,
        url: String? = null,
        method: String? = null,
        statusCode: Int? = null,
        duration: Long? = null,
        detail: String? = null,
        error: Throwable? = null,
        /** 请求头，从 AnalyzeUrl.headerMap 传入 */
        requestHeaders: Map<String, String>? = null,
        /** Cookie 值，从 headerMap["Cookie"] 提取 */
        cookies: String? = null
    ) {
        if (!isEnabled) return
        val sourceUrl = source?.getKey()
        log(
            sourceUrl = sourceUrl,
            sourceName = source?.getTag(),
            sourceType = sourceTypeOf(source),
            stage = FlowStage.NETWORK,
            operation = getOperation(sourceUrl),
            message = message,
            detail = detail,
            url = url,
            method = method,
            statusCode = statusCode,
            duration = duration,
            error = error,
            requestHeaders = requestHeaders,
            cookies = cookies
        )
    }

    fun logParse(
        source: BaseSource?,
        message: String,
        rule: String? = null,
        result: String? = null,
        duration: Long? = null,
        detail: String? = null,
        error: Throwable? = null
    ) {
        if (!isEnabled) return
        val sourceUrl = source?.getKey()
        log(
            sourceUrl = sourceUrl,
            sourceName = source?.getTag(),
            sourceType = sourceTypeOf(source),
            stage = FlowStage.PARSE,
            operation = getOperation(sourceUrl),
            message = message,
            detail = detail,
            rule = rule,
            result = result,
            duration = duration,
            error = error
        )
    }

    /**
     * 记录规则执行路径树日志
     * 
     * @param source 书源对象
     * @param executionTree 规则执行路径树
     * @param message 日志消息
     * @param error 错误信息
     */
    fun logRuleExecution(
        source: BaseSource?,
        executionTree: RuleExecutionTree,
        message: String = "规则执行完成",
        error: Throwable? = null,
        book: Book? = null,
        bookChapter: BookChapter? = null,
        bookSource: BookSource? = null
    ) {
        if (!isEnabled) return
        
        val sourceUrl = source?.getKey()
        val sourceName = source?.getTag()
        val operation = getOperation(sourceUrl)
        
        DebugLogScope.launch {
            val requestId = sourceUrl?.let { getOrCreateRequestId(it) }
                ?: UUID.randomUUID().toString()

            val item = FlowLogItem(
                requestId = requestId,
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                sourceType = sourceTypeOf(source),
                stage = FlowStage.PARSE,
                operation = operation,
                message = message,
                rule = executionTree.fullRule,
                result = executionTree.root.output?.take(100),
                duration = executionTree.totalDuration,
                executionTree = executionTree,
                ruleType = executionTree.root.ruleType,
                matchCount = executionTree.root.matchCount,
                inputPreview = executionTree.root.input?.take(100),
                outputPreview = executionTree.root.output?.take(100),
                error = error,
                book = book,
                bookChapter = bookChapter,
                bookSource = bookSource
            )

            addLog(item)
        }
    }

    /**
     * 记录JS执行日志
     * 
     * @param source 书源对象
     * @param jsExecution JS执行记录
     * @param message 日志消息
     * @param error 错误信息
     */
    fun logJsExecution(
        source: BaseSource?,
        jsExecution: JsExecutionRecord,
        message: String = "JS执行完成",
        error: Throwable? = null,
        book: Book? = null,
        bookChapter: BookChapter? = null,
        bookSource: BookSource? = null
    ) {
        if (!isEnabled) return
        
        val sourceUrl = source?.getKey()
        val sourceName = source?.getTag()
        val operation = getOperation(sourceUrl)
        
        DebugLogScope.launch {
            val requestId = sourceUrl?.let { getOrCreateRequestId(it) }
                ?: UUID.randomUUID().toString()

            val item = FlowLogItem(
                requestId = requestId,
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                sourceType = sourceTypeOf(source),
                stage = FlowStage.PARSE,
                operation = operation,
                message = message,
                rule = jsExecution.jsCode.take(200),
                result = jsExecution.result?.take(100),
                duration = jsExecution.duration,
                jsExecution = jsExecution,
                ruleType = RuleType.JS,
                error = error ?: jsExecution.error,
                book = book,
                bookChapter = bookChapter,
                bookSource = bookSource
            )

            addLog(item)
        }
    }

    /**
     * 记录JS执行环境状态
     * 
     * @param source 书源对象
     * @param jsCode JS代码
     * @param context JS执行环境
     * @param result 执行结果
     * @param duration 执行耗时
     * @param error 错误信息
     */
    fun logJsContext(
        source: BaseSource?,
        jsCode: String,
        context: JsExecutionContext,
        result: String? = null,
        duration: Long? = null,
        error: Throwable? = null,
        book: Book? = null,
        bookChapter: BookChapter? = null,
        bookSource: BookSource? = null
    ) {
        if (!isEnabled) return
        val jsExecution = JsExecutionRecord(
            jsCode = jsCode,
            context = context,
            result = result,
            duration = duration,
            error = error
        )
        logJsExecution(
            source = source,
            jsExecution = jsExecution,
            message = if (error != null) "JS执行失败" else "JS执行完成",
            error = error,
            book = book,
            bookChapter = bookChapter,
            bookSource = bookSource
        )
    }

    fun logExtract(
        source: BaseSource?,
        message: String,
        rule: String? = null,
        result: String? = null,
        originalValue: String? = null,
        duration: Long? = null,
        detail: String? = null,
        error: Throwable? = null,
        book: Book? = null,
        bookChapter: BookChapter? = null,
        bookSource: BookSource? = null
    ) {
        if (!isEnabled) return
        val sourceUrl = source?.getKey()
        log(
            sourceUrl = sourceUrl,
            sourceName = source?.getTag(),
            sourceType = sourceTypeOf(source),
            stage = FlowStage.EXTRACT,
            operation = getOperation(sourceUrl),
            message = message,
            detail = detail,
            rule = rule,
            result = result,
            originalValue = originalValue,
            duration = duration,
            error = error,
            book = book,
            bookChapter = bookChapter,
            bookSource = bookSource
        )
    }

    /**
     * 记录数据替换日志
     * 
     * @param source 书源对象
     * @param message 日志消息
     * @param rule 替换规则
     * @param result 替换结果
     * @param originalValue 原始数据（替换前的数据）
     * @param duration 替换耗时（毫秒）
     * @param detail 详细信息
     * @param error 错误信息
     */
    fun logReplace(
        source: BaseSource?,
        message: String,
        rule: String? = null,
        result: String? = null,
        originalValue: String? = null,
        duration: Long? = null,
        detail: String? = null,
        error: Throwable? = null,
        book: Book? = null,
        bookChapter: BookChapter? = null,
        bookSource: BookSource? = null
    ) {
        if (!isEnabled) return
        val sourceUrl = source?.getKey()
        log(
            sourceUrl = sourceUrl,
            sourceName = source?.getTag(),
            sourceType = sourceTypeOf(source),
            stage = FlowStage.REPLACE,
            operation = getOperation(sourceUrl),
            message = message,
            detail = detail,
            rule = rule,
            result = result,
            originalValue = originalValue,
            duration = duration,
            error = error,
            book = book,
            bookChapter = bookChapter,
            bookSource = bookSource
        )
    }

    /**
     * 记录变量操作日志
     * 
     * @param source 书源对象
     * @param operations 变量操作列表
     * @param message 日志消息
     */
    fun logVariable(
        source: BaseSource?,
        operations: List<io.legado.app.model.debug.VariableOperation>,
        message: String = "变量操作"
    ) {
        if (!isEnabled || operations.isEmpty()) return
        
        val sourceUrl = source?.getKey()
        val sourceName = source?.getTag()
        val operation = getOperation(sourceUrl)
        
        DebugLogScope.launch {
            val requestId = sourceUrl?.let { getOrCreateRequestId(it) }
                ?: UUID.randomUUID().toString()

            val summary = buildString {
                val reads = operations.count { it.operationType == io.legado.app.model.debug.VariableOperationType.READ }
                val writes = operations.count { it.operationType == io.legado.app.model.debug.VariableOperationType.WRITE }
                val deletes = operations.count { it.operationType == io.legado.app.model.debug.VariableOperationType.DELETE }
                if (reads > 0) append("读${reads}次 ")
                if (writes > 0) append("写${writes}次 ")
                if (deletes > 0) append("删${deletes}次")
            }.trim()

            val item = FlowLogItem(
                requestId = requestId,
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                sourceType = sourceTypeOf(source),
                stage = FlowStage.VARIABLE,
                operation = operation,
                message = message,
                detail = summary,
                variableOperations = operations
            )

            addLog(item)
        }
    }

    /**
     * 记录单个变量读取操作
     * 
     * @param source 书源对象
     * @param key 变量名
     * @param value 变量值
     * @param storage 存储位置
     */
    fun logVariableRead(
        source: BaseSource?,
        key: String,
        value: String?,
        storage: io.legado.app.model.debug.VariableStorage = io.legado.app.model.debug.VariableStorage.UNKNOWN
    ) {
        if (!isEnabled) return
        val operation = io.legado.app.model.debug.VariableOperation(
            operationType = io.legado.app.model.debug.VariableOperationType.READ,
            key = key,
            value = value,
            storage = storage
        )
        logVariable(source, listOf(operation), "读取变量 $key")
    }

    /**
     * 记录单个变量写入操作
     * 
     * @param source 书源对象
     * @param key 变量名
     * @param value 变量值
     * @param oldValue 旧值（可选）
     * @param storage 存储位置
     */
    fun logVariableWrite(
        source: BaseSource?,
        key: String,
        value: String,
        oldValue: String? = null,
        storage: io.legado.app.model.debug.VariableStorage = io.legado.app.model.debug.VariableStorage.UNKNOWN
    ) {
        if (!isEnabled) return
        val operation = io.legado.app.model.debug.VariableOperation(
            operationType = io.legado.app.model.debug.VariableOperationType.WRITE,
            key = key,
            value = value,
            oldValue = oldValue,
            storage = storage
        )
        logVariable(source, listOf(operation), "写入变量 $key")
    }

    /**
     * 记录阶段数据流转日志
     * 
     * 用于记录单个阶段的数据流转，支持增量更新
     * 
     * @param source 书源对象
     * @param stage 数据流转阶段
     * @param fields 字段填充记录列表
     * @param message 日志消息
     */
    fun logStageDataFlow(
        source: BaseSource?,
        stage: io.legado.app.model.debug.DataFlowStage,
        fields: List<io.legado.app.model.debug.FieldFillRecord>,
        message: String = "${stage.displayName}数据流转",
        bookUrl: String? = null,
        bookName: String? = null,
        author: String? = null,
        book: Book? = null,
        bookChapter: BookChapter? = null,
        bookSource: BookSource? = null
    ) {
        if (!isEnabled || fields.isEmpty()) return

        val sourceUrl = source?.getKey()
        val sourceName = source?.getTag()
        val operation = getOperation(sourceUrl)

        DebugLogScope.launch {
            val requestId = sourceUrl?.let { getOrCreateRequestId(it) }
                ?: UUID.randomUUID().toString()

            val stageDataFlow = io.legado.app.model.debug.StageDataFlow(
                stage = stage,
                fields = fields
            )

            val bookDataFlow = BookDataFlow(
                bookUrl = bookUrl,
                bookName = bookName,
                author = author,
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                stages = listOf(stageDataFlow)
            )

            val summary = buildString {
                append("${stage.icon}${stage.displayName}: ")
                val changedCount = fields.count { it.hasChange() }
                val errorCount = fields.count { it.isError }
                append("${fields.size}个字段")
                if (changedCount > 0) append("，${changedCount}个变更")
                if (errorCount > 0) append("，${errorCount}个错误")
            }

            val item = FlowLogItem(
                requestId = requestId,
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                sourceType = sourceTypeOf(source),
                stage = FlowStage.DATA_FLOW,
                operation = operation,
                message = message,
                detail = summary,
                duration = stageDataFlow.duration,
                dataFlow = bookDataFlow,
                book = book,
                bookChapter = bookChapter,
                bookSource = bookSource
            )

            addLog(item)
        }
    }

    /**
     * 记录流程日志（异步）
     * 
     * @param sourceUrl 书源URL
     * @param sourceName 书源名称
     * @param stage 流程阶段
     * @param operation 操作类型
     * @param message 日志消息
     * @param detail 详细信息
     * @param duration 耗时（毫秒）
     * @param url 请求URL
     * @param method 请求方法
     * @param statusCode 状态码
     * @param rule 规则内容
     * @param result 执行结果
     * @param originalValue 原始数据（替换前的数据）
     * @param error 错误信息
     */
    fun log(
        sourceUrl: String?,
        sourceName: String? = null,
        sourceType: SourceType = SourceType.BOOK,
        stage: FlowStage,
        operation: String? = null,
        message: String,
        detail: String? = null,
        duration: Long? = null,
        url: String? = null,
        method: String? = null,
        statusCode: Int? = null,
        rule: String? = null,
        result: String? = null,
        originalValue: String? = null,
        error: Throwable? = null,
        executionTree: RuleExecutionTree? = null,
        jsExecution: JsExecutionRecord? = null,
        ruleType: RuleType? = null,
        matchCount: Int? = null,
        inputPreview: String? = null,
        outputPreview: String? = null,
        /** 网络请求头 */
        requestHeaders: Map<String, String>? = null,
        /** Cookie 值 */
        cookies: String? = null,
        /** 当前处理的书籍 */
        book: Book? = null,
        /** 当前处理的章节 */
        bookChapter: BookChapter? = null,
        bookSource: BookSource? = null
    ) {
        if (!isEnabled) return

        DebugLogScope.launch {
            // 获取或创建请求ID，用于分组
            val requestId = sourceUrl?.let { getOrCreateRequestId(it) }
                ?: UUID.randomUUID().toString()

            // 创建日志项
            val item = FlowLogItem(
                requestId = requestId,
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                sourceType = sourceType,
                stage = stage,
                operation = operation,
                message = message,
                detail = detail,
                duration = duration,
                url = url,
                method = method,
                statusCode = statusCode,
                rule = rule,
                result = result,
                originalValue = originalValue,
                error = error,
                executionTree = executionTree,
                jsExecution = jsExecution,
                ruleType = ruleType,
                matchCount = matchCount,
                inputPreview = inputPreview,
                outputPreview = outputPreview,
                requestHeaders = requestHeaders,
                cookies = cookies,
                book = book,
                bookChapter = bookChapter,
                bookSource = bookSource
            )

            addLog(item)
        }
    }

    /**
     * 添加日志项（内部方法）
     * 
     * @param item 日志项
     */
    private fun addLog(item: FlowLogItem) {
        synchronized(logDeque) {
            logDeque.addFirst(item)
            while (logDeque.size > MAX_LOG_COUNT) {
                logDeque.removeLast()
            }
        }
        scheduleUpdate()
    }
    
    private fun scheduleUpdate() {
        if (pendingUpdate.compareAndSet(false, true)) {
            DebugLogScope.launch {
                delay(UPDATE_DEBOUNCE_MS)
                pendingUpdate.set(false)
                _logs.emit(getCurrentLogs())
            }
        }
    }
    
    /**
     * 获取当前日志列表快照（同步方法）
     * 
     * @return 当前日志列表
     */
    fun getCurrentLogs(): List<FlowLogItem> {
        return synchronized(logDeque) {
            logDeque.toList()
        }
    }

    /**
     * 清空所有日志
     */
    fun clear() {
        synchronized(logDeque) {
            logDeque.clear()
        }
        requestSessions.clear()
        operationMap.clear()
        
        // 发送空列表到Flow
        DebugLogScope.launch {
            _logs.emit(emptyList())
        }
    }

    /**
     * 按请求ID分组
     * 
     * @param logs 日志列表
     * @return 按请求ID分组的日志Map
     */
    fun groupByRequestId(logs: List<FlowLogItem>): Map<String, List<FlowLogItem>> {
        return logs.groupBy { it.requestId }
    }

    /**
     * 按阶段过滤
     * 
     * @param logs 日志列表
     * @param stage 流程阶段（null表示不过滤）
     * @return 过滤后的日志列表
     */
    fun filterByStage(logs: List<FlowLogItem>, stage: FlowStage?): List<FlowLogItem> {
        return if (stage == null) logs else logs.filter { it.stage == stage }
    }

    /**
     * 按书源过滤
     * 
     * @param logs 日志列表
     * @param sourceUrl 书源URL（null表示不过滤）
     * @return 过滤后的日志列表
     */
    fun filterBySource(logs: List<FlowLogItem>, sourceUrl: String?): List<FlowLogItem> {
        return if (sourceUrl == null) logs else logs.filter { it.sourceUrl == sourceUrl }
    }

    /**
     * 按操作类型过滤
     * 
     * @param logs 日志列表
     * @param operation 操作类型（null表示不过滤）
     * @return 过滤后的日志列表
     */
    fun filterByOperation(logs: List<FlowLogItem>, operation: String?): List<FlowLogItem> {
        return if (operation == null) logs else logs.filter { it.operation == operation }
    }
}
