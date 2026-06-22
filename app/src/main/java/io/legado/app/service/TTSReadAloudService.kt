package io.legado.app.service

import android.app.PendingIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.MediaHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * 本地朗读
 */
class TTSReadAloudService : BaseReadAloudService(), TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    @Volatile
    private var ttsInitFinish = false
    private var speakJob: Coroutine<*>? = null
    private val utteranceListener = TTSUtteranceListener()
    private val utteranceWaiters = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    @Volatile
    private var speakingToken = 0

    @Volatile
    private var ttsRetryCount = 0

    private val TAG = "TTSReadAloudService"

    override fun onCreate() {
        super.onCreate()
        kotlin.runCatching {
            initTts()
        }.onFailure {
            AppLog.put("${getString(R.string.tts_init_failed)}\n$it", it, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearTTS()
    }

    @Synchronized
    private fun initTts() {
        ttsInitFinish = false
        val engine = GSON.fromJsonObject<SelectItem<String>>(ReadAloud.ttsEngine).getOrNull()?.value
        LogUtils.d(TAG, "initTts engine:$engine")
        textToSpeech = if (engine.isNullOrBlank()) {
            TextToSpeech(this, this)
        } else {
            TextToSpeech(this, this, engine)
        }
        upSpeechRate()
    }

    @Synchronized
    fun clearTTS() {
        speakJob?.cancel()
        speakingToken++
        completeAllUtterances(false)
        textToSpeech?.runCatching {
            setOnUtteranceProgressListener(null)
            stop()
            shutdown()
        }
        textToSpeech = null
        ttsInitFinish = false
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.setOnUtteranceProgressListener(utteranceListener)
            ttsInitFinish = true
            play()
        } else {
            toastOnUi(R.string.tts_init_failed)
        }
    }

    @Synchronized
    override fun play() {
        if (!ttsInitFinish) return
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("朗读列表为空")
            ReadBook.readAloud()
            return
        }
        super.play()
        MediaHelp.playSilentSound(this@TTSReadAloudService)
        speakJob?.cancel()
        val token = ++speakingToken
        speakJob = execute {
            speakLoop(token)
        }.onError {
            AppLog.put("tts朗读出错\n${it.localizedMessage}", it, true)
        }
    }

    private suspend fun speakLoop(token: Int) {
        LogUtils.d(TAG, "朗读列表大小 ${contentList.size}")
        LogUtils.d(TAG, "朗读页数 ${textChapter?.pageSize}")
        val tts = textToSpeech ?: throw NoStackTraceException("tts is null")
        while (coroutineContext.isActive && token == speakingToken && !pause) {
            if (nowSpeak !in contentList.indices) {
                playStop()
                delay(1000)
                nextChapter()
                return
            }
            var text = contentList[nowSpeak]
            if (text.matches(AppPattern.notReadAloudRegex)) {
                if (!moveToNextParagraph()) return
                continue
            }
            if (paragraphStartPos > 0) {
                text = text.substring(paragraphStartPos.coerceAtMost(text.length))
            }
            upParagraphStartProgress()
            val utteranceId = utteranceId(nowSpeak, token)
            val waiter = CompletableDeferred<Boolean>()
            utteranceWaiters[utteranceId] = waiter
            val result = tts.runCatching {
                speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }.getOrElse {
                utteranceWaiters.remove(utteranceId)
                AppLog.put("tts朗读出错\n${it.localizedMessage}", it, true)
                TextToSpeech.ERROR
            }
            if (result == TextToSpeech.ERROR) {
                utteranceWaiters.remove(utteranceId)
                retryTts()
                return
            }
            val completed = waitCurrentParagraphDone(utteranceId, waiter, token, text.length)
            if (token != speakingToken || pause || !coroutineContext.isActive) return
            if (!completed) {
                retryTts()
                return
            }
            ttsRetryCount = 0
            if (!moveToNextParagraph()) return
        }
    }

    private fun retryTts() {
        if (ttsRetryCount >= 3) {
            AppLog.put("tts连续出错, 停止重试")
            toastOnUi(R.string.tts_init_failed)
            pauseReadAloud(false)
            return
        }
        ttsRetryCount++
        AppLog.put("tts出错, 尝试重新初始化($ttsRetryCount/3)")
        clearTTS()
        initTts()
    }

    private suspend fun waitCurrentParagraphDone(
        utteranceId: String,
        waiter: CompletableDeferred<Boolean>,
        token: Int,
        textLength: Int
    ): Boolean {
        val timeout = (60_000L + textLength * 120L).coerceIn(90_000L, 600_000L)
        val result = withTimeoutOrNull(timeout) {
            waiter.await()
        }
        utteranceWaiters.remove(utteranceId)
        if (result == null && token == speakingToken && !pause && coroutineContext.isActive) {
            AppLog.put("tts朗读等待超时:$utteranceId")
        }
        return result == true
    }

    private fun upParagraphStartProgress() {
        textChapter?.let {
            if (pageIndex + 1 < it.pageSize
                && readAloudNumber + 1 > it.getReadLength(pageIndex + 1)
            ) {
                pageIndex++
                ReadBook.moveToNextPage()
            }
            upTtsProgress(readAloudNumber + 1)
        }
    }

    private fun moveToNextParagraph(): Boolean {
        do {
            if (nowSpeak !in contentList.indices) {
                nextChapter()
                return false
            }
            readAloudNumber += contentList[nowSpeak].length + 1 - paragraphStartPos
            paragraphStartPos = 0
            nowSpeak++
            if (nowSpeak >= contentList.size) {
                nextChapter()
                return false
            }
        } while (contentList[nowSpeak].matches(AppPattern.notReadAloudRegex))
        return true
    }

    private fun utteranceId(index: Int, token: Int): String {
        return "${AppConst.APP_TAG}${index}_$token"
    }

    private fun parseUtteranceIndex(utteranceId: String?): Int? {
        val value = utteranceId
            ?.takeIf { it.startsWith(AppConst.APP_TAG) }
            ?.removePrefix(AppConst.APP_TAG)
            ?.substringBefore('_')
            ?: return null
        return value.toIntOrNull()
    }

    private fun completeUtterance(utteranceId: String?, success: Boolean) {
        utteranceId ?: return
        utteranceWaiters.remove(utteranceId)?.complete(success)
    }

    private fun completeAllUtterances(success: Boolean) {
        utteranceWaiters.values.forEach { it.complete(success) }
        utteranceWaiters.clear()
    }

    private fun upRangeProgress(utteranceId: String?, start: Int) {
        val index = parseUtteranceIndex(utteranceId) ?: return
        if (index != nowSpeak) return
        textChapter?.let {
            val progress = readAloudNumber + paragraphStartPos + start
            if (pageIndex + 1 < it.pageSize
                && progress > it.getReadLength(pageIndex + 1)
            ) {
                pageIndex++
                ReadBook.moveToNextPage()
            }
            upTtsProgress(progress)
        }
    }

    private inner class TTSUtteranceListener : UtteranceProgressListener() {

        override fun onStart(utteranceId: String?) {
            LogUtils.d(TAG, "onStart nowSpeak:$nowSpeak pageIndex:$pageIndex utteranceId:$utteranceId")
        }

        override fun onDone(utteranceId: String?) {
            LogUtils.d(TAG, "onDone utteranceId:$utteranceId")
            completeUtterance(utteranceId, true)
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            super.onRangeStart(utteranceId, start, end, frame)
            LogUtils.d(TAG, "onRangeStart utteranceId:$utteranceId start:$start end:$end frame:$frame")
            upRangeProgress(utteranceId, start)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            LogUtils.d(TAG, "onError utteranceId:$utteranceId errorCode:$errorCode")
            completeUtterance(utteranceId, false)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            LogUtils.d(TAG, "onError utteranceId:$utteranceId")
            completeUtterance(utteranceId, false)
        }

    }

    override fun playStop() {
        ttsRetryCount = 0
        speakingToken++
        completeAllUtterances(false)
        textToSpeech?.runCatching {
            stop()
        }
    }

    /**
     * 更新朗读速度
     */
    override fun upSpeechRate(reset: Boolean) {
        if (AppConfig.ttsFlowSys) {
            if (reset) {
                clearTTS()
                initTts()
            }
        } else {
            val speechRate = (AppConfig.ttsSpeechRate + 5) / 10f
            textToSpeech?.setSpeechRate(speechRate)
        }
    }

    /**
     * 暂停朗读
     */
    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        ttsRetryCount = 0
        speakJob?.cancel()
        speakingToken++
        completeAllUtterances(false)
        textToSpeech?.runCatching {
            stop()
        }
    }

    /**
     * 恢复朗读
     */
    override fun resumeReadAloud() {
        ttsRetryCount = 0
        super.resumeReadAloud()
        play()
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<TTSReadAloudService>(actionStr)
    }

}
