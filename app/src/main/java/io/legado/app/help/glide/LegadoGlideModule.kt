package io.legado.app.help.glide

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import io.legado.app.BuildConfig
import io.legado.app.help.MemoryPressure
import io.legado.app.help.config.AppConfig
import java.io.File
import java.io.InputStream
import kotlin.math.min


@Suppress("unused")
@GlideModule
class LegadoGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpModeLoaderFactory
        )
        registry.prepend(
            String::class.java,
            InputStream::class.java,
            LegadoDataUrlLoader.Factory()
        )
        registry.prepend(
            String::class.java,
            File::class.java,
            FilePathLoader.Factory()
        )
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        super.applyOptions(context, builder)
        val calculatorBuilder = MemorySizeCalculator.Builder(context)
        if (MemoryPressure.isSmallHeap) {
            calculatorBuilder
                .setMemoryCacheScreens(1f)
                .setBitmapPoolScreens(1f)
                .setArrayPoolSize(1024 * 1024)
                .setMaxSizeMultiplier(0.18f)
                .setLowMemoryMaxSizeMultiplier(0.12f)
        }
        val calculator = calculatorBuilder.build()
        val bitmapPoolSize = if (MemoryPressure.isSmallHeap) {
            min(calculator.bitmapPoolSize, (MemoryPressure.maxMemory / 24).toInt())
        } else {
            calculator.bitmapPoolSize
        }
        val bitmapPool = AsyncRecycleBitmapPool(bitmapPoolSize)
        builder.setMemorySizeCalculator(calculator)
        builder.setBitmapPool(bitmapPool)
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, 1024 * 1024 * 1000))
        if (!BuildConfig.DEBUG && !AppConfig.recordLog) {
            builder.setLogLevel(Log.ERROR)
        }
    }
}
