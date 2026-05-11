package com.iris.ai

object GemmaConfig {
    // LiteRT-LM consumes .litertlm bundles. E2B is preferred default for snappy UX
    // on S21/A55; E4B is fallback if E2B is missing.
    val MODEL_CANDIDATES: List<Pair<String, String>> = listOf(
        "E2B" to "gemma-4-E2B-it.litertlm",
        "E4B" to "gemma-4-E4B-it.litertlm",
    )

    // Kept for backward compatibility with tests.
    const val PRIMARY_MODEL = "gemma-4-E2B-it.litertlm"
    const val FALLBACK_MODEL = "gemma-4-E4B-it.litertlm"

    const val MAX_NUM_IMAGES = 1
    const val MAX_TOKENS = 100
    const val TOP_K = 40
    const val TEMPERATURE = 0.3f

    const val IMG_LONGEST_EDGE = 512

    // Hard cap on the whole generation. Set generously: streaming TTS already starts
    // around the first-sentence mark, so we'd rather let the model finish than cut
    // the narration mid-sentence.
    const val INFERENCE_TIMEOUT_MS = 90_000L

    const val PROGRESS_TICK_INTERVAL_MS = 15_000L
}
