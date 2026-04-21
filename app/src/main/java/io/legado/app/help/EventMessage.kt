package io.legado.app.help

import android.text.TextUtils

/**
 * 通用事件消息封装类
 *
 * 当事件需要同时携带标识和数据时使用，替代直接传递基本类型。
 * 两种标识方式：
 *   - tag: 字符串标识，适合语义明确的场景
 *   - what: 整型标识，适合数值编码的场景
 *
 * 使用示例：
 *   发送：postEvent(EventBus.SAVE_CONTENT, EventMessage.obtain("export", filePath))
 *   接收：observeEvent<EventMessage>(EventBus.SAVE_CONTENT) { msg ->
 *       if (msg.isFrom("export")) { ... }
 *   }
 */
@Suppress("unused")
class EventMessage {

    /** 整型事件标识 */
    var what: Int? = null

    /** 字符串事件标识 */
    var tag: String? = null

    /** 事件携带的数据 */
    var obj: Any? = null

    /** 判断事件是否来自指定 tag */
    fun isFrom(tag: String): Boolean {
        return TextUtils.equals(this.tag, tag)
    }

    /** 判断事件是否来自 tags 中的任意一个 */
    fun maybeFrom(vararg tags: String): Boolean {
        return listOf(*tags).contains(tag)
    }

    companion object {

        /** 创建仅带 tag 标识的事件消息 */
        fun obtain(tag: String): EventMessage {
            val message = EventMessage()
            message.tag = tag
            return message
        }

        /** 创建仅带 what 标识的事件消息 */
        fun obtain(what: Int): EventMessage {
            val message = EventMessage()
            message.what = what
            return message
        }

        /** 创建带 what 标识和数据的事件消息 */
        fun obtain(what: Int, obj: Any): EventMessage {
            val message = EventMessage()
            message.what = what
            message.obj = obj
            return message
        }

        /** 创建带 tag 标识和数据的事件消息 */
        fun obtain(tag: String, obj: Any): EventMessage {
            val message = EventMessage()
            message.tag = tag
            message.obj = obj
            return message
        }
    }

}
