package io.legado.app.ui.config.covergallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.legado.app.R
import io.legado.app.data.entities.CoverGalleryGroup
import io.legado.app.data.entities.CoverGalleryGroupWithImages
import io.legado.app.data.entities.CoverGalleryImage
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.theme.pageCardElevatedContainerColor
import io.legado.app.ui.theme.pageMutedIconTint
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.theme.pageSurfaceVariantColor
import io.legado.app.ui.theme.pageTopBarContainerColor
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun CoverGalleryScreen(
    viewModel: CoverGalleryViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val messageDialog by viewModel.messageDialog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val topBarColor = pageTopBarContainerColor()
    val elevatedContainerColor = pageCardElevatedContainerColor()
    val secondaryTextColor = pageSecondaryTextColor()

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var editGroup by remember { mutableStateOf<CoverGalleryGroupWithImages?>(null) }
    var deleteGroup by remember { mutableStateOf<CoverGalleryGroupWithImages?>(null) }
    var deleteImage by remember { mutableStateOf<CoverGalleryImage?>(null) }
    var pendingExportZipName by remember { mutableStateOf("") }
    var pendingImageGroupId by remember { mutableLongStateOf(0L) }

    val selectImage = rememberLauncherForActivityResult(HandleFileContract()) {
        val groupId = pendingImageGroupId
        if (groupId != 0L) {
            it.uri?.let { uri ->
                viewModel.addImage(context, groupId, uri)
            }
            pendingImageGroupId = 0L
        }
    }
    val selectImportZip = rememberLauncherForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            viewModel.importZip(
                context,
                uri,
                onNoImage = { message -> context.toastOnUi(message) }
            )
        }
    }
    val exportZip = rememberLauncherForActivityResult(HandleFileContract()) {
        if (it.uri != null) {
            val zipName = pendingExportZipName.ifBlank { "zip" }
            context.toastOnUi("导出成功：$zipName")
        } else {
            context.toastOnUi("导出失败")
        }
        pendingExportZipName = ""
    }

    messageDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMessageDialog() },
            containerColor = elevatedContainerColor,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = secondaryTextColor,
            shape = RoundedCornerShape(0.dp),
            title = { Text(dialog.title) },
            text = { Text(dialog.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissMessageDialog() }) {
                    Text("确定")
                }
            }
        )
    }

    editGroup?.let { groupWithImages ->
        GroupNameDialog(
            initialName = groupWithImages.group.name,
            title = if (groupWithImages.group.id == 0L) "添加分组" else "重命名分组",
            onDismiss = { editGroup = null },
            onConfirm = { name ->
                if (groupWithImages.group.id == 0L) {
                    viewModel.addGroup(name)
                } else {
                    viewModel.renameGroup(groupWithImages.group.id, name)
                }
                editGroup = null
            }
        )
    }

    deleteGroup?.let { groupWithImages ->
        AlertDialog(
            onDismissRequest = { deleteGroup = null },
            containerColor = elevatedContainerColor,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = secondaryTextColor,
            shape = RoundedCornerShape(0.dp),
            title = { Text("删除分组") },
            text = { Text("确定删除“${groupWithImages.group.name}”及其中 ${groupWithImages.images.size} 张图片吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGroup(groupWithImages.group.id)
                        deleteGroup = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroup = null }) {
                    Text("取消")
                }
            }
        )
    }

    deleteImage?.let { image ->
        AlertDialog(
            onDismissRequest = { deleteImage = null },
            containerColor = elevatedContainerColor,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = secondaryTextColor,
            shape = RoundedCornerShape(0.dp),
            title = { Text("删除图片") },
            text = { Text("确定从图集中删除这张图片吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteImage(image.id)
                        deleteImage = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteImage = null }) {
                    Text("取消")
                }
            }
        )
    }

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
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("封面图集") },
                actions = {
                    IconButton(
                        onClick = {
                            selectImportZip.launch {
                                requestCode = 3002
                                mode = HandleFileContract.FILE
                                title = "导入zip"
                                allowExtensions = arrayOf("zip")
                            }
                        }
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "导入zip")
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(
                        onClick = {
                            editGroup = CoverGalleryGroupWithImages(
                                group = CoverGalleryGroup(),
                                images = emptyList()
                            )
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加分组")
                    }
                    IconButton(
                        onClick = {
                            (context as? AppCompatActivity)?.showDialogFragment(
                                TextDialog(
                                    context.getString(R.string.help),
                                    "## 封面图集\n\n" +
                                        "- 支持图片类型：jpg、jpeg、png、webp、gif、bmp、heic、heif\n" +
                                        "- 导入文件类型：zip，zip 中的图片会导入为一个分组。\n" +
                                        "- 导出文件类型：zip，导出内容为当前分组中的图片。",
                                    TextDialog.Mode.MD
                                )
                            )
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_help),
                            contentDescription = stringResource(R.string.help)
                        )
                    }
                }
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
                    onValueChange = {
                        searchQuery = it
                        viewModel.setSearchQuery(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索分组") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    viewModel.setSearchQuery("")
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = elevatedContainerColor,
                        unfocusedContainerColor = elevatedContainerColor,
                        focusedBorderColor = pageAccentColor(),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLeadingIconColor = pageAccentColor(),
                        unfocusedLeadingIconColor = pageMutedIconTint(),
                        focusedTrailingIconColor = pageAccentColor(),
                        unfocusedTrailingIconColor = pageMutedIconTint()
                    ),
                    singleLine = true
                )
            }

            if (groups.isEmpty()) {
                EmptyGallery(
                    modifier = Modifier.fillMaxSize(),
                    onAddGroup = {
                        editGroup = CoverGalleryGroupWithImages(
                            group = CoverGalleryGroup(),
                            images = emptyList()
                        )
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(groups, key = { it.group.id }) { groupWithImages ->
                        CoverGalleryGroupCard(
                            groupWithImages = groupWithImages,
                            onAddImage = {
                                pendingImageGroupId = groupWithImages.group.id
                                selectImage.launch {
                                    requestCode = 3001
                                    mode = HandleFileContract.IMAGE
                                }
                            },
                            onSetDefault = { viewModel.setDefaultGroup(groupWithImages.group.id) },
                            onUnsetDefault = { viewModel.unsetDefaultGroup(groupWithImages.group.id) },
                            onRerandomize = { viewModel.rerandomizeGroup(groupWithImages.group.id) },
                            onExportZip = {
                                if (groupWithImages.images.isEmpty()) {
                                    context.toastOnUi("空分组不能导出")
                                    return@CoverGalleryGroupCard
                                }
                                viewModel.exportGroupZip(
                                    context,
                                    groupWithImages,
                                    onZipReady = { zipFile ->
                                        pendingExportZipName = zipFile.name
                                        exportZip.launch {
                                            requestCode = 3003
                                            mode = HandleFileContract.EXPORT
                                            title = "导出zip"
                                            onlyOtherActions = true
                                            otherActions = arrayListOf(
                                                SelectItem("系统文件选择器", HandleFileContract.DIR),
                                                SelectItem("自带文件选择器", 10)
                                            )
                                            fileData = HandleFileContract.FileData(
                                                zipFile.name,
                                                zipFile,
                                                "application/zip"
                                            )
                                        }
                                    },
                                    onFailure = { message ->
                                        context.toastOnUi("导出失败\n$message")
                                    }
                                )
                            },
                            onUploadZip = {
                                if (groupWithImages.images.isEmpty()) {
                                    context.toastOnUi("空分组不能上传")
                                    return@CoverGalleryGroupCard
                                }
                                viewModel.uploadGroupZip(
                                    context,
                                    groupWithImages,
                                    onSuccess = {
                                        context.toastOnUi("上传 WebDav 成功")
                                    },
                                    onFailure = { message ->
                                        context.toastOnUi("上传 WebDav 失败\n$message")
                                    }
                                )
                            },
                            onRename = { editGroup = groupWithImages },
                            onDeleteGroup = { deleteGroup = groupWithImages },
                            onDeleteImage = { deleteImage = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGallery(
    modifier: Modifier = Modifier,
    onAddGroup: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = pageMutedIconTint()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "暂无封面分组",
                style = MaterialTheme.typography.titleMedium,
                color = pageSecondaryTextColor()
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = onAddGroup) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("添加分组")
            }
        }
    }
}

@Composable
private fun CoverGalleryGroupCard(
    groupWithImages: CoverGalleryGroupWithImages,
    onAddImage: () -> Unit,
    onSetDefault: () -> Unit,
    onUnsetDefault: () -> Unit,
    onRerandomize: () -> Unit,
    onExportZip: () -> Unit,
    onUploadZip: () -> Unit,
    onRename: () -> Unit,
    onDeleteGroup: () -> Unit,
    onDeleteImage: (CoverGalleryImage) -> Unit
) {
    val group = groupWithImages.group
    val images = groupWithImages.images.sortedWith(compareBy({ it.order }, { it.id }))
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = pageCardContainerColor(),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (group.isDefault) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (group.isDefault) pageAccentColor() else pageMutedIconTint()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (group.isDefault) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "默认 · 随机",
                                style = MaterialTheme.typography.labelSmall,
                                color = pageAccentColor()
                            )
                        }
                    }
                    Text(
                        text = "${images.size} 张图片",
                        style = MaterialTheme.typography.bodySmall,
                        color = pageSecondaryTextColor()
                    )
                }
                IconButton(onClick = onAddImage) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = "添加图片",
                        tint = pageAccentColor()
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = pageMutedIconTint()
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = pageCardElevatedContainerColor()
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (group.isDefault) "取消默认" else "设为默认") },
                            onClick = {
                                if (group.isDefault) onUnsetDefault() else onSetDefault()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    if (group.isDefault) Icons.Default.Clear else Icons.Default.Check,
                                    contentDescription = null
                                )
                            }
                        )
                        if (group.isDefault) {
                            DropdownMenuItem(
                                text = { Text("重新随机") },
                                onClick = {
                                    onRerandomize()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Casino, contentDescription = null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("导出为zip") },
                            onClick = {
                                onExportZip()
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("上传 WebDav") },
                            onClick = {
                                onUploadZip()
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                onRename()
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("删除分组", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                onDeleteGroup()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (images.isEmpty()) {
                FilledTonalButton(onClick = onAddImage) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("添加图片")
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images, key = { it.id }) { image ->
                        CoverImageThumb(
                            image = image,
                            onDelete = { onDeleteImage(image) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun CoverImageThumb(
    image: CoverGalleryImage,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(104.dp)
            .clip(RoundedCornerShape(6.dp))
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = pageSurfaceVariantColor()
        ) {}
        GlideImage(
            model = image.path,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        ) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "删除图片",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun GroupNameDialog(
    initialName: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pageCardElevatedContainerColor(),
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = pageSecondaryTextColor(),
        shape = RoundedCornerShape(0.dp),
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("分组名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = pageCardElevatedContainerColor(),
                    unfocusedContainerColor = pageCardElevatedContainerColor(),
                    focusedBorderColor = pageAccentColor(),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
