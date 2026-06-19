package io.legado.app.help

import android.content.ComponentCallbacks2
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object MemoryPressure {

    private const val M = 1024 * 1024L
    private var lastTrimTime = 0L
    private var trimCallback: ((Int) -> Unit)? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    val maxMemory: Long
        get() = Runtime.getRuntime().maxMemory()

    val isSmallHeap: Boolean
        get() = maxMemory <= 320L * M

    fun usedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    fun availableMemory(): Long {
        return maxMemory - usedMemory()
    }

    fun shouldTrimNow(): Boolean {
        val available = availableMemory()
        val max = maxMemory
        return available < 24L * M || available < max / 10
    }

    @Suppress("DEPRECATION")
    fun trimLevelForCurrentState(): Int {
        val available = availableMemory()
        return when {
            available < 8L * M -> ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
            available < 16L * M -> ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
            else -> ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
        }
    }

    fun throttleTrim(block: (Int) -> Unit) {
        if (!shouldTrimNow()) return
        val now = System.currentTimeMillis()
        if (now - lastTrimTime < 1500L) return
        lastTrimTime = now
        block(trimLevelForCurrentState())
    }

    fun setTrimCallback(callback: (Int) -> Unit) {
        trimCallback = callback
    }

    fun trimNow(level: Int, waitForCompletion: Boolean = false) {
        lastTrimTime = System.currentTimeMillis()
        dispatchTrim(level, waitForCompletion)
    }

    fun trimIfNeeded() {
        throttleTrim { level ->
            dispatchTrim(level, waitForCompletion = false)
        }
    }

    private fun dispatchTrim(level: Int, waitForCompletion: Boolean) {
        val callback = trimCallback ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(level)
            return
        }
        if (!waitForCompletion) {
            mainHandler.post { callback(level) }
            return
        }
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                callback(level)
            } finally {
                latch.countDown()
            }
        }
        latch.await(500L, TimeUnit.MILLISECONDS)
    }
}
