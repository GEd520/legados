package io.legado.app.utils

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object ImageProcessUtils {

    fun saveBitmapToFile(
        context: Context,
        bitmap: Bitmap,
        aspectWidth: Int,
        aspectHeight: Int,
        dirName: String,
        prefix: String,
        targetWidth: Int = 1600,
        outputPath: String? = null
    ): String? {
        val safeAspectWidth = aspectWidth.coerceAtLeast(1)
        val safeAspectHeight = aspectHeight.coerceAtLeast(1)
        val safeTargetWidth = targetWidth.coerceAtLeast(128)
        val targetHeight = (safeTargetWidth * safeAspectHeight.toFloat() / safeAspectWidth)
            .roundToInt()
            .coerceAtLeast(128)
        val scaled = if (bitmap.width != safeTargetWidth || bitmap.height != targetHeight) {
            Bitmap.createScaledBitmap(bitmap, safeTargetWidth, targetHeight, true)
        } else {
            bitmap
        }
        val file = if (outputPath.isNullOrBlank()) {
            val dir = context.externalFiles.getFile(dirName).apply { mkdirs() }
            File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        } else {
            File(outputPath).withJpgExtension().apply {
                parentFile?.mkdirs()
            }
        }
        try {
            FileOutputStream(file).use {
                scaled.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
        return file.absolutePath
    }

    fun calculateSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= targetWidth &&
            height / (sampleSize * 2) >= targetHeight
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun File.withJpgExtension(): File {
        if (extension.equals("jpg", ignoreCase = true) ||
            extension.equals("jpeg", ignoreCase = true)
        ) {
            return this
        }
        return File(parentFile ?: File("."), "$nameWithoutExtension.jpg")
    }
}
