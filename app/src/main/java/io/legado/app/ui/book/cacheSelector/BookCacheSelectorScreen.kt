package io.legado.app.ui.book.cacheSelector

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.ui.book.cacheSelector.components.BookCacheItemCard
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.theme.pageTopBarBackground
import io.legado.app.ui.theme.pageTopBarColors
import io.legado.app.utils.ConvertUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookCacheSelectorScreen(
    viewModel: BookCacheSelectorViewModel = viewModel(),
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onExportClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val bookItems by viewModel.bookItems.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()
    val totalSelectedSize by viewModel.totalSelectedSize.collectAsState()

    val topBarColors = pageTopBarColors()

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
                        text = "书籍缓存选择",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (viewModel.isAllSelected()) {
                            viewModel.deselectAll()
                        } else {
                            viewModel.selectAll()
                        }
                    }) {
                        Text(
                            text = if (viewModel.isAllSelected()) "取消全选" else "全选",
                            color = topBarColors.contentColor
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = pageCardContainerColor(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier.weight(1f),
                        enabled = selectedCount > 0
                    ) {
                        Text("保存选择")
                    }
                    OutlinedButton(
                        onClick = onExportClick,
                        modifier = Modifier.weight(1f),
                        enabled = selectedCount > 0
                    ) {
                        Text("导出选中")
                    }
                }
            }
        }
    ) { paddingValues ->
        when (uiState) {
            is BookCacheSelectorUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is BookCacheSelectorUiState.Idle -> {
                LazyColumn(
                    modifier = Modifier.padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 汇总条
                    item {
                        SummaryBar(
                            selectedCount = selectedCount,
                            totalSize = totalSelectedSize
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    items(
                        items = bookItems,
                        key = { it.book.bookUrl }
                    ) { item ->
                        BookCacheItemCard(
                            item = item,
                            onToggleSelect = { viewModel.toggleSelect(item.book) }
                        )
                    }

                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            is BookCacheSelectorUiState.Exporting -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = (uiState as BookCacheSelectorUiState.Exporting).message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is BookCacheSelectorUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (uiState as BookCacheSelectorUiState.Error).message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryBar(
    selectedCount: Int,
    totalSize: Long
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已选 ${selectedCount} 本",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "总大小: ${ConvertUtils.formatFileSize(totalSize)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
