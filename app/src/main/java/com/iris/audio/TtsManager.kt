package com.iris.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

class TtsManager(private val context: Context) {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ptBrAvailable = MutableStateFlow(false)
    val ptBrAvailable: StateFlow<Boolean> = _ptBrAvailable.asStateFlow()

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
            _isReady.value = true
        } else {
            _isReady.value = false
        }
    }

    suspend fun speak(text: String) {
        if (!_isReady.value || text.isBlank()) return
        suspendCancellableCoroutine<Unit> { cont ->
            val id = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId == id) _isSpeaking.value = true
                }
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                @Deprecated("kept for API compat")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == id) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            })
            cont.invokeOnCancellation { tts.stop(); _isSpeaking.value = false }
            val params = Bundle()
            tts.speak(text, TextToSpeech.QUEUE_ADD, params, id)
        }
    }

    fun stop() {
        tts.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
        _isReady.value = false
        _isSpeaking.value = false
    }
}
