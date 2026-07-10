package io.legado.app.ui.book.readRecord.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.legado.app.utils.formatReadDuration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.*

enum class HeatmapMode {
    COUNT,
    TIME
}

const val HEATMAP_CALENDAR_TITLE = "阅读日历"
private const val HEATMAP_COUNT_BASELINE = 6
private const val HEATMAP_TIME_BASELINE_MINUTES = 120

@Composable
fun HeatmapCalendarStartAction(
    currentMode: HeatmapMode,
    onModeChanged: (HeatmapMode) -> Unit
) {
    Row {
        FilterChip(
            selected = currentMode == HeatmapMode.COUNT,
            onClick = { onModeChanged(HeatmapMode.COUNT) },
            label = { Text("次数") },
            modifier = Modifier.padding(end = 4.dp)
        )
        FilterChip(
            selected = currentMode == HeatmapMode.TIME,
            onClick = { onModeChanged(HeatmapMode.TIME) },
            label = { Text("时长") }
        )
    }
}

@Composable
fun HeatmapCalendarEndAction(
    onClearDate: () -> Unit
) {
    TextButton(onClick = onClearDate) {
        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.width(4.dp))
        Text("清除筛选", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun HeatmapCalendarSection(
    dailyReadCounts: Map<LocalDate, Int>,
    dailyReadTimes: Map<LocalDate, Long>,
    currentMode: HeatmapMode,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate?) -> Unit
) {
    val today = LocalDate.now()
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }

    val maxValue = remember(dailyReadCounts, dailyReadTimes, currentMode) {
        if (currentMode == HeatmapMode.COUNT) {
            (dailyReadCounts.values.maxOrNull() ?: 1).coerceAtLeast(HEATMAP_COUNT_BASELINE)
        } else {
            val maxTime = dailyReadTimes.values.maxOrNull() ?: 1L
            ((maxTime / 60000).toInt()).coerceAtLeast(HEATMAP_TIME_BASELINE_MINUTES)
        }
    }

    val monthDays = remember(currentYearMonth) {
        (1..currentYearMonth.lengthOfMonth()).map { currentYearMonth.atDay(it) }
    }
    val monthReadCount = remember(monthDays, dailyReadCounts) {
        monthDays.sumOf { dailyReadCounts[it] ?: 0 }
    }
    val monthReadTime = remember(monthDays, dailyReadTimes) {
        monthDays.sumOf { dailyReadTimes[it] ?: 0L }
    }
    val activeDays = remember(monthDays, dailyReadCounts, dailyReadTimes) {
        monthDays.count { (dailyReadCounts[it] ?: 0) > 0 || (dailyReadTimes[it] ?: 0L) > 0L }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { currentYearMonth = currentYearMonth.minusMonths(1) }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${currentYearMonth.year}年${currentYearMonth.monthValue}月",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (currentMode == HeatmapMode.COUNT) "按阅读次数显示" else "按阅读时长显示",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { currentYearMonth = currentYearMonth.plusMonths(1) }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MonthStatPill(
                        label = "阅读",
                        value = "${monthReadCount}次",
                        modifier = Modifier.weight(1f)
                    )
                    MonthStatPill(
                        label = "时长",
                        value = formatReadDuration(monthReadTime),
                        modifier = Modifier.weight(1f)
                    )
                    MonthStatPill(
                        label = "天数",
                        value = "${activeDays}天",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        MonthCalendarGrid(
            yearMonth = currentYearMonth,
            dailyReadCounts = dailyReadCounts,
            dailyReadTimes = dailyReadTimes,
            mode = currentMode,
            maxValue = maxValue,
            selectedDate = selectedDate,
            today = today,
            onDateSelected = onDateSelected
        )

        HeatmapLegend()

        if (selectedDate != null) {
            SelectedDateSummary(
                date = selectedDate,
                readCount = dailyReadCounts[selectedDate] ?: 0,
                readTime = dailyReadTimes[selectedDate] ?: 0L,
                onClearDate = { onDateSelected(null) }
            )
        }
    }
}

@Composable
fun MonthCalendarGrid(
    yearMonth: YearMonth,
    dailyReadCounts: Map<LocalDate, Int>,
    dailyReadTimes: Map<LocalDate, Long>,
    mode: HeatmapMode,
    maxValue: Int,
    selectedDate: LocalDate?,
    today: LocalDate,
    onDateSelected: (LocalDate?) -> Unit
) {
    val firstDayOfMonth = yearMonth.atDay(1)
    val lastDayOfMonth = yearMonth.atEndOfMonth()
    val firstDayOfWeek = firstDayOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    
    val days = remember(firstDayOfWeek, lastDayOfMonth) {
        generateSequence(firstDayOfWeek) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDayOfMonth) || it.dayOfWeek != DayOfWeek.SUNDAY }
            .toList()
    }
    
    val weeks = days.chunked(7)
    
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { date ->
                    val isCurrentMonth = date.month == yearMonth.month
                    
                    val value = if (mode == HeatmapMode.COUNT) {
                        dailyReadCounts[date] ?: 0
                    } else {
                        ((dailyReadTimes[date] ?: 0L) / 60000).toInt()
                    }
                    
                    val isSelected = date == selectedDate
                    val isToday = date == today
                    val backgroundColor = heatmapCellColor(
                        value = value,
                        maxValue = maxValue,
                        isCurrentMonth = isCurrentMonth,
                        isSelected = isSelected
                    )
                    val textColor = heatmapTextColor(
                        value = value,
                        maxValue = maxValue,
                        isCurrentMonth = isCurrentMonth,
                        isSelected = isSelected
                    )
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(backgroundColor)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                } else if (isToday) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(enabled = isCurrentMonth) {
                                onDateSelected(if (isSelected) null else date)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeatmapWeekColumn(
    week: List<LocalDate>,
    dailyReadCounts: Map<LocalDate, Int>,
    dailyReadTimes: Map<LocalDate, Long>,
    mode: HeatmapMode,
    maxValue: Int,
    selectedDate: LocalDate?,
    today: LocalDate,
    onDateSelected: (LocalDate?) -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        week.forEach { date ->
            val value = if (mode == HeatmapMode.COUNT) {
                dailyReadCounts[date] ?: 0
            } else {
                ((dailyReadTimes[date] ?: 0L) / 60000).toInt()
            }
            
            val isSelected = date == selectedDate
            val isToday = date == today
            
            val color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                value == 0 -> MaterialTheme.colorScheme.surfaceVariant
                else -> {
                    val intensity = (value.toFloat() / maxValue).coerceIn(0f, 1f)
                    Color(
                        red = onSurfaceColor.red * intensity + surfaceColor.red * (1 - intensity),
                        green = onSurfaceColor.green * intensity + surfaceColor.green * (1 - intensity),
                        blue = onSurfaceColor.blue * intensity + surfaceColor.blue * (1 - intensity)
                    )
                }
            }
            
            Surface(
                modifier = Modifier
                    .size(16.dp)
                    .clickable {
                        onDateSelected(
                            if (isSelected) null else date
                        )
                    },
                shape = RoundedCornerShape(2.dp),
                color = color,
                shadowElevation = 2.dp
            ) {
                if (isToday && !isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(1.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun HeatmapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(4.dp))
        repeat(5) { index ->
            val color = if (index == 0) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
            } else {
                val ratio = index / 4f
                val intensity = ratio * ratio
                lerp(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    MaterialTheme.colorScheme.primary,
                    intensity
                )
            }
            Surface(
                modifier = Modifier
                    .size(14.dp)
                    .padding(1.dp),
                shape = RoundedCornerShape(3.dp),
                color = color
            ) {}
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SelectedDateSummary(
    date: LocalDate,
    readCount: Int,
    readTime: Long,
    onClearDate: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${readCount}次 · ${formatReadDuration(readTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HeatmapCalendarEndAction(onClearDate = onClearDate)
        }
    }
}

@Composable
private fun heatmapCellColor(
    value: Int,
    maxValue: Int,
    isCurrentMonth: Boolean,
    isSelected: Boolean
): Color {
    if (isSelected) {
        return MaterialTheme.colorScheme.primary
    }
    if (!isCurrentMonth) {
        return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    }
    if (value <= 0) {
        return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    }
    val ratio = (value.toFloat() / maxValue).coerceIn(0f, 1f)
    val intensity = ratio * ratio
    return lerp(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        MaterialTheme.colorScheme.primary,
        intensity
    )
}

@Composable
private fun heatmapTextColor(
    value: Int,
    maxValue: Int,
    isCurrentMonth: Boolean,
    isSelected: Boolean
): Color {
    if (isSelected) {
        return MaterialTheme.colorScheme.onPrimary
    }
    if (!isCurrentMonth) {
        return MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }
    if (value <= 0) {
        return MaterialTheme.colorScheme.onSurfaceVariant
    }
    val ratio = (value.toFloat() / maxValue).coerceIn(0f, 1f)
    return if (ratio > 0.72f) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
}

@Composable
fun rememberDateRange(weeksToShow: Int): Pair<LocalDate, LocalDate> {
    val today = LocalDate.now()
    val startDate = today.minusWeeks(weeksToShow.toLong()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return remember(weeksToShow) { startDate to today }
}

@Composable
fun rememberWeeks(startDate: LocalDate, weeksToShow: Int): List<List<LocalDate>> {
    return remember(startDate, weeksToShow) {
        (0 until weeksToShow).map { weekIndex ->
            (0 until 7).map { dayIndex ->
                startDate.plusWeeks(weekIndex.toLong()).plusDays(dayIndex.toLong())
            }
        }
    }
}

@Composable
fun rememberDaysInRange(startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
    return remember(startDate, endDate) {
        generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .toList()
    }
}

@Composable
fun NoEarlierDataIndicator(modifier: Modifier = Modifier) {
    Text(
        text = "无更早数据",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapCalendarBottomSheet(
    dailyReadCounts: Map<LocalDate, Int>,
    dailyReadTimes: Map<LocalDate, Long>,
    currentMode: HeatmapMode,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate?) -> Unit,
    onModeChanged: (HeatmapMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.76f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "阅读日历",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "查看每天的阅读分布",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HeatmapCalendarStartAction(
                        currentMode = currentMode,
                        onModeChanged = onModeChanged
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            HeatmapCalendarSection(
                dailyReadCounts = dailyReadCounts,
                dailyReadTimes = dailyReadTimes,
                currentMode = currentMode,
                selectedDate = selectedDate,
                onDateSelected = { date ->
                    onDateSelected(date)
                }
            )
        }
    }
}

