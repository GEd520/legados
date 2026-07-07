package io.legado.app.help

import android.webkit.JavascriptInterface
import androidx.annotation.Keep
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.repository.debug.FlowLogRecorder
import io.legado.app.model.debug.VariableStorage

/**
 * JS缓存管理器包装类
 * 
 * 用于在JS中追踪 cache.get() / cache.put() 操作
 * 包装 CacheManager 的功能，并添加调试日志记录
 */
@Keep
@Suppress("unused")
class JsCacheManager(
    private val source: BaseSource? = null
) {
    @JavascriptInterface
    fun put(key: String, value: String): String {
        val debugEnabled = FlowLogRecorder.isEnabled
        val oldValue = if (debugEnabled) CacheManager.get(key) else null
        CacheManager.put(key, value)
        if (debugEnabled) {
            FlowLogRecorder.logVariableWrite(
                source = source,
                key = key,
                value = value,
                oldValue = oldValue,
                storage = VariableStorage.SOURCE
            )
        }
        return value
    }

    @JavascriptInterface
    fun put(key: String, value: String, saveTime: Int): String {
        val debugEnabled = FlowLogRecorder.isEnabled
        val oldValue = if (debugEnabled) CacheManager.get(key) else null
        CacheManager.put(key, value, saveTime)
        if (debugEnabled) {
            FlowLogRecorder.logVariableWrite(
                source = source,
                key = key,
                value = value,
                oldValue = oldValue,
                storage = VariableStorage.SOURCE
            )
        }
        return value
    }

    @JavascriptInterface
    fun get(key: String): String? {
        val value = CacheManager.get(key)
        if (value != null && FlowLogRecorder.isEnabled) {
            FlowLogRecorder.logVariableRead(
                source = source,
                key = key,
                value = value,
                storage = VariableStorage.SOURCE
            )
        }
        return value
    }

    @JavascriptInterface
    fun delete(key: String) {
        val debugEnabled = FlowLogRecorder.isEnabled
        val oldValue = if (debugEnabled) CacheManager.get(key) else null
        CacheManager.delete(key)
        if (oldValue != null && debugEnabled) {
            FlowLogRecorder.logVariable(
                source = source,
                operations = listOf(
                    io.legado.app.model.debug.VariableOperation(
                        operationType = io.legado.app.model.debug.VariableOperationType.DELETE,
                        key = key,
                        oldValue = oldValue,
                        storage = VariableStorage.SOURCE
                    )
                ),
                message = "删除缓存 $key"
            )
        }
    }

    @JavascriptInterface
    fun putMemory(key: String, value: Any) {
        CacheManager.putMemory(key, value)
    }

    @JavascriptInterface
    fun getFromMemory(key: String): Any? {
        return CacheManager.getFromMemory(key)
    }

    @JavascriptInterface
    fun deleteMemory(key: String) {
        CacheManager.deleteMemory(key)
    }

    @JavascriptInterface
    fun getInt(key: String): Int? {
        val value = CacheManager.getInt(key)
        if (value != null && FlowLogRecorder.isEnabled) {
            FlowLogRecorder.logVariableRead(
                source = source,
                key = key,
                value = value.toString(),
                storage = VariableStorage.SOURCE
            )
        }
        return value
    }

    @JavascriptInterface
    fun getLong(key: String): Long? {
        val value = CacheManager.getLong(key)
        if (value != null && FlowLogRecorder.isEnabled) {
            FlowLogRecorder.logVariableRead(
                source = source,
                key = key,
                value = value.toString(),
                storage = VariableStorage.SOURCE
            )
        }
        return value
    }

    @JavascriptInterface
    fun getDouble(key: String): Double? {
        val value = CacheManager.getDouble(key)
        if (value != null && FlowLogRecorder.isEnabled) {
            FlowLogRecorder.logVariableRead(
                source = source,
                key = key,
                value = value.toString(),
                storage = VariableStorage.SOURCE
            )
        }
        return value
    }
}
