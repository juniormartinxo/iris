package com.iris.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class SpeechManager(private val context: Context) {

    sealed interface SpeechResult {
        data class Recognized(val text: String) : SpeechResult
        data object NoInput : SpeechResult
        data object NoOfflineModel : SpeechResult
        data class Error(val message: String) : SpeechResult
    }

    private var recognizer: SpeechRecognizer? = null

    fun isOfflineRecognitionAvailable(): Boolean =
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    suspend fun listen(timeoutMs: Long = LISTEN_TIMEOUT_MS): SpeechResult =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<SpeechResult> { cont ->
                if (!isOfflineRecognitionAvailable()) {
                    if (cont.isActive) cont.resume(SpeechResult.NoOfflineModel)
                    return@suspendCancellableCoroutine
                }

                val rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                recognizer = rec
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1500L)
                }

                rec.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        val result = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechResult.NoInput
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> SpeechResult.NoOfflineModel
                            else -> SpeechResult.Error("Erro de reconhecimento: $error")
                        }
                        cleanup()
                        if (cont.isActive) cont.resume(result)
                    }

                    override fun onResults(results: Bundle?) {
                        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = list?.firstOrNull()?.takeIf { it.isNotBlank() }
                        cleanup()
                        if (cont.isActive) {
                            cont.resume(
                                if (text != null) SpeechResult.Recognized(text)
                                else SpeechResult.NoInput
                            )
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                cont.invokeOnCancellation { cleanup() }
                rec.startListening(intent)
            }
        } ?: SpeechResult.NoInput

    fun cancel() {
        cleanup()
    }

    private fun cleanup() {
        val r = recognizer ?: return
        recognizer = null
        // SpeechRecognizer must be touched only on the main thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyRecognizer(r)
        } else {
            Handler(Looper.getMainLooper()).post { destroyRecognizer(r) }
        }
    }

    private fun destroyRecognizer(r: SpeechRecognizer) {
        runCatching { r.stopListening() }
        runCatching { r.cancel() }
        runCatching { r.destroy() }
    }

    companion object {
        const val LISTEN_TIMEOUT_MS = 10_000L
    }
}
