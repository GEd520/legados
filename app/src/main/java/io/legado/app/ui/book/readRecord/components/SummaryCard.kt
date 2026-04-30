package io.legado.app.ui.book.readRecord.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.ui.book.readRecord.readRecordBookStackSurfaceColor
import io.legado.app.ui.book.readRecord.readRecordMutedIconTint
import io.legado.app.ui.book.readRecord.readRecordSecondaryTextColor
import io.legado.app.ui.book.readRecord.readRecordSummaryCardContainerColor

@Composable
fun SummaryCard(
    totalReadTime: Long,
    bookCount: Int,
    latestRecords: List<ReadRecord>
) {
    val hours = totalReadTime / (1000 * 60 * 60)
    val minutes = (totalReadTime / (1000 * 60)) % 60
    val timeString = if (hours > 0) "${hours}小时${minutes}分钟" else "${minutes}分钟"
    val shape = RoundedCornerShape(16.dp)
    val cardColor = readRecordSummaryCardContainerColor()
    val secondaryTextColor = readRecordSecondaryTextColor()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(8.dp, shape, clip = false),
        shape = shape,
        color = cardColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "累计阅读成就",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "已读 ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$bookCount",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " 本书",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "共阅读 $timeString",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }

            if (latestRecords.isNotEmpty()) {
                BookStackView(
                    coverPaths = latestRecords.take(3).map { null }
                )
            }
        }
    }
}

@Composable
private fun BookStackView(coverPaths: List<String?>) {
    val xOffsetStep = 12.dp
    val stackWidth = 48.dp + (xOffsetStep * (coverPaths.size - 1).coerceAtLeast(0))
    val stackSurfaceColor = readRecordBookStackSurfaceColor()
    val iconTint = readRecordMutedIconTint()

    Box(
        modifier = Modifier
            .width(stackWidth)
            .height(72.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        coverPaths.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .padding(start = xOffsetStep * index)
                    .zIndex(index.toFloat())
                    .rotate(if (index % 2 == 0) 3f else -3f)
            ) {
                Surface(
                    shadowElevation = 4.dp,
                    shape = RoundedCornerShape(4.dp),
                    color = stackSurfaceColor
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.width(24.dp).height(24.dp)
                        )
                    }
                }
            }
        }
    }
}
