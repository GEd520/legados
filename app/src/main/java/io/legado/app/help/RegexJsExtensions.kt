package io.legado.app.help

import androidx.annotation.Keep
import io.legado.app.constant.AppLog
import io.legado.app.data.repository.debug.FlowLogRecorder
import io.legado.app.model.analyzeRule.RuleData
import io.legado.app.model.debug.VariableStorage
import io.legado.app.utils.ChineseUtils

@Keep
@Suppress("unused")
class RegexJsExtensions(private val name: String): JsEncodeUtils {
    private val ruleData by lazy { RuleData() }

    /**
     * 输出调试日志
     */
    fun log(msg: Any?): Any? {
        AppLog.putDebug("替换净化规则 $name 输出: $msg")
        return msg
    }

    /**
     * 输出对象类型
     */
    fun logType(any: Any?) {
        if (any == null) {
            log("null")
        } else {
            log(any.javaClass.name)
        }
    }

    fun t2s(text: String): String {
        return ChineseUtils.t2s(text)
    }

    fun s2t(text: String): String {
        return ChineseUtils.s2t(text)
    }

    fun get(key: String): String {
        val value = ruleData.getVariable(key).takeIf { it.isNotEmpty() } ?: ""
        if (value.isNotEmpty() && FlowLogRecorder.isEnabled) {
            FlowLogRecorder.logVariableRead(
                source = null,
                key = key,
                value = value,
                storage = VariableStorage.RULE_DATA
            )
        }
        return value
    }

    fun put(key: String, value: String): String {
        val debugEnabled = FlowLogRecorder.isEnabled
        val oldValue = if (debugEnabled) ruleData.getVariable(key).takeIf { it.isNotEmpty() } else null
        ruleData.putVariable(key, value)
        if (debugEnabled) {
            FlowLogRecorder.logVariableWrite(
                source = null,
                key = key,
                value = value,
                oldValue = oldValue,
                storage = VariableStorage.RULE_DATA
            )
        }
        return value
    }
}
