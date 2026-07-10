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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
        while (nowSpeak in contentList.indices && contentList[nowSpeak].matches(AppPattern.notReadAloudRegex)) {
            if (!moveToNextParagraph()) return
        }
        val startIndex = nowSpeak
        if (startIndex !in contentList.indices) {
            playStop()
            delay(1000)
            nextChapter()
            return
        }
        var hasAddedText = false
        for (index in startIndex until contentList.size) {
            coroutineContext.ensureActive()
            if (token != speakingToken || pause) return
            var text = contentList[index]
            if (text.matches(AppPattern.notReadAloudRegex)) {
                continue
            }
            if (paragraphStartPos > 0 && index == startIndex) {
                text = text.substring(paragraphStartPos.coerceAtMost(text.length))
            }
            if (!hasAddedText) {
                upParagraphStartProgress()
            }
            val queueMode = if (hasAddedText) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
            val result = tts.runCatching {
                speak(text, queueMode, null, utteranceId(index, token))
            }.getOrElse {
                AppLog.put("tts朗读出错\n${it.localizedMessage}", it, true)
                TextToSpeech.ERROR
            }
            if (result == TextToSpeech.ERROR) {
                if (hasAddedText) {
                    AppLog.put("tts朗读出错:$text")
                } else {
                    retryTts()
                    return
                }
            }
            hasAddedText = true
        }
        if (!hasAddedText && token == speakingToken && !pause) {
            playStop()
            delay(1000)
            nextChapter()
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

    private fun parseUtteranceToken(utteranceId: String?): Int? {
        val value = utteranceId
            ?.takeIf { it.startsWith(AppConst.APP_TAG) }
            ?.substringAfter('_', "")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return value.toIntOrNull()
    }

    private fun isCurrentUtterance(utteranceId: String?): Boolean {
        return parseUtteranceToken(utteranceId) == speakingToken
    }

    private fun upRangeProgress(utteranceId: String?, start: Int) {
        if (!isCurrentUtterance(utteranceId)) return
        val index = parseUtteranceIndex(utteranceId) ?: return
        if (index != nowSpeak) return
        textChapter?.let {
            val progress = readAloudNumber + start
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
            if (!isCurrentUtterance(utteranceId)) return
            upParagraphStartProgress()
        }

        override fun onDone(utteranceId: String?) {
            LogUtils.d(TAG, "onDone utteranceId:$utteranceId")
            if (!isCurrentUtterance(utteranceId)) return
            ttsRetryCount = 0
            moveToNextParagraph()
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            super.onRangeStart(utteranceId, start, end, frame)
            LogUtils.d(TAG, "onRangeStart utteranceId:$utteranceId start:$start end:$end frame:$frame")
            upRangeProgress(utteranceId, start)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            LogUtils.d(TAG, "onError utteranceId:$utteranceId errorCode:$errorCode")
            if (!isCurrentUtterance(utteranceId)) return
            moveToNextParagraph()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            LogUtils.d(TAG, "onError utteranceId:$utteranceId")
            if (!isCurrentUtterance(utteranceId)) return
            moveToNextParagraph()
        }

    }

    override fun playStop() {
        ttsRetryCount = 0
        speakingToken++
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
