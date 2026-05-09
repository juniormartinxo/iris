package com.iris.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.iris.ui.AppMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class GemmaManager(private val context: Context) {

    sealed interface ModelState {
        data object NotLoaded : ModelState
        data object Loading : ModelState
        data class Ready(val variant: String) : ModelState
        data class Error(val reason: ErrorReason) : ModelState
    }

    enum class ErrorReason {
        FILE_NOT_FOUND, OOM_DURING_LOAD, CORRUPT_MODEL, INIT_FAILED
    }

    private val _state = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private var llm: LlmInference? = null
    private var loadedVariant: String? = null
    @Volatile private var currentSession: LlmInferenceSession? = null

    suspend fun load() = withContext(Dispatchers.Default) {
        _state.value = ModelState.Loading
        val baseDir = context.getExternalFilesDir(null)
        if (baseDir == null || !baseDir.exists()) {
            _state.value = ModelState.Error(ErrorReason.FILE_NOT_FOUND)
            return@withContext
        }

        val candidates = listOf(
            "E2B" to File(baseDir, GemmaConfig.PRIMARY_MODEL),
            "E4B" to File(baseDir, GemmaConfig.FALLBACK_MODEL),
        ).filter { (_, f) -> f.exists() && f.length() > 0 }

        if (candidates.isEmpty()) {
            _state.value = ModelState.Error(ErrorReason.FILE_NOT_FOUND)
            return@withContext
        }

        var lastError: ErrorReason? = null
        for ((variant, file) in candidates) {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(file.absolutePath)
                    .setMaxNumImages(GemmaConfig.MAX_NUM_IMAGES)
                    .setMaxTokens(GemmaConfig.MAX_TOKENS)
                    .build()
                val instance = LlmInference.createFromOptions(context, options)
                llm = instance
                loadedVariant = variant
                _state.value = ModelState.Ready(variant)
                return@withContext
            } catch (oom: OutOfMemoryError) {
                lastError = ErrorReason.OOM_DURING_LOAD
                continue
            } catch (t: Throwable) {
                lastError = ErrorReason.INIT_FAILED
                continue
            }
        }
        _state.value = ModelState.Error(lastError ?: ErrorReason.INIT_FAILED)
    }

    fun cancelInference() {
        runCatching {
            currentSession?.cancelGenerateResponseAsync()
            currentSession?.close()
        }
        currentSession = null
    }

    suspend fun describe(
        bitmap: Bitmap,
        mode: AppMode,
        userQuestion: String? = null,
    ): Flow<String> = callbackFlow {
        val llmInstance = llm
        if (llmInstance == null) {
            close(IllegalStateException("Model not loaded"))
            return@callbackFlow
        }

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(GemmaConfig.TOP_K)
            .setTemperature(GemmaConfig.TEMPERATURE)
            .setGraphOptions(
                GraphOptions.builder().setEnableVisionModality(true).build()
            )
            .build()

        val session = LlmInferenceSession.createFromOptions(llmInstance, sessionOptions)
        currentSession = session
        val buffer = SentenceBuffer()

        try {
            session.addQueryChunk(SystemPrompts.forMode(mode, userQuestion))
            session.addImage(BitmapImageBuilder(bitmap).build())

            session.generateResponseAsync { partial, done ->
                buffer.feed(partial) { sentence -> trySend(sentence) }
                if (done) {
                    buffer.flush { sentence -> trySend(sentence) }
                    close()
                }
            }

            awaitClose {
                runCatching { session.cancelGenerateResponseAsync() }
                runCatching { session.close() }
                if (currentSession === session) currentSession = null
            }
        } catch (t: Throwable) {
            runCatching { session.close() }
            if (currentSession === session) currentSession = null
            close(t)
        }
    }.flowOn(Dispatchers.Default)
}
