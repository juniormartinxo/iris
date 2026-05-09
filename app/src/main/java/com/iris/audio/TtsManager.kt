package com.iris.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class TtsManager(private val context: Context) {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ptBrAvailable = MutableStateFlow(false)
    val ptBrAvailable: StateFlow<Boolean> = _ptBrAvailable.asStateFlow()

    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            if (utteranceId != null && pending.containsKey(utteranceId)) {
                _isSpeaking.value = true
            }
        }
        override fun onDone(utteranceId: String?) = resume(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = resume(utteranceId)

        @Deprecated("kept for API compat")
        override fun onError(utteranceId: String?) {
            resume(utteranceId)
        }

        private fun resume(utteranceId: String?) {
            if (utteranceId == null) return
            val cont = pending.remove(utteranceId) ?: return
            if (pending.isEmpty()) _isSpeaking.value = false
            if (cont.isActive) cont.resume(Unit)
        }
    }

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale("pt", "BR")
            val langStatus = tts.setLanguage(locale)
            val available = langStatus == TextToSpeech.LANG_AVAILABLE ||
                langStatus == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                langStatus == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            _ptBrAvailable.value = available
            tts.setSpeechRate(1.0f)
            tts.setPitch(1.0f)
            tts.setOnUtteranceProgressListener(progressListener)
            _isReady.value = true
        } else {
            _isReady.value = false
        }
    }

    suspend fun speak(text: String) {
        if (!_isReady.value || text.isBlank()) return
        suspendCancellableCoroutine<Unit> { cont ->
            val id = UUID.randomUUID().toString()
            pending[id] = cont
            cont.invokeOnCancellation {
                pending.remove(id)
                tts.stop()
                if (pending.isEmpty()) _isSpeaking.value = false
            }
            tts.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), id)
        }
    }

    fun stop() {
        tts.stop()
        val drained = pending.values.toList()
        pending.clear()
        _isSpeaking.value = false
        drained.forEach { if (it.isActive) it.resume(Unit) }
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
        stop()
        _isReady.value = false
    }
}
