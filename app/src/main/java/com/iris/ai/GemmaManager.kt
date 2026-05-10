package com.iris.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.iris.ui.AppMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

    private var engine: Engine? = null
    private var loadedVariant: String? = null
    @Volatile private var currentConversation: Conversation? = null

    suspend fun load() = withContext(Dispatchers.Default) {
        _state.value = ModelState.Loading
        val baseDir = context.getExternalFilesDir(null)
        if (baseDir == null || !baseDir.exists()) {
            _state.value = ModelState.Error(ErrorReason.FILE_NOT_FOUND)
            return@withContext
        }

        val candidates = GemmaConfig.MODEL_CANDIDATES
            .map { (variant, name) -> variant to File(baseDir, name) }
            .filter { (_, f) -> f.exists() && f.length() > 0 }

        if (candidates.isEmpty()) {
            _state.value = ModelState.Error(ErrorReason.FILE_NOT_FOUND)
            return@withContext
        }

        Log.i(TAG, "Found ${candidates.size} candidate model(s): ${candidates.map { it.first }}")
        var lastError: ErrorReason? = null
        for ((variant, file) in candidates) {
            try {
                Log.i(TAG, "Trying to load $variant from ${file.absolutePath} (${file.length()} bytes)")
                val config = EngineConfig(
                    modelPath = file.absolutePath,
                    backend = Backend.CPU(),
                    visionBackend = Backend.CPU(),
                    maxNumImages = GemmaConfig.MAX_NUM_IMAGES,
                )
                val instance = Engine(config)
                instance.initialize()
                engine = instance
                loadedVariant = variant
                _state.value = ModelState.Ready(variant)
                Log.i(TAG, "Loaded $variant successfully")
                return@withContext
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM loading $variant", oom)
                lastError = ErrorReason.OOM_DURING_LOAD
                continue
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load $variant", t)
                lastError = ErrorReason.INIT_FAILED
                continue
            }
        }
        _state.value = ModelState.Error(lastError ?: ErrorReason.INIT_FAILED)
    }

    companion object {
        private const val TAG = "Iris"
    }

    fun cancelInference() {
        val conv = currentConversation ?: return
        currentConversation = null
        runCatching { conv.cancelProcess() }
        runCatching { conv.close() }
    }

    suspend fun describe(
        bitmap: Bitmap,
        mode: AppMode,
        userQuestion: String? = null,
    ): Flow<String> = callbackFlow {
        val instance = engine
        if (instance == null) {
            close(IllegalStateException("Model not loaded"))
            return@callbackFlow
        }

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val imageBytes = baos.toByteArray()

        val conversation = instance.createConversation()
        currentConversation = conversation
        val buffer = SentenceBuffer()

        val job = launch {
            runCatching {
                val prompt = SystemPrompts.forMode(mode, userQuestion)
                val request = Message.user(
                    Contents.of(
                        Content.ImageBytes(imageBytes),
                        Content.Text(prompt),
                    )
                )
                conversation.sendMessageAsync(request).collect { reply ->
                    val text = reply.toString()
                    if (text.isNotEmpty()) {
                        buffer.feed(text) { sentence -> trySend(sentence) }
                    }
                }
                buffer.flush { sentence -> trySend(sentence) }
            }.onFailure { t ->
                close(t)
                return@onFailure
            }
            close()
        }

        awaitClose {
            job.cancel()
            runCatching { conversation.cancelProcess() }
            runCatching { conversation.close() }
            if (currentConversation === conversation) currentConversation = null
        }
    }.flowOn(Dispatchers.Default)
}
