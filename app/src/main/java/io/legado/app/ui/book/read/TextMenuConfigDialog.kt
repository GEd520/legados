package io.legado.app.ui.book.read

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.theme.pageTopBarBackground
import io.legado.app.ui.theme.pageTopBarColors
import io.legado.app.utils.toastOnUi

/**
 * 文本菜单项配置对话框 - Compose实现
 * 
 * 功能说明：
 * 提供一个界面让用户选择要显示/隐藏的文本菜单项
 */
class TextMenuConfigDialog : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    TextMenuConfigDialogContent(
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }
}

/**
 * 文本菜单配置对话框内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextMenuConfigDialogContent(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val menuItems = remember { TextMenuConfig.getAllMenuItems() }
    var hiddenIds by remember { 
        mutableStateOf(TextMenuConfig.getHiddenMenuItemIds(context))
    }
    var showProcessTextConfig by remember { mutableStateOf(false) }
    
    val topBarColors = pageTopBarColors()
    val cardColor = pageCardContainerColor()

    if (showProcessTextConfig) {
        ProcessTextConfigContent(
            onDismiss = { showProcessTextConfig = false }
        )
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                color = cardColor
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        modifier = Modifier.pageTopBarBackground(topBarColors),
                        title = {
                            Text(
                                text = stringResource(R.string.text_menu_config),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "关闭"
                                )
                            }
                        },
                        actions = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                IconButton(onClick = { showProcessTextConfig = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "更多选项"
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = topBarColors.contentColor
                        )
                    )

                    Text(
                        text = stringResource(R.string.text_menu_config_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(menuItems) { item ->
                            MenuItemRow(
                                item = item,
                                isChecked = item.id !in hiddenIds,
                                onCheckedChange = { checked ->
                                    val newHiddenIds = hiddenIds.toMutableSet()
                                    if (checked) {
                                        newHiddenIds.remove(item.id)
                                    } else {
                                        newHiddenIds.add(item.id)
                                    }
                                    TextMenuConfig.setHiddenMenuItemIds(context, newHiddenIds)
                                    hiddenIds = newHiddenIds
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                TextMenuConfig.resetToDefault(context)
                                hiddenIds = emptySet()
                                context.toastOnUi("已重置为默认配置")
                            }
                        ) {
                            Text(text = stringResource(R.string.reset_to_default))
                        }

                        TextButton(onClick = onDismiss) {
                            Text(text = stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 其他应用菜单配置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessTextConfigContent(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hiddenItems by remember { 
        mutableStateOf(TextMenuConfig.getHiddenProcessTextItems(context))
    }
    
    val processTextApps = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getProcessTextApps(context)
        } else {
            emptyList()
        }
    }
    
    val topBarColors = pageTopBarColors()
    val cardColor = pageCardContainerColor()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            color = cardColor
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                TopAppBar(
                    modifier = Modifier.pageTopBarBackground(topBarColors),
                    title = {
                        Text(
                            text = stringResource(R.string.process_text_menu_config),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = topBarColors.contentColor
                    )
                )

                Text(
                    text = stringResource(R.string.process_text_menu_config_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (processTextApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_process_text_apps),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(processTextApps) { appInfo ->
                            ProcessTextAppRow(
                                appInfo = appInfo,
                                isChecked = appInfo.key !in hiddenItems,
                                onCheckedChange = { checked ->
                                    val newHiddenItems = hiddenItems.toMutableSet()
                                    if (checked) {
                                        newHiddenItems.remove(appInfo.key)
                                    } else {
                                        newHiddenItems.add(appInfo.key)
                                    }
                                    TextMenuConfig.setHiddenProcessTextItems(context, newHiddenItems)
                                    hiddenItems = newHiddenItems
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            TextMenuConfig.resetProcessTextConfig(context)
                            hiddenItems = emptySet()
                            context.toastOnUi("已重置为默认配置")
                        }
                    ) {
                        Text(text = stringResource(R.string.reset_to_default))
                    }

                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

/**
 * 其他应用信息
 */
data class ProcessTextAppInfo(
    val key: String,
    val label: String,
    val packageName: String,
    val className: String
)

/**
 * 获取能处理 ACTION_PROCESS_TEXT 的应用列表
 */
@Suppress("DEPRECATION")
private fun getProcessTextApps(context: Context): List<ProcessTextAppInfo> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        return emptyList()
    }
    
    val intent = Intent()
        .setAction(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
    
    return try {
        val resolveInfoList = context.packageManager.queryIntentActivities(intent, 0)
        resolveInfoList.map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val className = resolveInfo.activityInfo.name
            ProcessTextAppInfo(
                key = TextMenuConfig.getProcessTextItemKey(packageName, className),
                label = resolveInfo.loadLabel(context.packageManager).toString(),
                packageName = packageName,
                className = className
            )
        }.sortedBy { it.label }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 菜单项行
 */
@Composable
fun MenuItemRow(
    item: TextMenuConfig.MenuItemInfo,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = stringResource(item.nameResId),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "ID: ${item.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 其他应用菜单项行
 */
@Composable
fun ProcessTextAppRow(
    appInfo: ProcessTextAppInfo,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = appInfo.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = appInfo.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}
