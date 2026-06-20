package io.legado.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.CoverGalleryGroup
import io.legado.app.data.entities.CoverGalleryGroupWithImages
import io.legado.app.data.entities.CoverGalleryImage
import io.legado.app.help.CacheManager
import io.legado.app.help.config.AppConfig
import io.legado.app.model.BookCover
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.createFolderIfNotExist
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefString
import io.legado.app.utils.inputStream
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class CoverGalleryRepository {

    private val dao = appDb.coverGalleryDao

    fun flowGroupsWithImages(query: String) = if (query.isBlank()) {
        dao.flowGroupsWithImages()
    } else {
        dao.flowGroupsWithImages(query)
    }

    fun flowGroupWithImages(groupId: Long) = dao.flowGroupWithImages(groupId)

    fun allGroupsWithImages(): List<CoverGalleryGroupWithImages> {
        return dao.getAllGroupsWithImages()
    }

    fun getGroupName(groupId: Long?): String? {
        groupId ?: return null
        return dao.getGroupWithImagesNow(groupId)?.group?.name
    }

    suspend fun addGroup(name: String): Long {
        val order = (dao.getMaxGroupOrder() ?: -1) + 1
        return dao.insertGroup(
            CoverGalleryGroup(
                name = name.trim(),
                order = order
            )
        )
    }

    suspend fun renameGroup(groupId: Long, name: String) {
        val group = dao.getGroup(groupId) ?: return
        dao.updateGroup(
            group.copy(
                name = name.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
        refreshDefaultCover()
    }

    suspend fun deleteGroup(groupId: Long) {
        dao.deleteGroup(groupId)
        clearGroupState(groupId)
        refreshDefaultCover()
    }

    suspend fun addImage(context: Context, groupId: Long, uri: Uri) {
        val path = copyImageToCovers(context, uri)
        val order = (dao.getMaxImageOrder(groupId) ?: -1) + 1
        dao.insertImage(
            CoverGalleryImage(
                groupId = groupId,
                path = path,
                order = order
            )
        )
        refreshDefaultCover()
    }

    suspend fun deleteImage(imageId: Long) {
        dao.deleteImage(imageId)
        refreshDefaultCover()
    }

    suspend fun setDefaultGroup(groupId: Long) {
        dao.setDefaultGroup(groupId)
        refreshDefaultCover()
    }

    suspend fun rerandomizeGroup(groupId: Long) {
        CacheManager.put(randomSeedKeyPrefix + groupId, System.currentTimeMillis())
        CacheManager.delete(sequenceKeyPrefix + groupId)
        refreshDefaultCover()
    }

    suspend fun unsetDefaultGroup(groupId: Long) {
        dao.unmarkDefaultGroup(groupId, System.currentTimeMillis())
        refreshDefaultCover()
    }

    suspend fun exportGroupZip(
        context: Context,
        groupWithImages: CoverGalleryGroupWithImages
    ): File = withContext(IO) {
        val group = groupWithImages.group
        val fileName = "${group.name.normalizeFileName().ifBlank { "封面图集" }}.zip"
        val exportDir = context.cacheDir.getFile("coverGalleryExport").createFolderIfNotExist()
        val zipFile = FileUtils.createFileWithReplace(File(exportDir, fileName).absolutePath)
        val usedEntryNames = hashSetOf<String>()
        var imageCount = 0
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOutputStream ->
            groupWithImages.images
                .sortedWith(compareBy({ it.order }, { it.id }))
                .map { File(it.path) }
                .filter { it.exists() && it.isFile && it.isCoverGalleryImageFile() }
                .distinctBy { it.absolutePath }
                .forEach { imageFile ->
                    val entryName = uniqueFileName(imageFile.name, usedEntryNames)
                    zipOutputStream.putNextEntry(ZipEntry(entryName))
                    imageFile.inputStream().use { it.copyTo(zipOutputStream) }
                    zipOutputStream.closeEntry()
                    imageCount++
                }
        }
        if (imageCount == 0) {
            zipFile.delete()
            throw NoCoverGalleryImageException("空分组不能导出")
        }
        zipFile
    }

    suspend fun importZip(context: Context, uri: Uri): ZipImportResult = withContext(IO) {
        val sourceName = DocumentFile.fromSingleUri(context, uri)?.name.orEmpty()
        val groupName = sourceName
            .substringBeforeLast('.', sourceName)
            .trim()
            .ifBlank { "封面图集" }
        val targetDir = context.externalFiles.getFile("covers").createFolderIfNotExist()
        val usedImageNames = targetDir.listFiles()
            ?.mapTo(hashSetOf()) { it.name }
            ?: hashSetOf()
        val imagePaths = arrayListOf<String>()

        uri.inputStream(context).getOrThrow().use { inputStream ->
            ZipInputStream(inputStream).use { zipInputStream ->
                var entry = zipInputStream.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    val fileName = entryName
                        .substringAfterLast('/')
                        .substringAfterLast('\\')
                        .normalizeFileName()
                    if (!entry.isDirectory && fileName.isCoverGalleryImageFileName()) {
                        val targetFile = File(targetDir, uniqueFileName(fileName, usedImageNames))
                        FileOutputStream(targetFile).use { zipInputStream.copyTo(it) }
                        imagePaths.add(targetFile.absolutePath)
                    }
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
        }

        if (imagePaths.isEmpty()) {
            throw NoCoverGalleryImageException("zip包里没有可导入的图片")
        }

        val groupId = addGroup(groupName)
        val images = imagePaths.mapIndexed { index, path ->
            CoverGalleryImage(
                groupId = groupId,
                path = path,
                order = index
            )
        }
        dao.insertImages(*images.toTypedArray())
        refreshDefaultCover()
        ZipImportResult(groupName, images.size)
    }

    fun getDefaultCoverPath(
        identity: String? = null,
        originalCoverPath: String? = null
    ): String? {
        val selected = getSelectedGroupWithImages(originalCoverPath)
        val groupWithImages = selected ?: dao.getDefaultGroupWithImages() ?: return null
        val images = groupWithImages.images
            .filter { it.path.isNotBlank() }
            .sortedWith(compareBy({ it.order }, { it.id }))
        if (images.isEmpty()) return null
        val key = identity?.takeIf { it.isNotBlank() } ?: "default"
        val index = if (selected != null && selectedMode() == MODE_SEQUENCE) {
            sequentialIndex(groupWithImages.group.id, key, images.size)
        } else {
            val randomSeed = CacheManager.getLong(randomSeedKeyPrefix + groupWithImages.group.id) ?: 0L
            stableIndex(
                key = "${groupWithImages.group.id}:$randomSeed:$key",
                size = images.size
            )
        }
        return images[index].path
    }

    fun setSelectedGroup(isNight: Boolean, groupId: Long?) {
        val key = if (isNight) PreferKey.coverCollectionNight else PreferKey.coverCollectionDay
        appCtx.putPrefString(key, groupId?.toString().orEmpty())
    }

    private fun getSelectedGroupWithImages(originalCoverPath: String?): CoverGalleryGroupWithImages? {
        val isNight = AppConfig.isNightTheme
        if (selectedMode(isNight) == MODE_MIXED && originalCoverPath.isRealCoverPath()) {
            return null
        }
        val groupId = appCtx.getPrefString(
            if (isNight) PreferKey.coverCollectionNight else PreferKey.coverCollectionDay
        )?.toLongOrNull() ?: return null
        return dao.getGroupWithImagesNow(groupId)
    }

    private fun selectedMode(isNight: Boolean = AppConfig.isNightTheme): String {
        return appCtx.getPrefString(
            if (isNight) PreferKey.coverCollectionModeNight else PreferKey.coverCollectionModeDay,
            MODE_RANDOM
        ) ?: MODE_RANDOM
    }

    private fun sequentialIndex(groupId: Long, key: String, size: Int): Int {
        if (size <= 1) return 0
        val cacheKey = "$sequenceKeyPrefix$groupId"
        val assignments = CacheManager.get(cacheKey)
            ?.lineSequence()
            ?.mapIndexedNotNull { index, line ->
                val value = line.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                val savedIndex = value.substringAfter('\t', "").toIntOrNull()
                val savedKey = value.substringBefore('\t')
                savedKey to (savedIndex ?: index)
            }
            ?.toMutableList()
            ?: mutableListOf()
        assignments.firstOrNull { it.first == key }?.let {
            return Math.floorMod(it.second, size)
        }
        val nextIndex = (assignments.maxOfOrNull { it.second } ?: -1) + 1
        assignments.add(key to nextIndex)
        val bounded = assignments.takeLast(MAX_SEQUENCE_ASSIGNMENTS)
        CacheManager.put(cacheKey, bounded.joinToString("\n") { "${it.first}\t${it.second}" })
        return Math.floorMod(nextIndex, size)
    }

    private fun clearGroupState(groupId: Long) {
        CacheManager.delete(randomSeedKeyPrefix + groupId)
        CacheManager.delete(sequenceKeyPrefix + groupId)
    }

    private fun String?.isRealCoverPath(): Boolean {
        val value = this?.trim().orEmpty()
        if (value.isBlank() || value.equals("use_default_cover", ignoreCase = true)) {
            return false
        }
        val lowerValue = value.lowercase()
        return when {
            lowerValue.startsWith("http://") ||
                lowerValue.startsWith("https://") ||
                lowerValue.startsWith("content://") ||
                lowerValue.startsWith("android.resource://") ||
                lowerValue.startsWith("file:///android_asset/") -> true
            lowerValue.startsWith("file://") -> runCatching {
                File(Uri.parse(value).path.orEmpty()).isFile
            }.getOrDefault(false)
            File(value).isAbsolute -> File(value).isFile
            else -> true
        }
    }

    private fun stableIndex(key: String, size: Int): Int {
        if (size <= 1) return 0
        var hash = 1125899906842597L
        key.forEach {
            hash = 31 * hash + it.code
        }
        return Math.floorMod(hash, size)
    }

    private fun copyImageToCovers(context: Context, uri: Uri): String {
        var file = context.externalFiles
        val sourceName = DocumentFile.fromSingleUri(context, uri)?.name.orEmpty()
        val suffix = if (sourceName.contains(".9.png", true)) {
            ".9.png"
        } else {
            "." + sourceName.substringAfterLast(".", "jpg")
        }
        val fileName = uri.inputStream(context).getOrThrow().use {
            MD5Utils.md5Encode(it) + suffix
        }
        file = FileUtils.createFileIfNotExist(file, "covers", fileName)
        uri.inputStream(context).getOrThrow().use { inputStream ->
            FileOutputStream(file).use {
                inputStream.copyTo(it)
            }
        }
        return file.absolutePath
    }

    private fun File.isCoverGalleryImageFile(): Boolean {
        return name.isCoverGalleryImageFileName()
    }

    private fun String.isCoverGalleryImageFileName(): Boolean {
        return substringAfterLast('.', "").lowercase() in imageExtensions
    }

    private fun uniqueFileName(
        fileName: String,
        usedNames: MutableSet<String>
    ): String {
        val fallbackName = "image.jpg"
        val normalizedName = fileName.ifBlank { fallbackName }.normalizeFileName().ifBlank { fallbackName }
        val nameWithoutExtension = normalizedName.substringBeforeLast('.', normalizedName)
        val extension = normalizedName.substringAfterLast('.', "")
        var candidate = normalizedName
        var suffix = 2
        while (!usedNames.add(candidate)) {
            candidate = if (extension.isBlank()) {
                "$nameWithoutExtension-$suffix"
            } else {
                "$nameWithoutExtension-$suffix.$extension"
            }
            suffix++
        }
        return candidate
    }

    private fun refreshDefaultCover() {
        BookCover.upDefaultCover()
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

    data class ZipImportResult(
        val groupName: String,
        val imageCount: Int
    )

    class NoCoverGalleryImageException(message: String) : IllegalArgumentException(message)

    companion object {
        const val MODE_RANDOM = "random"
        const val MODE_SEQUENCE = "sequence"
        const val MODE_MIXED = "mixed"
        const val backupDirName = "封面图集"
        const val randomSeedKeyPrefix = "coverGalleryRandomSeed:"
        const val sequenceKeyPrefix = "coverGallerySequence:"
        private const val MAX_SEQUENCE_ASSIGNMENTS = 5000
        private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    }
}
