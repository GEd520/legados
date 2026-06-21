package io.legado.app.ui.book.readRecord

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.data.appDb
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.formatReadDuration
import io.legado.app.utils.fullScreen
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.setStatusBarColorAuto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BookReadRecordActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_NAME = "bookName"
        const val EXTRA_BOOK_AUTHOR = "bookAuthor"
    }

    private var bgDrawable: Drawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        loadBackgroundImage()
        enableEdgeToEdge()

        val bookName = intent.getStringExtra(EXTRA_BOOK_NAME).orEmpty()
        val bookAuthor = intent.getStringExtra(EXTRA_BOOK_AUTHOR).orEmpty()

        setContent {
            LegadoTheme {
                BookReadRecordScreen(
                    bgDrawable = bgDrawable,
                    bookName = bookName,
                    bookAuthor = bookAuthor,
                    onBackClick = { finish() }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadBackgroundImage() {
        try {
            val metrics = android.util.DisplayMetrics()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val windowMetrics = windowManager.currentWindowMetrics
                val bounds = windowMetrics.bounds
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
            } else {
                windowManager.defaultDisplay.getMetrics(metrics)
            }
            bgDrawable = ThemeConfig.getBgImage(this, metrics)
        } catch (_: Exception) {
            bgDrawable = null
        }
    }

    private fun initTheme() {
        val theme = ThemeConfig.getTheme()
        when (theme) {
            io.legado.app.constant.Theme.Dark -> {
                setTheme(io.legado.app.R.style.AppTheme_Dark)
            }

            io.legado.app.constant.Theme.Light -> {
                setTheme(io.legado.app.R.style.AppTheme_Light)
            }

            else -> {
                if (ColorUtils.isColorLight(primaryColor)) {
                    setTheme(io.legado.app.R.style.AppTheme_Light)
                } else {
                    setTheme(io.legado.app.R.style.AppTheme_Dark)
                }
            }
        }
    }

    private fun setupSystemBar() {
        fullScreen()
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
        setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, true)
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(ThemeStore.navigationBarColor(this))
        } else {
            val nbColor = ColorUtils.darkenColor(ThemeStore.navigationBarColor(this))
            setNavigationBarColorAuto(nbColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReadRecordScreen(
    bgDrawable: Drawable?,
    bookName: String,
    bookAuthor: String,
    onBackClick: () -> Unit
) {
    val repository = remember { ReadRecordRepository(appDb.readRecordDao) }

    val rawSessions = repository.getBookSessions(bookName, bookAuthor)
        .collectAsStateWithLifecycle(emptyList())
        .value

    val dayTotalDurationMap = remember(rawSessions) {
        rawSessions
            .groupBy { formatRecordDate(it.startTime) }
            .mapValues { (_, sessions) -> sessions.sumOf { (it.endTime - it.startTime).coerceAtLeast(0L) } }
    }
    data class MergedSession(val session: ReadRecordSession, val actualDuration: Long)

    val (timelineDays, sessionDurationMap) = remember(rawSessions) {
        val merged = rawSessions
            .sortedBy { it.startTime }
            .fold(mutableListOf<MergedSession>()) { list, session ->
                val sessionDuration = (session.endTime - session.startTime).coerceAtLeast(0L)
                if (list.isEmpty()) {
                    list.add(MergedSession(session, sessionDuration))
                } else {
                    val lastMs = list.last()
                    val last = lastMs.session
                    if (session.startTime - last.endTime <= 60_000L) {
                        list[list.lastIndex] = MergedSession(
                            session = last.copy(
                                endTime = maxOf(last.endTime, session.endTime),
                                words = last.words + session.words,
                                durChapterTitle = session.durChapterTitle.ifBlank { last.durChapterTitle }
                            ),
                            actualDuration = lastMs.actualDuration + sessionDuration
                        )
                    } else {
                        list.add(MergedSession(session, sessionDuration))
                    }
                }
                list
            }
        val durationMap = merged.associate { ms -> ms.session.startTime to ms.actualDuration }
        val days = merged
            .groupBy { formatRecordDate(it.session.startTime) }
            .toSortedMap(compareByDescending { it })
            .map { (date, daySessions) ->
                ReadRecordTimelineDay(
                    date = date,
                    sessions = daySessions.map { it.session }.sortedByDescending { it.startTime }
                )
            }
        Pair(days, durationMap)
    }

    val totalReadTime = repository.getBookReadTime(bookName, bookAuthor)
        .collectAsStateWithLifecycle(0L)
        .value

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = bookName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (timelineDays.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "暂无阅读记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val listState = rememberLazyListState()
                val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            val totalItems = listState.layoutInfo.totalItemsCount
                            if (totalItems > 0) {
                                val visibleItems = listState.layoutInfo.visibleItemsInfo.size
                                val fraction = visibleItems.toFloat() / totalItems.toFloat()
                                val barHeight = size.height * fraction.coerceIn(0.04f, 1f)
                                val progress = listState.firstVisibleItemIndex.toFloat()
                                    .coerceAtMost((totalItems - visibleItems).coerceAtLeast(0).toFloat())
                                val maxScroll = (totalItems - visibleItems).coerceAtLeast(1).toFloat()
                                val barY = (size.height - barHeight) * (progress / maxScroll).coerceIn(0f, 1f)
                                drawRoundRect(
                                    color = scrollbarColor,
                                    topLeft = Offset(size.width - 5.dp.toPx(), barY),
                                    size = Size(3.dp.toPx(), barHeight),
                                    cornerRadius = CornerRadius(1.5.dp.toPx())
                                )
                            }
                        },
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "summary") {
                        SummaryHeader(
                            totalReadTime = totalReadTime,
                            dayCount = timelineDays.size,
                            sessionCount = timelineDays.sumOf { it.sessions.size }
                        )
                    }

                    items(
                        items = timelineDays,
                        key = { it.date }
                    ) { day ->
                        DaySection(day.date, day.sessions, dayTotalDurationMap[day.date] ?: 0L, sessionDurationMap)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(
    totalReadTime: Long,
    dayCount: Int,
    sessionCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatChip(
            icon = Icons.Filled.Timer,
            label = formatReadDuration(totalReadTime),
            suffix = "阅读"
        )
        StatChip(
            label = sessionCount.toString(),
            suffix = "次"
        )
        StatChip(
            label = dayCount.toString(),
            suffix = "天"
        )
    }
}

@Composable
private fun StatChip(
    icon: ImageVector? = null,
    label: String,
    suffix: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = suffix,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DaySection(
    date: String,
    sessions: List<ReadRecordSession>,
    totalDuration: Long,
    sessionDurationMap: Map<Long, Long>
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${sessions.size}次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatReadDuration(totalDuration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sessions.sortedByDescending { it.startTime }.forEach { session ->
                    SessionRow(session, sessionDurationMap[session.startTime] ?: (session.endTime - session.startTime).coerceAtLeast(0L))
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: ReadRecordSession, duration: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatRecordTime(session.startTime),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatReadDuration(duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        session.durChapterTitle.takeIf { it.isNotBlank() }?.let { title ->
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

private fun formatRecordDate(timeMillis: Long): String {
    return Instant.ofEpochMilli(timeMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
}

private fun formatRecordTime(timeMillis: Long): String {
    return Instant.ofEpochMilli(timeMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}
