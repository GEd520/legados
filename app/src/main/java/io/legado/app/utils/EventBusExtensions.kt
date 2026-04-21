@file:Suppress("unused")
//事件总线扩展函数
/**
 * 事件总线扩展函数
 *
 * 基于 LiveEventBus 封装，提供类型安全的事件发送与订阅 API。
 * 利用 Kotlin reified 泛型在编译期确定事件类型，避免运行时类型转换错误。
 *
 * 核心流程：
 *   发送端：postEvent(tag, event) → LiveEventBus 内部以 tag 为 key 分发事件
 *   订阅端：observeEvent(tag) { event -> ... } → 通过 LiveData 自动感知生命周期
 *
 * 支持三种组件订阅：AppCompatActivity、Fragment、LifecycleService
 * 支持粘性事件：observeEventSticky 可接收订阅前已发送的事件
 */
package io.legado.app.utils

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import com.jeremyliao.liveeventbus.LiveEventBus
import com.jeremyliao.liveeventbus.core.Observable

/**
 * 获取指定 tag 和类型的事件 Observable，用于手动调用 observe/removeObserver 等高级操作
 */
inline fun <reified EVENT> eventObservable(tag: String): Observable<EVENT> {
    return LiveEventBus.get(tag, EVENT::class.java)
}

/**
 * 立即发送事件，所有订阅该 tag 的观察者都会收到通知
 */
inline fun <reified EVENT> postEvent(tag: String, event: EVENT) {
    LiveEventBus.get<EVENT>(tag).post(event)
}

/**
 * 延迟发送事件，在指定 delay 毫秒后分发
 */
inline fun <reified EVENT> postEventDelay(tag: String, event: EVENT, delay: Long) {
    LiveEventBus.get<EVENT>(tag).postDelay(event, delay)
}

/**
 * 有序发送事件，保证事件按调用顺序依次分发（内部通过主线程 Handler 排队）
 */
inline fun <reified EVENT> postEventOrderly(tag: String, event: EVENT) {
    LiveEventBus.get<EVENT>(tag).postOrderly(event)
}

// ── AppCompatActivity 订阅 ──

/**
 * 在 Activity 中订阅事件，生命周期感知：Activity 销毁时自动移除观察者
 * @param tags 事件标签，支持同时订阅多个 tag
 * @param observer 事件回调，泛型 EVENT 决定回调参数类型
 */
inline fun <reified EVENT> AppCompatActivity.observeEvent(
    vararg tags: String,
    noinline observer: (EVENT) -> Unit
) {
    val o = Observer<EVENT> {
        observer(it)
    }
    tags.forEach {
        eventObservable<EVENT>(it).observe(this, o)
    }
}

/**
 * 在 Activity 中订阅粘性事件，可接收订阅之前已发送的最新事件
 * 适用场景：进入页面时需要获取当前状态（如音频播放状态）
 */
inline fun <reified EVENT> AppCompatActivity.observeEventSticky(
    vararg tags: String,
    noinline observer: (EVENT) -> Unit
) {
    val o = Observer<EVENT> {
        observer(it)
    }
    tags.forEach {
        eventObservable<EVENT>(it).observeSticky(this, o)
    }
}

// ── Fragment 订阅 ──

/**
 * 在 Fragment 中订阅事件，生命周期感知：Fragment 视图销毁时自动移除观察者
 */
inline fun <reified EVENT> Fragment.observeEvent(
    vararg tags: String,
    noinline observer: (EVENT) -> Unit
) {
    val o = Observer<EVENT> {
        observer(it)
    }
    tags.forEach {
        eventObservable<EVENT>(it).observe(this, o)
    }
}

/**
 * 在 Fragment 中订阅粘性事件
 */
inline fun <reified EVENT> Fragment.observeEventSticky(
    vararg tags: String,
    noinline observer: (EVENT) -> Unit
) {
    val o = Observer<EVENT> {
        observer(it)
    }
    tags.forEach {
        eventObservable<EVENT>(it).observeSticky(this, o)
    }
}

// ── LifecycleService 订阅 ──

/**
 * 在 LifecycleService 中订阅事件，生命周期感知：Service 销毁时自动移除观察者
 */
inline fun <reified EVENT> LifecycleService.observeEvent(
    vararg tags: String,
    noinline observer: (EVENT) -> Unit
) {
    val o = Observer<EVENT> {
        observer(it)
    }
    tags.forEach {
        eventObservable<EVENT>(it).observe(this, o)
    }
}

/**
 * 在 LifecycleService 中订阅粘性事件
 */
inline fun <reified EVENT> LifecycleService.observeEventSticky(
    vararg tags: String,
    noinline observer: (EVENT) -> Unit
) {
    val o = Observer<EVENT> {
        observer(it)
    }
    tags.forEach {
        eventObservable<EVENT>(it).observeSticky(this, o)
    }
}