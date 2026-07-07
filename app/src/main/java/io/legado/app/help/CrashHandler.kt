package io.legado.app.help

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.Looper
import android.webkit.WebSettings
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.model.ReadAloud
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.createFolderReplace
import io.legado.app.utils.externalCache
import io.legado.app.utils.getFile
import io.legado.app.utils.longToastOnUiLegacy
import io.legado.app.utils.sendToClip
import io.legado.app.utils.writeText
import splitties.init.appCtx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * 异常管理类
 */
class CrashHandler(val context: Context) : Thread.UncaughtExceptionHandler {

    /**
     * 系统默认UncaughtExceptionHandler
     */
    private var mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        //设置该CrashHandler为系统默认的
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    /**
     * uncaughtException 回调函数
     */
    override fun uncaughtException(thread: Thread, ex: Throwable) {
        if (shouldAbsorb(ex)) {
            AppLog.put("发生未捕获的异常\n${ex.localizedMessage}", ex)
            Looper.loop()
        } else {
            ReadAloud.stop(context)
            handleException(ex)
            mDefaultHandler?.uncaughtException(thread, ex)
        }
    }

    private fun shouldAbsorb(e: Throwable): Boolean {
        return when {
            e::class.simpleName == "CannotDeliverBroadcastException" -> true
            e is SecurityException && e.message?.contains(
                "nor current process has android.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS",
                true
            ) == true -> true

            else -> false
        }
    }

    /**
     * 处理该异常
     */
    private fun handleException(ex: Throwable?) {
        if (ex == null) return
        LocalConfig.appCrash = true
        //保存日志文件
        saveCrashInfo2File(ex)
        //复制到剪贴板
        if (AppConfig.copyCrashLog) {
            copyCrashLogToClipboard(ex)
        }
        if ((ex is OutOfMemoryError || ex.cause is OutOfMemoryError) && AppConfig.recordHeapDump) {
            doHeapDump()
        }
        context.longToastOnUiLegacy(safeCrashToast(ex))
        Thread.sleep(3000)
    }

    /**
     * 复制崩溃日志到剪贴板
     */
    private fun copyCrashLogToClipboard(ex: Throwable) {
        kotlin.runCatching {
            context.sendToClip(generateCrashLog(ex))
        }
    }

    companion object {
        /**
         * 存储异常和参数信息
         */
        private val paramsMap by lazy {
            val map = LinkedHashMap<String, String>()
            kotlin.runCatching {
                //获取系统信息
                map["MANUFACTURER"] = Build.MANUFACTURER
                map["BRAND"] = Build.BRAND
                map["MODEL"] = Build.MODEL
                map["SDK_INT"] = Build.VERSION.SDK_INT.toString()
                map["RELEASE"] = Build.VERSION.RELEASE
                map["WebViewUserAgent"] = try {
                    WebSettings.getDefaultUserAgent(appCtx)
                } catch (e: Throwable) {
                    e.toString()
                }
                map["packageName"] = appCtx.packageName
                map["heapSize"] = Runtime.getRuntime().maxMemory().toString()
                //获取app版本信息
                AppConst.appInfo.let {
                    map["versionName"] = it.versionName
                    map["versionCode"] = it.versionCode.toString()
                }
            }
            map
        }

        /**
         * 格式化时间
         */
        @SuppressLint("SimpleDateFormat")
        private val format = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")

        private const val MAX_CRASH_LOG_CHARS = 64 * 1024
        private const val MAX_THROWABLE_DEPTH = 8
        private const val MAX_STACK_FRAMES = 96
        private const val MAX_SUPPRESSED = 8
        private const val MAX_THROWABLE_HEADER_CHARS = 4096

        /**
         * 生成崩溃日志字符串
         */
        private fun generateCrashLog(ex: Throwable): String {
            val sb = StringBuilder(8 * 1024)
            for ((key, value) in paramsMap) {
                sb.appendLimited(key).appendLimited("=").appendLimited(value).appendLimited("\n")
            }

            appendThrowableLimited(sb, ex)
            return sb.toString()
        }

        private fun safeCrashToast(ex: Throwable): String {
            return buildString {
                appendLimited(ex.javaClass.name)
                ex.message?.takeIf { it.isNotBlank() }?.let {
                    appendLimited(": ")
                    appendLimited(it, 512)
                }
            }
        }

        private fun appendThrowableLimited(
            sb: StringBuilder,
            throwable: Throwable,
            prefix: String = "",
            depth: Int = 0
        ) {
            if (depth >= MAX_THROWABLE_DEPTH || sb.length >= MAX_CRASH_LOG_CHARS) {
                sb.appendLimited("... throwable chain truncated\n")
                return
            }
            sb.appendLimited(prefix).appendLimited(throwable.javaClass.name)
            throwable.message?.takeIf { it.isNotBlank() }?.let {
                sb.appendLimited(": ")
                sb.appendLimited(it, MAX_THROWABLE_HEADER_CHARS)
            }
            sb.appendLimited("\n")

            val stack = throwable.stackTrace
            val frameCount = stack.size.coerceAtMost(MAX_STACK_FRAMES)
            for (i in 0 until frameCount) {
                sb.appendLimited("\tat ")
                    .appendLimited(stack[i].toString())
                    .appendLimited("\n")
            }
            if (stack.size > frameCount) {
                sb.appendLimited("\t... ")
                    .appendLimited((stack.size - frameCount).toString())
                    .appendLimited(" more\n")
            }

            throwable.suppressed
                .take(MAX_SUPPRESSED)
                .forEach {
                    appendThrowableLimited(sb, it, "Suppressed: ", depth + 1)
                }
            if (throwable.suppressed.size > MAX_SUPPRESSED) {
                sb.appendLimited("... ")
                    .appendLimited((throwable.suppressed.size - MAX_SUPPRESSED).toString())
                    .appendLimited(" suppressed exceptions truncated\n")
            }

            throwable.cause?.let {
                appendThrowableLimited(sb, it, "Caused by: ", depth + 1)
            }
        }

        private fun StringBuilder.appendLimited(value: String, maxChars: Int = Int.MAX_VALUE): StringBuilder {
            if (length >= MAX_CRASH_LOG_CHARS) return this
            val remaining = MAX_CRASH_LOG_CHARS - length
            val appendLength = value.length.coerceAtMost(maxChars).coerceAtMost(remaining)
            append(value, 0, appendLength)
            if (appendLength < value.length && length < MAX_CRASH_LOG_CHARS) {
                val marker = "...[truncated]"
                append(marker, 0, marker.length.coerceAtMost(MAX_CRASH_LOG_CHARS - length))
            }
            return this
        }

        /**
         * 保存错误信息到文件中
         */
        fun saveCrashInfo2File(ex: Throwable) {
            val crashLog = generateCrashLog(ex)
            val timestamp = System.currentTimeMillis()
            val time = format.format(Date())
            val fileName = "crash-$time-$timestamp.log"
            try {
                val backupPath = AppConfig.backupPath
                    ?: throw NoStackTraceException("备份路径未配置")
                val uri = Uri.parse(backupPath)
                val fileDoc = FileDoc.fromUri(uri, true)
                fileDoc.createFileIfNotExist(fileName, "crash")
                    .writeText(crashLog)
            } catch (_: Exception) {
            }
            kotlin.runCatching {
                appCtx.externalCacheDir?.let { rootFile ->
                    val exceedTimeMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
                    rootFile.getFile("crash").listFiles()?.forEach {
                        if (it.lastModified() < exceedTimeMillis) {
                            it.delete()
                        }
                    }
                    FileUtils.createFileIfNotExist(rootFile, "crash", fileName)
                        .writeText(crashLog)
                }
            }
        }

        /**
         * 进行堆转储
         */
        fun doHeapDump(manually: Boolean = false) {
            val heapDir = appCtx
                .externalCache
                .getFile("heapDump")
            heapDir.createFolderReplace()
            val fileName = if (manually) {
                "heap-dump-manually-${System.currentTimeMillis()}.hprof"
            } else {
                "heap-dump-${System.currentTimeMillis()}.hprof"
            }
            val heapFile = heapDir.getFile(fileName)
            val heapDumpName = heapFile.absolutePath
            Debug.dumpHprofData(heapDumpName)
        }

    }

}
