package io.legado.app.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ComponentCallbacks2
import android.util.Size
import androidx.collection.LruCache
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import io.legado.app.R
import io.legado.app.constant.AppLog.putDebug
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.MemoryPressure
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isEpub
import io.legado.app.help.book.isMobi
import io.legado.app.help.book.isPdf
import io.legado.app.help.config.AppConfig
import io.legado.app.model.localBook.EpubFile
import io.legado.app.model.localBook.MobiFile
import io.legado.app.model.localBook.PdfFile
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.SvgUtils
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

object ImageProvider {

    private val errorBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(appCtx.resources, R.drawable.image_loading_error)
    }

    private val gifFileCache = ConcurrentHashMap<String, Boolean>()

    /**
     * 缓存bitmap LruCache实现
     * filePath bitmap
     */
    private const val M = 1024 * 1024
    val cacheSize: Int
        get() {
            if (AppConfig.bitmapCacheSize !in 1..1024) {
                AppConfig.bitmapCacheSize = 50
            }
            val userSize = AppConfig.bitmapCacheSize * M
            val heapBound = (MemoryPressure.maxMemory / 8)
                .coerceIn(8L * M, 64L * M)
                .toInt()
            return min(userSize, heapBound)
        }

    val bitmapLruCache = BitmapLruCache()

    class BitmapLruCache : LruCache<String, Bitmap>(cacheSize) {

        private var removeCount = 0

        val count get() = putCount() + createCount() - evictionCount() - removeCount

        override fun sizeOf(key: String, value: Bitmap): Int {
            return if (value === errorBitmap) 256 else value.byteCount
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (!evicted) {
                synchronized(this) {
                    removeCount++
                }
            }
        }

    }

    /**
     * 存储位图到缓存
     * @param key 键
     * @param bitmap 位图
     */
    fun put(key: String, bitmap: Bitmap) {
        MemoryPressure.trimIfNeeded()
        if (!ensureLruCacheSize(bitmap)) return
        bitmapLruCache.put(key, bitmap)
    }

    /**
     * 从缓存获取位图
     * @param key 键
     * @return 位图
     */
    fun get(key: String): Bitmap? {
        return bitmapLruCache[key]
    }

    /**
     * 从缓存移除位图
     * @param key 键
     * @return 被移除的位图
     */
    fun remove(key: String): Bitmap? {
        return bitmapLruCache.remove(key)
    }

    /**
     * 获取未回收的位图
     * @param key 键
     * @return 位图
     */
    private fun getNotRecycled(key: String): Bitmap? {
        val bitmap = bitmapLruCache[key] ?: return null
        if (bitmap.isRecycled) {
            bitmapLruCache.remove(key)
            return null
        }
        return bitmap
    }

    /**
     * 确保LRU缓存大小
     * @param bitmap 位图
     */
    private fun ensureLruCacheSize(bitmap: Bitmap): Boolean {
        val targetSize = cacheSize
        if (bitmapLruCache.maxSize() != targetSize) {
            bitmapLruCache.resize(targetSize)
        }
        return bitmap == errorBitmap || bitmap.byteCount <= bitmapLruCache.maxSize()
    }

    /**
     *缓存网络图片和epub图片
     */
    suspend fun cacheImage(
        book: Book,
        src: String,
        bookSource: BookSource?
    ): File {
        return withContext(IO) {
            val vFile = BookHelp.getImage(book, src)
            if (!BookHelp.isImageExist(book, src)) {
                val inputStream = when {
                    book.isEpub -> EpubFile.getImage(book, src)
                    book.isPdf -> PdfFile.getImage(book, src)
                    book.isMobi -> MobiFile.getImage(book, src)
                    else -> {
                        BookHelp.saveImage(bookSource, book, src)
                        null
                    }
                }
                inputStream?.use { input ->
                    val newFile = FileUtils.createFileIfNotExist(vFile.absolutePath)
                    FileOutputStream(newFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return@withContext vFile
        }
    }

    /**
     *获取图片宽度高度信息
     */
    suspend fun getImageSize(
        book: Book,
        src: String,
        bookSource: BookSource?
    ): Size {
        if (ParagraphBubbleRenderer.isBubbleSrc(src)) {
            return ParagraphBubbleRenderer.getSize(src)
        }
        val file = cacheImage(book, src, bookSource)
        val op = BitmapFactory.Options()
        // inJustDecodeBounds如果设置为true,仅仅返回图片实际的宽和高,宽和高是赋值给opts.outWidth,opts.outHeight;
        op.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, op)
        if (op.outWidth < 1 && op.outHeight < 1) {
            //svg size
            val size = SvgUtils.getSize(file.absolutePath)
            if (size != null) return size
            putDebug("ImageProvider: $src Unsupported image type")
            //file.delete() 重复下载
            return Size(errorBitmap.width, errorBitmap.height)
        }
        return Size(op.outWidth, op.outHeight)
    }

    suspend fun isGif(
        book: Book,
        src: String,
        bookSource: BookSource?
    ): Boolean {
        if (ParagraphBubbleRenderer.isBubbleSrc(src)) {
            return false
        }
        val file = cacheImage(book, src, bookSource)
        return isGifFile(file)
    }

    fun isGifFile(file: File): Boolean {
        return gifFileCache.getOrPut(file.absolutePath) {
            if (!file.exists() || file.length() < 6) {
                return@getOrPut false
            }
            kotlin.runCatching {
                FileInputStream(file).use { input ->
                    val header = ByteArray(6)
                    if (input.read(header) != header.size) {
                        false
                    } else {
                        val signature = String(header, Charsets.US_ASCII)
                        signature == "GIF87a" || signature == "GIF89a"
                    }
                }
            }.getOrDefault(false)
        }
    }

    suspend fun getGifDrawable(
        book: Book,
        src: String,
        width: Int,
        height: Int,
        bookSource: BookSource?
    ): GifDrawable? = withContext(IO) {
        val vFile = cacheImage(book, src, bookSource)
        if (!isGifFile(vFile)) {
            return@withContext null
        }
        kotlin.runCatching {
            Glide.with(appCtx)
                .asGif()
                .load(vFile)
                .submit(width.coerceAtLeast(1), height.coerceAtLeast(1))
                .get()
        }.getOrNull()
    }

    /**
     *获取bitmap 使用LruCache缓存
     */
    fun getImage(
        book: Book,
        src: String,
        width: Int,
        height: Int? = null
    ): Bitmap {
        if (ParagraphBubbleRenderer.isBubbleSrc(src)) {
            val cacheKey = ParagraphBubbleRenderer.cacheKey(src, width, height)
            getNotRecycled(cacheKey)?.let { return it }
            return kotlin.runCatching {
                ParagraphBubbleRenderer.render(src, width, height)
                    ?: throw NoStackTraceException(appCtx.getString(R.string.error_decode_bitmap))
            }.onSuccess {
                put(cacheKey, it)
            }.onFailure {
                put(cacheKey, errorBitmap)
            }.getOrDefault(errorBitmap)
        }
        //src为空白时 可能被净化替换掉了 或者规则失效
        if (book.getUseReplaceRule() && src.isBlank()) {
            book.setUseReplaceRule(false)
            appCtx.toastOnUi(R.string.error_image_url_empty)
        }
        val vFile = BookHelp.getImage(book, src)
        if (!vFile.exists()) return errorBitmap
        //epub文件提供图片链接是相对链接，同时阅读多个epub文件，缓存命中错误
        //bitmapLruCache的key同一改成缓存文件的路径
        val cacheBitmap = getNotRecycled(vFile.absolutePath)
        if (cacheBitmap != null) return cacheBitmap
        return kotlin.runCatching {
            val bitmap = BitmapUtils.decodeBitmap(vFile.absolutePath, width, height)
                ?: SvgUtils.createBitmap(vFile.absolutePath, width, height)
                ?: throw NoStackTraceException(appCtx.getString(R.string.error_decode_bitmap))
            put(vFile.absolutePath, bitmap)
            bitmap
        }.onFailure {
            //错误图片占位,防止重复获取
            put(vFile.absolutePath, errorBitmap)
        }.getOrDefault(errorBitmap)
    }

    /**
     * 清除缓存
     */
    fun clear() {
        bitmapLruCache.evictAll()
    }

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
            || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
            || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            clear()
            gifFileCache.clear()
        } else if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
        ) {
            bitmapLruCache.trimToSize(bitmapLruCache.maxSize() / 2)
        }
    }

}
