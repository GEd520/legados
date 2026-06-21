/**
 * URL访问记录界面 - Jetpack Compose实现
 * 
 * 功能特性：
 * 1. 多条件筛选：域名、来源、方法、状态
 * 2. 日期分组显示：今天、昨天、本周、更早
 * 3. 详情对话框：显示完整请求/响应信息
 * 4. 相对时间显示：如"5分钟前"
 * 5. FilterChip显示当前筛选条件
 */
package io.legado.app.ui.urlRecord

// ==================== 导入部分 ====================
// 动画相关
import androidx.compose.animation.AnimatedVisibility

// 基础布局和交互
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape

// 图标
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

// Material3 UI组件库
import androidx.compose.material3.*

// Compose核心
import androidx.compose.runtime.*

// UI相关工具
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.legado.app.data.entities.UrlRecord
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.theme.pageTopBarColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlRecordScreen(
    viewModel: UrlRecordViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val domains by viewModel.domains.collectAsState()
    val sourceNames by viewModel.sourceNames.collectAsState()
    val methods by viewModel.methods.collectAsState()
    val recordCount by viewModel.recordCount.collectAsState()
    val isRecordEnabled by viewModel.isRecordEnabled.collectAsState()
    
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilterPanel by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf<Int?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<UrlRecord?>(null) }
    
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val containerColor = pageCardContainerColor()
    val topBarColors = pageTopBarColors()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(searchQuery) {
        viewModel.setSearchQuery(searchQuery.ifBlank { null })
    }

    if (showClearDialog != null) {
        val days = showClearDialog!!
        AlertDialog(
            onDismissRequest = { showClearDialog = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (days == 0) "清除所有记录" else "清除${days}天前的记录") },
            text = {
                Text(if (days == 0) "确定要清除所有URL访问记录吗？" else "确定要清除${days}天前的记录吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            if (days == 0) {
                                viewModel.clearAll()
                            } else {
                                viewModel.deleteOldRecords(days)
                            }
                        }
                        showClearDialog = null
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    selectedRecord?.let { record ->
        RecordDetailDialog(
            record = record,
            onDismiss = { selectedRecord = null }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColors.containerColor,
                    scrolledContainerColor = topBarColors.containerColor,
                    navigationIconContentColor = topBarColors.contentColor,
                    titleContentColor = topBarColors.contentColor,
                    actionIconContentColor = topBarColors.contentColor
                ),
                title = {
                    Column {
                        Text(
                            text = "URL访问记录",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        )
                        if (recordCount > 0) {
                            Text(
                                text = "共 $recordCount 条记录",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { showFilterPanel = !showFilterPanel }) {
                        val hasFilters = viewModel.hasActiveFilters()
                        Badge(
                            containerColor = if (hasFilters) MaterialTheme.colorScheme.error 
                                             else Color.Transparent,
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "筛选")
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("开启URL记录")
                                        Spacer(Modifier.weight(1f))
                                        if (isRecordEnabled) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    val newEnabled = !isRecordEnabled
                                    viewModel.setRecordUrl(newEnabled)
                                    Toast.makeText(
                                        context,
                                        if (newEnabled) "已开启URL记录" else "已关闭URL记录",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isRecordEnabled) Icons.Default.ToggleOn 
                                        else Icons.Default.ToggleOff,
                                        contentDescription = null
                                    )
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("清除7天前的记录") },
                                onClick = {
                                    showClearDialog = 7
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("清除30天前的记录") },
                                onClick = {
                                    showClearDialog = 30
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Text("清除所有记录", color = MaterialTheme.colorScheme.error) 
                                },
                                onClick = {
                                    showClearDialog = 0
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteForever, 
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索URL/域名/来源") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = containerColor,
                        unfocusedContainerColor = containerColor,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    singleLine = true
                )
            }

            AnimatedVisibility(visible = showFilterPanel) {
                FilterPanel(
                    viewModel = viewModel,
                    domains = domains,
                    sourceNames = sourceNames,
                    methods = methods,
                    containerColor = containerColor
                )
            }

            ActiveFilterChips(viewModel = viewModel)

            when (val state = uiState) {
                is UrlRecordUIState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is UrlRecordUIState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "暂无URL访问记录",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "开启URL记录后，所有网络请求都会被记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is UrlRecordUIState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                is UrlRecordUIState.Success -> {
                    val groupedRecords = remember(state.records) { 
                        groupRecordsByDate(state.records) 
                    }
                    GroupedRecordList(
                        groupedRecords = groupedRecords,
                        onRecordClick = { selectedRecord = it }
                    )
                }
            }
        }
    }
}

/**
 * 按日期分组记录
 */
private fun groupRecordsByDate(records: List<UrlRecord>): Map<String, List<UrlRecord>> {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val todayStart = calendar.timeInMillis
    
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayStart = calendar.timeInMillis
    
    calendar.add(Calendar.DAY_OF_YEAR, -5)
    val weekStart = calendar.timeInMillis
    
    return records.groupBy { record ->
        when {
            record.timestamp >= todayStart -> "今天"
            record.timestamp >= yesterdayStart -> "昨天"
            record.timestamp >= weekStart -> "本周"
            else -> {
                val sdf = SimpleDateFormat("MM月dd日", Locale.getDefault())
                sdf.format(Date(record.timestamp))
            }
        }
    }
}

/**
 * 计算相对时间
 */
private fun getRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        diff < 604_800_000 -> "${diff / 86_400_000}天前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

/**
 * 筛选面板
 */
@Composable
private fun FilterPanel(
    viewModel: UrlRecordViewModel,
    domains: List<String>,
    sourceNames: List<String>,
    methods: List<String>,
    containerColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (domains.isNotEmpty()) {
                FilterSection(
                    title = "域名",
                    items = domains,
                    selectedItem = viewModel.currentDomain,
                    onSelect = { viewModel.filterByDomain(it) }
                )
            }
            
            if (sourceNames.isNotEmpty()) {
                FilterSection(
                    title = "来源",
                    items = sourceNames,
                    selectedItem = viewModel.currentSourceName,
                    onSelect = { viewModel.filterBySourceName(it) }
                )
            }
            
            if (methods.isNotEmpty()) {
                FilterSection(
                    title = "方法",
                    items = methods,
                    selectedItem = viewModel.currentMethod,
                    onSelect = { viewModel.filterByMethod(it) }
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "状态",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(50.dp)
                )
                FilterChip(
                    selected = viewModel.currentSuccess == true,
                    onClick = { viewModel.filterByStatus(if (viewModel.currentSuccess == true) null else true) },
                    label = { Text("成功") },
                    modifier = Modifier.padding(end = 4.dp)
                )
                FilterChip(
                    selected = viewModel.currentSuccess == false,
                    onClick = { viewModel.filterByStatus(if (viewModel.currentSuccess == false) null else false) },
                    label = { Text("失败") }
                )
            }
        }
    }
}

/**
 * 筛选区域
 */
@Composable
private fun FilterSection(
    title: String,
    items: List<String>,
    selectedItem: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(50.dp)
        )
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = selectedItem == null,
                onClick = { onSelect(null) },
                label = { Text("全部") }
            )
            items.take(10).forEach { item ->
                FilterChip(
                    selected = selectedItem == item,
                    onClick = { onSelect(if (selectedItem == item) null else item) },
                    label = { 
                        Text(
                            text = item.take(15) + if (item.length > 15) "..." else "",
                            maxLines = 1
                        ) 
                    }
                )
            }
            if (items.size > 10) {
                Text(
                    text = "+${items.size - 10}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 激活的筛选条件标签
 */
@Composable
private fun ActiveFilterChips(viewModel: UrlRecordViewModel) {
    val hasFilters = viewModel.hasActiveFilters()
    
    if (hasFilters) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                viewModel.currentDomain?.let { domain ->
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.filterByDomain(null) },
                        label = { Text("域名: ${domain.take(20)}") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "清除",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                viewModel.currentSourceName?.let { source ->
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.filterBySourceName(null) },
                        label = { Text("来源: ${source.take(15)}") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "清除",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                viewModel.currentMethod?.let { method ->
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.filterByMethod(null) },
                        label = { Text("方法: $method") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "清除",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                viewModel.currentSuccess?.let { success ->
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.filterByStatus(null) },
                        label = { Text(if (success) "状态: 成功" else "状态: 失败") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "清除",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                viewModel.searchViewQuery?.let { query ->
                    if (query.isNotBlank()) {
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.setSearchQuery(null) },
                            label = { Text("搜索: ${query.take(10)}") },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "清除",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
                TextButton(
                    onClick = { viewModel.clearAllFilters() },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("清除全部", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * 分组记录列表
 */
@Composable
private fun GroupedRecordList(
    groupedRecords: Map<String, List<UrlRecord>>,
    onRecordClick: (UrlRecord) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        groupedRecords.forEach { (dateGroup, records) ->
            item(key = "header_$dateGroup") {
                Text(
                    text = dateGroup,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(records, key = { it.id }) { record ->
                UrlRecordItem(
                    record = record,
                    onClick = { onRecordClick(record) }
                )
            }
        }
    }
}

/**
 * URL记录列表项
 */
@Composable
private fun UrlRecordItem(
    record: UrlRecord,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MethodBadge(method = record.method)
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(
                    responseCode = record.responseCode,
                    errorMsg = record.errorMsg
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${record.duration}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = getRelativeTime(record.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = record.domain,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF009688)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = record.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            record.sourceName?.let { source ->
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFF9800).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = source,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * 记录详情对话框
 */
@Composable
private fun RecordDetailDialog(
    record: UrlRecord,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "请求详情",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailSection(title = "基本信息") {
                    DetailRow("方法", record.method)
                    DetailRow("状态码", record.responseCode.toString())
                    DetailRow("耗时", "${record.duration}ms")
                    DetailRow("时间", dateFormat.format(Date(record.timestamp)))
                    DetailRow("相对时间", getRelativeTime(record.timestamp))
                }
                
                DetailSection(title = "URL信息") {
                    DetailRow("域名", record.domain)
                    SelectableText(
                        text = record.url,
                        label = "完整URL"
                    )
                }
                
                record.sourceName?.let { source ->
                    DetailSection(title = "来源信息") {
                        DetailRow("来源名称", source)
                        record.sourceUrl?.let { url ->
                            DetailRow("来源URL", url)
                        }
                    }
                }
                
                record.requestBody?.let { body ->
                    if (body.isNotBlank()) {
                        DetailSection(title = "请求体") {
                            SelectableText(
                                text = body,
                                label = "Body"
                            )
                        }
                    }
                }
                
                record.errorMsg?.let { error ->
                    if (error.isNotBlank()) {
                        DetailSection(title = "错误信息", isError = true) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) 
                                    as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("URL", record.url)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "URL已复制", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("复制URL")
            }
        }
    )
}

/**
 * 详情区域
 */
@Composable
private fun DetailSection(
    title: String,
    isError: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isError) MaterialTheme.colorScheme.error 
                    else MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(
            color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                   else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        content()
    }
}

/**
 * 详情行
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

/**
 * 可选择文本
 */
@Composable
private fun SelectableText(text: String, label: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 100.dp),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

/**
 * HTTP方法标签
 */
@Composable
private fun MethodBadge(method: String) {
    val isPost = method.equals("POST", ignoreCase = true)
    val isPut = method.equals("PUT", ignoreCase = true)
    val isDelete = method.equals("DELETE", ignoreCase = true)
    
    val bgColor = when {
        isPost -> Color(0xFF9C27B0).copy(alpha = 0.15f)
        isPut -> Color(0xFFFF9800).copy(alpha = 0.15f)
        isDelete -> Color(0xFFF44336).copy(alpha = 0.15f)
        else -> Color(0xFF2196F3).copy(alpha = 0.15f)
    }
    
    val textColor = when {
        isPost -> Color(0xFF9C27B0)
        isPut -> Color(0xFFFF9800)
        isDelete -> Color(0xFFF44336)
        else -> Color(0xFF2196F3)
    }
    
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = method.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * HTTP状态码标签
 */
@Composable
private fun StatusBadge(responseCode: Int, errorMsg: String?) {
    val (color, text) = when {
        errorMsg != null -> Color(0xFFF44336) to "错误"
        responseCode in 200..299 -> Color(0xFF4CAF50) to "$responseCode"
        responseCode in 400..499 -> Color(0xFFFF9800) to "$responseCode"
        responseCode in 500..599 -> Color(0xFFF44336) to "$responseCode"
        else -> Color(0xFF9E9E9E) to "$responseCode"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = color
    )
}
