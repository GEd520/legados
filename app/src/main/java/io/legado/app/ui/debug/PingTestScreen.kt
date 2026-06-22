package io.legado.app.ui.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.theme.pageTopBarBackground
import io.legado.app.ui.theme.pageTopBarColors
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

data class PingResult(
    val seq: Int,
    val success: Boolean,
    val time: Long = 0,
    val error: String? = null
)

data class PingStats(
    val sent: Int,
    val received: Int,
    val lost: Int,
    val lossRate: Float,
    val minTime: Long,
    val maxTime: Long,
    val avgTime: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingTestScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val topBarColors = pageTopBarColors()
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    
    var host by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("4") }
    var timeout by remember { mutableStateOf("3000") }
    var isPinging by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0) }
    var pingResults by remember { mutableStateOf<List<PingResult>>(emptyList()) }
    var pingStats by remember { mutableStateOf<PingStats?>(null) }
    var pingOutput by remember { mutableStateOf("") }
    var resolvedIp by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                modifier = Modifier.pageTopBarBackground(topBarColors),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = topBarColors.contentColor,
                    titleContentColor = topBarColors.contentColor,
                    actionIconContentColor = topBarColors.contentColor
                ),
                title = {
                    Text(
                        text = stringResource(R.string.debug_ping_test),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val clipText = clipboardManager.getText()?.text ?: ""
                        if (clipText.isNotBlank()) {
                            host = clipText.trim()
                        } else {
                            context.toastOnUi(R.string.debug_clipboard_empty)
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "粘贴")
                    }
                    IconButton(onClick = {
                        host = ""
                        pingResults = emptyList()
                        pingStats = null
                        pingOutput = ""
                        resolvedIp = ""
                        currentProgress = 0
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "清空")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.debug_ping_host),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.debug_ping_host_hint)) },
                        singleLine = true,
                        trailingIcon = {
                            if (host.isNotEmpty()) {
                                IconButton(onClick = { host = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清除", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = count,
                            onValueChange = { count = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.debug_ping_count)) },
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = timeout,
                            onValueChange = { timeout = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.debug_ping_timeout)) },
                            singleLine = true
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            if (host.isBlank()) {
                                context.toastOnUi(R.string.debug_ping_host_empty)
                                return@Button
                            }
                            
                            val pingCount = count.toIntOrNull() ?: 4
                            val pingTimeout = timeout.toIntOrNull() ?: 3000
                            
                            if (pingCount < 1 || pingCount > 100) {
                                context.toastOnUi(R.string.debug_ping_count_invalid)
                                return@Button
                            }
                            
                            isPinging = true
                            pingResults = emptyList()
                            pingStats = null
                            pingOutput = ""
                            resolvedIp = ""
                            currentProgress = 0
                            
                            val strResolveFailed = context.getString(R.string.debug_ping_resolve_failed)
                            val strReplyFrom = context.getString(R.string.debug_ping_reply_from)
                            val strTimeoutMsg = context.getString(R.string.debug_ping_timeout_msg)
                            val strStatistics = context.getString(R.string.debug_ping_statistics)
                            val strPackets = context.getString(R.string.debug_ping_packets)
                            val strError = context.getString(R.string.debug_error)
                            
                            coroutineScope.launch {
                                try {
                                    val output = StringBuilder()
                                    val results = mutableListOf<PingResult>()
                                    
                                    output.append("PING $host\n")
                                    
                                    val inetAddress = withContext(Dispatchers.IO) {
                                        try {
                                            InetAddress.getByName(host)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    
                                    if (inetAddress == null) {
                                        output.append("\n$strResolveFailed\n")
                                        pingOutput = output.toString()
                                        isPinging = false
                                        return@launch
                                    }
                                    
                                    resolvedIp = inetAddress.hostAddress ?: ""
                                    output.append("(${inetAddress.hostAddress})\n\n")
                                    
                                    val times = mutableListOf<Long>()
                                    
                                    for (i in 1..pingCount) {
                                        if (!isPinging) break
                                        
                                        currentProgress = i
                                        
                                        val startTime = System.currentTimeMillis()
                                        val result = withContext(Dispatchers.IO) {
                                            try {
                                                val reachable = inetAddress.isReachable(pingTimeout)
                                                val elapsed = System.currentTimeMillis() - startTime
                                                PingResult(
                                                    seq = i,
                                                    success = reachable,
                                                    time = elapsed
                                                )
                                            } catch (e: Exception) {
                                                PingResult(
                                                    seq = i,
                                                    success = false,
                                                    error = e.message
                                                )
                                            }
                                        }
                                        
                                        results.add(result)
                                        pingResults = results.toList()
                                        
                                        if (result.success) {
                                            times.add(result.time)
                                            output.append("${String.format("%3d", i)}: $strReplyFrom ${inetAddress.hostAddress}: time=${result.time}ms\n")
                                        } else {
                                            output.append("${String.format("%3d", i)}: $strTimeoutMsg\n")
                                        }
                                    }
                                    
                                    output.append("\n--- $host $strStatistics ---\n")
                                    
                                    val stats = calculateStats(results, times)
                                    pingStats = stats
                                    
                                    output.append("$strPackets: sent=${stats.sent}, received=${stats.received}, lost=${stats.lost} (${String.format("%.1f", stats.lossRate * 100)}% loss)\n")
                                    
                                    if (times.isNotEmpty()) {
                                        output.append("rtt min/avg/max = ${stats.minTime}/${stats.avgTime}/${stats.maxTime} ms\n")
                                    }
                                    
                                    pingOutput = output.toString()
                                    
                                } catch (e: Exception) {
                                    pingOutput = "$strError: ${e.message}"
                                } finally {
                                    isPinging = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isPinging && host.isNotBlank()
                    ) {
                        if (isPinging) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.debug_ping_running))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.debug_ping_start))
                        }
                    }
                    
                    if (isPinging) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val pingCount = count.toIntOrNull() ?: 4
                        LinearProgressIndicator(
                            progress = { currentProgress.toFloat() / pingCount },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedButton(
                            onClick = { isPinging = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.debug_ping_stop))
                        }
                    }
                }
            }
            
            if (resolvedIp.isNotEmpty()) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.debug_ping_resolved),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { context.sendToClip(resolvedIp) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = resolvedIp,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            if (pingResults.isNotEmpty()) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.debug_ping_results),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { showHistory = !showHistory }) {
                                Text(if (showHistory) stringResource(R.string.debug_ping_hide) else stringResource(R.string.debug_ping_show_all))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (showHistory) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                pingResults.forEach { result ->
                                    PingResultItem(result)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val successCount = pingResults.count { it.success }
                                val failCount = pingResults.count { !it.success }
                                val avgTime = pingResults.filter { it.success }.map { it.time }.average().toLong()
                                
                                StatChip(
                                    icon = Icons.Default.CheckCircle,
                                    label = stringResource(R.string.debug_ping_success),
                                    value = successCount.toString(),
                                    color = Color(0xFF4CAF50)
                                )
                                StatChip(
                                    icon = Icons.Default.Cancel,
                                    label = stringResource(R.string.debug_ping_failed),
                                    value = failCount.toString(),
                                    color = MaterialTheme.colorScheme.error
                                )
                                if (successCount > 0) {
                                    StatChip(
                                        icon = Icons.Default.Speed,
                                        label = stringResource(R.string.debug_ping_avg_time),
                                        value = "${avgTime}ms",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            pingStats?.let { stats ->
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.debug_ping_statistics),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                label = stringResource(R.string.debug_ping_sent),
                                value = stats.sent.toString()
                            )
                            StatItem(
                                label = stringResource(R.string.debug_ping_received),
                                value = stats.received.toString()
                            )
                            StatItem(
                                label = stringResource(R.string.debug_ping_lost),
                                value = stats.lost.toString()
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                label = stringResource(R.string.debug_ping_loss_rate),
                                value = String.format("%.1f%%", stats.lossRate * 100),
                                valueColor = when {
                                    stats.lossRate == 0f -> Color(0xFF4CAF50)
                                    stats.lossRate < 0.3f -> Color(0xFFFF9800)
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                            if (stats.received > 0) {
                                StatItem(
                                    label = stringResource(R.string.debug_ping_avg_time),
                                    value = "${stats.avgTime}ms"
                                )
                            }
                        }
                        
                        if (stats.received > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem(
                                    label = stringResource(R.string.debug_ping_min_time),
                                    value = "${stats.minTime}ms"
                                )
                                StatItem(
                                    label = stringResource(R.string.debug_ping_max_time),
                                    value = "${stats.maxTime}ms"
                                )
                            }
                        }
                    }
                }
            }
            
            if (pingOutput.isNotEmpty()) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.debug_ping_output),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { context.sendToClip(pingOutput) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        SelectionContainer {
                            Text(
                                text = pingOutput,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PingResultItem(result: PingResult) {
    Surface(
        color = if (result.success) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (result.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "#${result.seq}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = if (result.success) "${result.time}ms" else stringResource(R.string.debug_ping_timeout_msg),
                style = MaterialTheme.typography.bodySmall,
                color = if (result.success) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$label: $value",
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun calculateStats(results: List<PingResult>, times: List<Long>): PingStats {
    val sent = results.size
    val received = results.count { it.success }
    val lost = sent - received
    val lossRate = if (sent > 0) lost.toFloat() / sent else 0f
    
    val minTime = times.minOrNull() ?: 0
    val maxTime = times.maxOrNull() ?: 0
    val avgTime = if (times.isNotEmpty()) times.sum() / times.size else 0
    
    return PingStats(
        sent = sent,
        received = received,
        lost = lost,
        lossRate = lossRate,
        minTime = minTime,
        maxTime = maxTime,
        avgTime = avgTime
    )
}
