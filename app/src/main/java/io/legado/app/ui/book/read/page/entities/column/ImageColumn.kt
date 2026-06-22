package io.legado.app.ui.book.read.page.entities.column

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import com.bumptech.glide.load.resource.gif.GifDrawable
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextLine.Companion.emptyTextLine
import io.legado.app.utils.dpToPx
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.lang.ref.WeakReference

/**
 * 图片列
 */
@Keep
data class ImageColumn(
    override var start: Float,
    override var end: Float,
    var src: String,
    var click: String? = null,
    var isAnimated: Boolean = false
) : BaseColumn {

    companion object {
        private val animationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override var textLine: TextLine = emptyTextLine
    @Volatile
    private var gifDrawable: GifDrawable? = null
    @Volatile
    private var gifLoadStarted = false
    private var attachedViewRef: WeakReference<ContentTextView>? = null
    private val drawableCallback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) {
            textLine.invalidateSelf()
            attachedViewRef?.get()?.postInvalidateOnAnimation()
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            val view = attachedViewRef?.get() ?: return
            val delay = (`when` - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0L)
            view.postDelayed(what, delay)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            attachedViewRef?.get()?.removeCallbacks(what)
        }
    }

    override fun draw(view: ContentTextView, canvas: Canvas) {
        val book = ReadBook.book ?: return

        val height = textLine.height
        val width = (end - start).toInt().coerceAtLeast(1)

        if (isAnimated) {
            view.registerAnimatedColumn(this)
            ensureGifDrawable(view, width, height.toInt().coerceAtLeast(1))
        }

        val drawable = gifDrawable
        val bitmap = if (isAnimated && drawable != null) {
            null
        } else {
            ImageProvider.getImage(
                book,
                src,
                width,
                height.toInt()
            )
        }

        val rectF = if (textLine.isImage) {
            RectF(start, 0f, end, height)
        } else {
            /*以宽度为基准保持图片的原始比例叠加，当div为负数时，允许高度比字符更高*/
            val imageWidth = drawable?.intrinsicWidth ?: bitmap?.width ?: return
            val imageHeight = drawable?.intrinsicHeight ?: bitmap?.height ?: return
            val h = (end - start) / imageWidth * imageHeight
            val div = (height - h) / 2
            RectF(start, div, end, height - div)
        }
        kotlin.runCatching {
            if (isAnimated && drawable != null) {
                drawable.bounds = Rect(
                    rectF.left.toInt(),
                    rectF.top.toInt(),
                    rectF.right.toInt(),
                    rectF.bottom.toInt()
                )
                drawable.draw(canvas)
            } else if (bitmap != null && !bitmap.isRecycled) {
                canvas.drawBitmap(bitmap, null, rectF, view.imagePaint)
            }
        }.onFailure { e ->
            appCtx.toastOnUi(e.localizedMessage)
        }
    }

    override fun isTouch(x: Float): Boolean {
        return x > start && x < end + 20.dpToPx()
    }

    fun attachAnimatedView(view: ContentTextView) {
        if (!isAnimated) return
        attachedViewRef = WeakReference(view)
        gifDrawable?.let { drawable ->
            if (drawable.callback !== drawableCallback) {
                drawable.callback = drawableCallback
            }
            if (!drawable.isRunning) {
                drawable.start()
            }
        }
    }

    fun detachAnimatedView() {
        if (!isAnimated) return
        attachedViewRef?.clear()
        attachedViewRef = null
        gifDrawable?.let { drawable ->
            drawable.stop()
            if (drawable.callback === drawableCallback) {
                drawable.callback = null
            }
        }
    }

    private fun ensureGifDrawable(view: ContentTextView, width: Int, height: Int) {
        if (!isAnimated || gifDrawable != null || gifLoadStarted) return
        val book = ReadBook.book ?: return
        gifLoadStarted = true
        animationScope.launch {
            val drawable = ImageProvider.getGifDrawable(
                book = book,
                src = src,
                width = width,
                height = height,
                bookSource = ReadBook.bookSource
            )
            if (drawable == null) {
                gifLoadStarted = false
                return@launch
            }
            gifDrawable = drawable
            gifLoadStarted = false
            view.post {
                attachAnimatedView(view)
                textLine.invalidateSelf()
                view.postInvalidateOnAnimation()
            }
        }
    }

}
