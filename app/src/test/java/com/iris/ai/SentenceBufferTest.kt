package com.iris.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceBufferTest {

    @Test
    fun emitsNothingWithoutTerminator() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Olá", onSentence = { emissions += it })
        buffer.feed(" mundo", onSentence = { emissions += it })
        assertEquals(emptyList<String>(), emissions)
    }

    @Test
    fun emitsAtPeriod() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Olá mundo. Como vai", onSentence = { emissions += it })
        assertEquals(listOf("Olá mundo."), emissions)
    }

    @Test
    fun emitsAtQuestionMark() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Tudo bem? Sim", onSentence = { emissions += it })
        assertEquals(listOf("Tudo bem?"), emissions)
    }

    @Test
    fun emitsAtExclamation() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Cuidado! À frente", onSentence = { emissions += it })
        assertEquals(listOf("Cuidado!"), emissions)
    }

    @Test
    fun emitsMultipleSentencesInSingleFeed() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Frase um. Frase dois! Frase três? Cauda", onSentence = { emissions += it })
        assertEquals(
            listOf("Frase um.", "Frase dois!", "Frase três?"),
            emissions
        )
    }

    @Test
    fun flushEmitsLeftoverWithoutTerminator() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Olá mundo sem ponto", onSentence = { emissions += it })
        buffer.flush(onSentence = { emissions += it })
        assertEquals(listOf("Olá mundo sem ponto"), emissions)
    }

    @Test
    fun flushEmitsNothingIfBufferEmpty() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Frase.", onSentence = { emissions += it })
        buffer.flush(onSentence = { emissions += it })
        assertEquals(listOf("Frase."), emissions)
    }

    @Test
    fun trimsLeadingWhitespaceOfSubsequentSentences() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Um.   Dois.", onSentence = { emissions += it })
        assertEquals(listOf("Um.", "Dois."), emissions)
    }
}
