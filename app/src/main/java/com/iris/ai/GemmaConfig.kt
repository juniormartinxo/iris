package com.iris.ai

object GemmaConfig {
    const val PRIMARY_MODEL = "gemma-4-E2B-it.litertlm"
    const val FALLBACK_MODEL = "gemma-4-E4B-it.litertlm"

    const val MAX_NUM_IMAGES = 1
    const val MAX_TOKENS = 100
    const val TOP_K = 40
    const val TEMPERATURE = 0.3f

    const val IMG_LONGEST_EDGE = 512
    const val INFERENCE_TIMEOUT_MS = 30_000L
}
