package io.legado.app.ui.book.readRecord.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.utils.formatReadDuration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.*

enum class HeatmapMode {
    COUNT,
    TIME
}

const val HEATMAP_CALENDAR_TITLE = "阅读日历"

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
    val weeksToShow = 16
    val startDate = today.minusWeeks(weeksToShow.toLong()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    
    val weeks = remember(startDate, weeksToShow) {
        (0 until weeksToShow).map { weekIndex ->
            (0 until 7).map { dayIndex ->
                startDate.plusWeeks(weekIndex.toLong()).plusDays(dayIndex.toLong())
            }
        }
    }

    val maxValue = remember(dailyReadCounts, dailyReadTimes, currentMode) {
        if (currentMode == HeatmapMode.COUNT) {
            (dailyReadCounts.values.maxOrNull() ?: 1).coerceAtLeast(1)
        } else {
            val maxTime = dailyReadTimes.values.maxOrNull() ?: 1L
            ((maxTime / 60000).toInt()).coerceAtLeast(1)
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Top
        ) {
            items(weeks, key = { week -> week.first().toString() }) { week ->
                HeatmapWeekColumn(
                    week = week,
                    dailyReadCounts = dailyReadCounts,
                    dailyReadTimes = dailyReadTimes,
                    mode = currentMode,
                    maxValue = maxValue,
                    selectedDate = selectedDate,
                    today = today,
                    onDateSelected = onDateSelected
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        HeatmapLegend()
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
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(4.dp))
        repeat(5) { index ->
            val intensity = index / 4f
            val color = Color(
                red = onSurfaceColor.red * intensity + surfaceColor.red * (1 - intensity),
                green = onSurfaceColor.green * intensity + surfaceColor.green * (1 - intensity),
                blue = onSurfaceColor.blue * intensity + surfaceColor.blue * (1 - intensity)
            )
            Surface(
                modifier = Modifier
                    .size(12.dp)
                    .padding(1.dp),
                shape = RoundedCornerShape(2.dp),
                color = color,
                shadowElevation = 2.dp
            ) {}
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("阅读日历", style = MaterialTheme.typography.titleLarge)
                HeatmapCalendarStartAction(
                    currentMode = currentMode,
                    onModeChanged = onModeChanged
                )
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
            
            if (selectedDate != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HeatmapCalendarEndAction {
                    onDateSelected(null)
                }
            }
        }
    }
}

