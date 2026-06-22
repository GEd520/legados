package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.pageTopBarBackground
import io.legado.app.ui.theme.pageTopBarColors
import io.legado.app.help.config.CoverHtmlTemplateConfig
import io.legado.app.constant.EventBus
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverHtmlTemplateListScreen(
    onBackClick: () -> Unit,
    onEditTemplate: (CoverHtmlTemplateConfig.Template?) -> Unit
) {
    val context = LocalContext.current
    val topBarColors = pageTopBarColors()
    var templateList by remember { mutableStateOf(CoverHtmlTemplateConfig.templateList.toList()) }
    var selectedId by remember { mutableStateOf(CoverHtmlTemplateConfig.getSelectedTemplate().id) }
    
    val containerColor = coverHtmlCardContainerColor()
    
    fun refreshList() {
        templateList = CoverHtmlTemplateConfig.templateList.toList()
        selectedId = CoverHtmlTemplateConfig.getSelectedTemplate().id
    }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                modifier = Modifier.pageTopBarBackground(topBarColors),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    navigationIconContentColor = topBarColors.contentColor,
                    titleContentColor = topBarColors.contentColor,
                    actionIconContentColor = topBarColors.contentColor
                ),
                title = {
                    Text(
                        text = stringResource(R.string.cover_html_template),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
                actions = {
                    IconButton(onClick = { onEditTemplate(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "新建")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (templateList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无模板",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(templateList, key = { it.id }) { template ->
                    TemplateItem(
                        template = template,
                        isSelected = template.id == selectedId,
                        containerColor = containerColor,
                        onSelect = {
                            CoverHtmlTemplateConfig.setSelectedTemplate(template.id)
                            CoverImageView.clearHtmlCoverCache()
                            postEvent(EventBus.BOOKSHELF_REFRESH, "")
                            selectedId = template.id
                            refreshList()
                        },
                        onEdit = { onEditTemplate(template) },
                        onDelete = {
                            if (templateList.size <= 1) {
                                context.toastOnUi(R.string.cover_html_keep_one_template)
                            } else {
                                CoverHtmlTemplateConfig.deleteTemplateById(template.id)
                                CoverImageView.clearHtmlCoverCache()
                                postEvent(EventBus.BOOKSHELF_REFRESH, "")
                                refreshList()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateItem(
    template: CoverHtmlTemplateConfig.Template,
    isSelected: Boolean,
    containerColor: Color,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name.ifEmpty { "未命名模板" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val previewText = template.htmlCode
                    .replace(Regex("<[^>]*>"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(50)
                Text(
                    text = if (previewText.isNotBlank()) "$previewText..." 
                           else stringResource(R.string.cover_html_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun coverHtmlCardContainerColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val alpha = if (background.luminance() > 0.5f) 0.9f else 0.9f
    return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
}

@Composable
fun coverHtmlTopBarContainerColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val alpha = if (background.luminance() > 0.5f) 0.82f else 0.94f
    return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
}

private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
