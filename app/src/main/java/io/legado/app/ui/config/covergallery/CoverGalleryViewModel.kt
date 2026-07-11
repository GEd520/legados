package io.legado.app.ui.config.covergallery

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.CoverGalleryGroupWithImages
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.help.AppWebDav
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CoverGalleryViewModel : ViewModel() {

    private val repository = CoverGalleryRepository()
    private val searchQuery = MutableStateFlow("")
    private val _messageDialog = MutableStateFlow<CoverGalleryMessageDialog?>(null)
    private val uploadingGroupIds = hashSetOf<Long>()

    val groups: StateFlow<List<CoverGalleryGroupWithImages>> = searchQuery
        .flatMapLatest { repository.flowGroupsWithImages(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val messageDialog: StateFlow<CoverGalleryMessageDialog?> = _messageDialog.asStateFlow()

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun addGroup(name: String) {
        val realName = name.trim()
        if (realName.isBlank()) return
        viewModelScope.launch {
            repository.addGroup(realName)
        }
    }

    fun renameGroup(groupId: Long, name: String) {
        val realName = name.trim()
        if (realName.isBlank()) return
        viewModelScope.launch {
            repository.renameGroup(groupId, realName)
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
        }
    }

    fun addImage(context: Context, groupId: Long, uri: Uri) {
        viewModelScope.launch {
            repository.addImage(context.applicationContext, groupId, uri)
        }
    }

    fun addImages(context: Context, groupId: Long, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.addImages(context.applicationContext, groupId, uris)
            }.onSuccess { result ->
                val details = buildList {
                    add("成功添加 ${result.addedCount} 张")
                    if (result.skippedCount > 0) add("跳过重复 ${result.skippedCount} 张")
                    if (result.failedCount > 0) add("失败 ${result.failedCount} 张")
                }
                _messageDialog.value = CoverGalleryMessageDialog(
                    title = if (result.failedCount == 0) "添加完成" else "部分图片添加失败",
                    message = details.joinToString("，")
                )
            }.onFailure {
                _messageDialog.value = CoverGalleryMessageDialog(
                    title = "添加失败",
                    message = it.localizedMessage ?: "无法添加所选图片"
                )
            }
        }
    }

    fun exportGroupZip(
        context: Context,
        groupWithImages: CoverGalleryGroupWithImages,
        onZipReady: (File) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                repository.exportGroupZip(context.applicationContext, groupWithImages)
            }.onSuccess {
                onZipReady(it)
            }.onFailure {
                onFailure(it.localizedMessage ?: "导出zip失败")
            }
        }
    }

    fun uploadGroupZip(
        context: Context,
        groupWithImages: CoverGalleryGroupWithImages,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            val groupId = groupWithImages.group.id
            if (!uploadingGroupIds.add(groupId)) {
                onFailure("正在上传")
                return@launch
            }
            var zipFile: File? = null
            try {
                val exportedZip = repository.exportGroupZip(context.applicationContext, groupWithImages)
                zipFile = exportedZip
                AppWebDav.uploadCoverGalleryPackage(
                    "${groupWithImages.group.id}-${groupWithImages.group.name}",
                    exportedZip
                )
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "上传 WebDav 失败")
            } finally {
                zipFile?.delete()
                uploadingGroupIds.remove(groupId)
            }
        }
    }

    fun importZip(
        context: Context,
        uri: Uri,
        onNoImage: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                repository.importZip(context.applicationContext, uri)
            }.onSuccess {
                _messageDialog.value = CoverGalleryMessageDialog(
                    title = "导入成功",
                    message = "已导入“${it.groupName}”，共 ${it.imageCount} 张图片"
                )
            }.onFailure {
                val message = it.localizedMessage ?: "导入zip失败"
                if (it is CoverGalleryRepository.NoCoverGalleryImageException) {
                    onNoImage(message)
                } else {
                    _messageDialog.value = CoverGalleryMessageDialog(
                        title = "导入失败",
                        message = message
                    )
                }
            }
        }
    }

    fun dismissMessageDialog() {
        _messageDialog.value = null
    }

    fun deleteImage(imageId: Long) {
        viewModelScope.launch {
            repository.deleteImage(imageId)
        }
    }

    fun setDefaultGroup(groupId: Long) {
        viewModelScope.launch {
            repository.setDefaultGroup(groupId)
        }
    }

    fun unsetDefaultGroup(groupId: Long) {
        viewModelScope.launch {
            repository.unsetDefaultGroup(groupId)
        }
    }

    fun rerandomizeGroup(groupId: Long) {
        viewModelScope.launch {
            repository.rerandomizeGroup(groupId)
        }
    }

    data class CoverGalleryMessageDialog(
        val title: String,
        val message: String
    )
}
