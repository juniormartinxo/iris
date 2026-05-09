package com.iris.ai

class SentenceBuffer {

    private val terminators = setOf('.', '?', '!', '\n')
    private val builder = StringBuilder()

    fun feed(chunk: String, onSentence: (String) -> Unit) {
        for (c in chunk) {
            builder.append(c)
            if (c in terminators) {
                emit(onSentence)
            }
        }
    }

    fun flush(onSentence: (String) -> Unit) {
        if (builder.isNotBlank()) {
            emit(onSentence)
        } else {
            builder.setLength(0)
        }
    }

    private fun emit(onSentence: (String) -> Unit) {
        val sentence = builder.toString().trim()
        builder.setLength(0)
        if (sentence.isNotEmpty()) {
            onSentence(sentence)
        }
    }
}
