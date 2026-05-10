package com.iris.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iris.ai.GemmaConfig
import com.iris.ai.GemmaManager
import com.iris.audio.SpeechManager
import com.iris.audio.TtsManager
import com.iris.camera.CameraManager
import com.iris.camera.FrameQuality
import com.iris.util.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AppViewModel(
    application: Application,
    private val gemma: GemmaManager,
    private val tts: TtsManager,
    private val camera: CameraManager,
    private val speech: SpeechManager,
    private val prefs: Prefs,
) : AndroidViewModel(application) {

    private val TAG = "Iris"

    val cameraManager: CameraManager get() = camera

    private val _state = MutableStateFlow(
        AppState(tutorialDone = prefs.tutorialDone, preflightDone = prefs.preflightDone)
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var inFlightJob: Job? = null

    init {
        viewModelScope.launch {
            gemma.state.collect { gs ->
                _state.update { current ->
                    when (gs) {
                        is GemmaManager.ModelState.NotLoaded ->
                            current.copy(phase = AppPhase.LoadingModel, modelVariant = null)
                        is GemmaManager.ModelState.Loading ->
                            current.copy(phase = AppPhase.LoadingModel)
                        is GemmaManager.ModelState.Ready ->
                            current.copy(phase = AppPhase.Idle, modelVariant = gs.variant)
                        is GemmaManager.ModelState.Error ->
                            current.copy(
                                phase = AppPhase.FatalError("Erro ao carregar modelo: ${gs.reason}"),
                                modelVariant = null,
                            )
                    }
                }
            }
        }
        viewModelScope.launch { gemma.load() }
    }

    fun selectMode(mode: AppMode) {
        if (isBusy(_state.value.phase)) return
        _state.update { it.copy(mode = mode) }
    }

    fun trigger() {
        if (isThermallyCritical()) {
            viewModelScope.launch {
                tts.speak("O aparelho está aquecendo. Aguarde alguns segundos e tente de novo.")
            }
            return
        }

        val current = _state.value
        if (current.phase is AppPhase.LoadingModel ||
            current.phase is AppPhase.FatalError) {
            return
        }
        if (isBusy(current.phase)) {
            viewModelScope.launch { tts.speak(BUSY_ALERT) }
            return
        }

        inFlightJob = viewModelScope.launch {
            runCatching {
                tts.stop()
                if (current.mode == AppMode.QUESTION) {
                    _state.update { it.copy(phase = AppPhase.Listening) }
                    tts.speak("Faça sua pergunta.")
                    val sr = speech.listen()
                    when (sr) {
                        is SpeechManager.SpeechResult.Recognized ->
                            runDescribe(question = sr.text)
                        is SpeechManager.SpeechResult.NoInput -> {
                            tts.speak("Não entendi. Toque duas vezes para falar de novo.")
                            _state.update { it.copy(phase = AppPhase.Idle) }
                        }
                        is SpeechManager.SpeechResult.NoOfflineModel -> {
                            tts.speak(
                                "O reconhecimento de voz offline em português ainda não está " +
                                "instalado neste celular. Vou abrir as configurações."
                            )
                            _state.update { it.copy(phase = AppPhase.Idle) }
                        }
                        is SpeechManager.SpeechResult.Error -> {
                            tts.speak("Erro no reconhecimento de voz. Tente de novo.")
                            _state.update { it.copy(phase = AppPhase.Idle) }
                        }
                    }
                } else {
                    runDescribe(question = null)
                }
            }.onFailure { t ->
                if (t !is CancellationException) {
                    tts.speak("Erro inesperado. Tente de novo.")
                    _state.update { it.copy(phase = AppPhase.Idle) }
                }
            }
        }
    }

    private suspend fun runDescribe(question: String?) {
        _state.update { it.copy(phase = AppPhase.Capturing) }
        tts.speak("Analisando.")

        val frame = runCatching { camera.captureFrame() }.getOrElse { t ->
            Log.e(TAG, "captureFrame failed", t)
            tts.speak("Erro na câmera. Tente de novo.")
            _state.update { it.copy(phase = AppPhase.Idle) }
            return
        }
        Log.i(TAG, "Captured frame ${frame.width}x${frame.height}")

        val q = camera.assess(frame)
        val complaint = qualityComplaint(q)
        if (complaint != null) {
            tts.speak(complaint)
            _state.update { it.copy(phase = AppPhase.Idle) }
            return
        }

        _state.update { it.copy(phase = AppPhase.Inferring) }
        val collected = StringBuilder()
        runCatching {
            withTimeout(GemmaConfig.INFERENCE_TIMEOUT_MS) {
                gemma.describe(frame, _state.value.mode, question).collect { sentence ->
                    collected.append(sentence).append(' ')
                    viewModelScope.launch { tts.speak(sentence) }
                }
            }
        }.onFailure { t ->
            Log.e(TAG, "Inference failed", t)
            when (t) {
                is TimeoutCancellationException ->
                    tts.speak("Demorando demais, toque duas vezes para tentar de novo.")
                is OutOfMemoryError ->
                    tts.speak("Memória cheia. Aguarde dez segundos e toque de novo.")
                !is CancellationException ->
                    tts.speak("Erro durante a análise. Toque duas vezes para tentar de novo.")
            }
            gemma.cancelInference()
        }

        _state.update {
            it.copy(
                phase = AppPhase.Idle,
                lastDescription = collected.toString().trim(),
            )
        }
    }

    private fun qualityComplaint(q: FrameQuality): String? = when {
        q.brightness < FrameQuality.BRIGHTNESS_TOO_DARK ->
            "Imagem muito escura. Verifique a iluminação ou se há algo cobrindo a câmera."
        q.brightness > FrameQuality.BRIGHTNESS_TOO_BRIGHT ->
            "Imagem muito clara. Há luz forte direta na câmera."
        q.variance < FrameQuality.VARIANCE_TOO_LOW ->
            "Não vejo nada com detalhes. A câmera pode estar apontada para uma parede ou superfície vazia."
        q.blurScore < FrameQuality.BLUR_TOO_LOW ->
            "Imagem desfocada. Segure o celular firme e tente de novo."
        else -> null
    }

    private fun isThermallyCritical(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val pm = getApplication<Application>()
            .getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL
    }

    private fun isBusy(phase: AppPhase): Boolean =
        phase is AppPhase.Capturing ||
        phase is AppPhase.Inferring ||
        phase is AppPhase.Listening

    fun repeat() {
        val current = _state.value
        if (current.phase is AppPhase.LoadingModel ||
            current.phase is AppPhase.FatalError) {
            return
        }
        if (isBusy(current.phase)) {
            viewModelScope.launch { tts.speak(BUSY_ALERT) }
            return
        }
        val last = current.lastDescription
        if (last.isBlank()) {
            viewModelScope.launch {
                tts.speak("Nada para repetir. Faça uma análise primeiro.")
            }
            return
        }
        viewModelScope.launch {
            tts.stop()
            tts.speak(last)
        }
    }

    fun retryLoad() {
        viewModelScope.launch { gemma.load() }
    }

    fun markTutorialDone() {
        prefs.tutorialDone = true
        _state.update { it.copy(tutorialDone = true) }
    }

    fun markPreflightDone() {
        prefs.preflightDone = true
        _state.update { it.copy(preflightDone = true) }
    }

    companion object {
        private const val BUSY_ALERT =
            "Aguarde a análise anterior terminar."
    }
}
