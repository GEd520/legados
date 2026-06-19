package io.legado.app.ui.book.readRecord

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.ui.book.readRecord.components.HeatmapCalendarSection
import io.legado.app.ui.book.readRecord.components.HeatmapMode
import io.legado.app.utils.formatReadDuration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadAchievementScreen(
    viewModel: ReadRecordViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val topBarColor = readRecordTopBarContainerColor()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondary
                ),
                title = {
                    Column {
                        Text(
                            text = "累计阅读成就",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = "你的阅读履历",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            AchievementContent(
                state = state,
                onDateSelected = viewModel::setSelectedDate,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun AchievementContent(
    state: ReadRecordUiState,
    onDateSelected: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDates = remember(state.dailyReadTimes, state.dailyReadCounts) {
        (state.dailyReadTimes.keys + state.dailyReadCounts.keys).filter { date ->
            (state.dailyReadTimes[date] ?: 0L) > 0L || (state.dailyReadCounts[date] ?: 0) > 0
        }.toSet()
    }
    val longestStreak = remember(activeDates) { calculateLongestStreak(activeDates) }
    val currentStreak = remember(activeDates) { calculateCurrentStreak(activeDates) }
    val bestReadDay = remember(state.dailyReadTimes) {
        state.dailyReadTimes.maxByOrNull { it.value }
    }
    val level = remember(state.totalReadTime) { calculateAchievementLevel(state.totalReadTime) }
    val milestones = remember(state, longestStreak) { buildMilestones(state, longestStreak) }
    val highlights = remember(state, bestReadDay, longestStreak) {
        buildHighlights(state, bestReadDay, longestStreak)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            AchievementHeroCard(
                level = level,
                totalReadTime = state.totalReadTime,
                bookCount = state.latestRecords.size,
                currentStreak = currentStreak
            )
        }

        item {
            MetricGrid(
                metrics = listOf(
                    AchievementMetric("阅读总时长", formatHeroDuration(state.totalReadTime), Icons.Default.Schedule),
                    AchievementMetric("读过书籍", "${state.latestRecords.size} 本", Icons.Default.MenuBook),
                    AchievementMetric("活跃天数", "${activeDates.size} 天", Icons.Default.CalendarMonth),
                    AchievementMetric("最长连续", "${longestStreak} 天", Icons.Default.LocalFireDepartment)
                )
            )
        }

        item {
            SectionCard(
                title = "阅读热力",
                subtitle = "按日回看阅读节奏"
            ) {
                HeatmapCalendarSection(
                    dailyReadCounts = state.dailyReadCounts,
                    dailyReadTimes = state.dailyReadTimes,
                    currentMode = HeatmapMode.TIME,
                    selectedDate = state.selectedDate,
                    onDateSelected = onDateSelected
                )
            }
        }

        item {
            SectionCard(
                title = "里程碑",
                subtitle = "那些一点点攒出来的进度"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    milestones.forEach { milestone ->
                        MilestoneRow(milestone = milestone)
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "阅读高光",
                subtitle = "最近值得记住的几个点"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    highlights.forEach { highlight ->
                        HighlightRow(highlight = highlight)
                    }
                }
            }
        }

        if (state.readTimeRecords.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "最长陪伴",
                    subtitle = "按累计阅读时长排序"
                )
            }
            items(state.readTimeRecords.take(5), key = { "${it.deviceId}|${it.bookName}|${it.bookAuthor}" }) { record ->
                TopBookRow(record = record)
            }
        }
    }
}

@Composable
private fun AchievementHeroCard(
    level: AchievementLevel,
    totalReadTime: Long,
    bookCount: Int,
    currentStreak: Int
) {
    val shape = RoundedCornerShape(20.dp)
    val progress = level.progressToNext

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = readRecordCardContainerColor(),
        border = readRecordCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "阅读等级",
                        style = MaterialTheme.typography.labelSmall,
                        color = readRecordSecondaryTextColor(),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = level.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = level.caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = readRecordSecondaryTextColor()
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.64f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(46.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Lv.${level.level}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(7.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
                )
                Text(
                    text = level.nextHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = readRecordSecondaryTextColor()
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuietHeroStat(
                        label = "总时长",
                        value = formatHeroDuration(totalReadTime),
                        modifier = Modifier.weight(1f)
                    )
                    QuietHeroStat(
                        label = "书籍",
                        value = "${bookCount}本",
                        modifier = Modifier.weight(1f)
                    )
                    QuietHeroStat(
                        label = "连读",
                        value = "${currentStreak}天",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuietHeroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = readRecordSecondaryTextColor(),
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetricGrid(metrics: List<AchievementMetric>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = readRecordCardContainerColor(),
        border = readRecordCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            metrics.chunked(2).forEach { rowMetrics ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowMetrics.forEach { metric ->
                        CompactMetric(metric = metric, modifier = Modifier.weight(1f))
                    }
                    if (rowMetrics.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMetric(
    metric: AchievementMetric,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        ) {
            Icon(
                imageVector = metric.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(7.dp)
                    .size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = readRecordSecondaryTextColor(),
                maxLines = 1
            )
            Text(
                text = metric.value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetricCard(
    metric: AchievementMetric,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = readRecordCardContainerColor(),
        border = readRecordCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
            ) {
                Icon(
                    imageVector = metric.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = readRecordSecondaryTextColor(),
                    maxLines = 1
                )
                Text(
                    text = metric.value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = readRecordCardContainerColor(),
        border = readRecordCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(title = title, subtitle = subtitle)
            content()
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = readRecordSecondaryTextColor()
        )
    }
}

@Composable
private fun MilestoneRow(milestone: Milestone) {
    val unlockedColor = MaterialTheme.colorScheme.primary
    val lockedColor = readRecordSecondaryTextColor()
    val progress = milestone.progress

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (milestone.unlocked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            border = if (milestone.unlocked) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
        ) {
            Icon(
                imageVector = milestone.icon,
                contentDescription = null,
                tint = if (milestone.unlocked) unlockedColor else lockedColor,
                modifier = Modifier
                    .padding(9.dp)
                    .size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = milestone.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (milestone.unlocked) "已解锁" else "${milestone.current}/${milestone.target}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (milestone.unlocked) unlockedColor else lockedColor
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = if (milestone.unlocked) unlockedColor else MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
            )
        }
    }
}

@Composable
private fun HighlightRow(highlight: Highlight) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        ) {
            Icon(
                imageVector = highlight.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .padding(9.dp)
                    .size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlight.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = highlight.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = readRecordSecondaryTextColor()
            )
        }
    }
}

@Composable
private fun TopBookRow(record: ReadRecord) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = readRecordCardContainerColor(),
        border = readRecordCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.bookName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = record.bookAuthor.ifBlank { "未知作者" },
                    style = MaterialTheme.typography.bodySmall,
                    color = readRecordSecondaryTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatReadDuration(record.readTime),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun calculateAchievementLevel(totalReadTime: Long): AchievementLevel {
    val totalHours = (totalReadTime / 3_600_000L).toInt()
    val level = (totalHours / 10 + 1).coerceAtLeast(1)
    val currentLevelHours = (level - 1) * 10
    val nextLevelHours = level * 10
    val progress = ((totalHours - currentLevelHours).toFloat() / 10f).coerceIn(0f, 1f)
    val title = when {
        level >= 30 -> "书海漫游者"
        level >= 18 -> "长篇旅人"
        level >= 10 -> "沉浸读者"
        level >= 5 -> "稳定读者"
        else -> "新晋读者"
    }
    val caption = when {
        level >= 30 -> "已经把阅读变成了长期秩序"
        level >= 18 -> "你的书页里有很长的路"
        level >= 10 -> "阅读正在形成自己的节奏"
        level >= 5 -> "持续投入，手感很好"
        else -> "每一页都在点亮履历"
    }
    val nextHint = "距离 Lv.${level + 1} 还差 ${(nextLevelHours - totalHours).coerceAtLeast(0)} 小时"
    return AchievementLevel(level, title, caption, progress, nextHint)
}

private fun formatHeroDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0 && minutes > 0 -> "${hours}h${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}分钟"
        else -> "0分钟"
    }
}

private fun calculateLongestStreak(activeDates: Set<LocalDate>): Int {
    if (activeDates.isEmpty()) return 0
    val dates = activeDates.sorted()
    var longest = 1
    var current = 1
    for (index in 1 until dates.size) {
        current = if (dates[index] == dates[index - 1].plusDays(1)) {
            current + 1
        } else {
            1
        }
        longest = maxOf(longest, current)
    }
    return longest
}

private fun calculateCurrentStreak(activeDates: Set<LocalDate>): Int {
    if (activeDates.isEmpty()) return 0
    var date = LocalDate.now()
    if (!activeDates.contains(date)) {
        date = date.minusDays(1)
        if (!activeDates.contains(date)) return 0
    }
    var streak = 0
    while (activeDates.contains(date)) {
        streak++
        date = date.minusDays(1)
    }
    return streak
}

private fun buildMilestones(
    state: ReadRecordUiState,
    longestStreak: Int
): List<Milestone> {
    val totalHours = (state.totalReadTime / 3_600_000L).toInt()
    val bookCount = state.latestRecords.size
    val activeDays = (state.dailyReadTimes.keys + state.dailyReadCounts.keys).count { date ->
        (state.dailyReadTimes[date] ?: 0L) > 0L || (state.dailyReadCounts[date] ?: 0) > 0
    }

    return listOf(
        Milestone("初入书页", Icons.Default.AutoAwesome, current = totalHours, target = 1),
        Milestone("十小时沉浸", Icons.Default.Schedule, current = totalHours, target = 10),
        Milestone("百小时旅程", Icons.Default.MilitaryTech, current = totalHours, target = 100),
        Milestone("十本陪伴", Icons.Default.MenuBook, current = bookCount, target = 10),
        Milestone("月度常客", Icons.Default.CalendarMonth, current = activeDays, target = 30),
        Milestone("七日连读", Icons.Default.LocalFireDepartment, current = longestStreak, target = 7)
    )
}

private fun buildHighlights(
    state: ReadRecordUiState,
    bestReadDay: Map.Entry<LocalDate, Long>?,
    longestStreak: Int
): List<Highlight> {
    val bestBook = state.readTimeRecords.firstOrNull()
    val latestBook = state.latestRecords.firstOrNull()
    val formatter = DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)

    return listOfNotNull(
        bestReadDay?.let {
            Highlight(
                title = "最沉浸的一天",
                subtitle = "${it.key.format(formatter)} 阅读了 ${formatReadDuration(it.value)}",
                icon = Icons.Default.TrendingUp
            )
        },
        bestBook?.let {
            Highlight(
                title = "最长陪伴",
                subtitle = "《${it.bookName}》累计 ${formatReadDuration(it.readTime)}",
                icon = Icons.Default.MenuBook
            )
        },
        latestBook?.let {
            Highlight(
                title = "最近翻开的书",
                subtitle = "《${it.bookName}》",
                icon = Icons.Default.Timeline
            )
        },
        Highlight(
            title = "连续阅读纪录",
            subtitle = "最长连续阅读 ${longestStreak} 天",
            icon = Icons.Default.LocalFireDepartment
        )
    )
}

private data class AchievementLevel(
    val level: Int,
    val title: String,
    val caption: String,
    val progressToNext: Float,
    val nextHint: String
)

private data class AchievementMetric(
    val label: String,
    val value: String,
    val icon: ImageVector
)

private data class Milestone(
    val title: String,
    val icon: ImageVector,
    val current: Int,
    val target: Int
) {
    val unlocked: Boolean = current >= target
    val progress: Float = (current.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

private data class Highlight(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)
