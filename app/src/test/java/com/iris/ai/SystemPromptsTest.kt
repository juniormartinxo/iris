package com.iris.ai

import com.iris.ui.AppMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptsTest {

    @Test
    fun continuousPromptInstructsDirectionPrefix() {
        val prompt = SystemPrompts.forMode(AppMode.CONTINUOUS)
        assertTrue(
            "Continuous prompt should require a direction prefix",
            prompt.contains("À frente vejo") || prompt.contains("direção")
        )
    }

    @Test
    fun continuousPromptCapsLengthAtThreeSentences() {
        val prompt = SystemPrompts.forMode(AppMode.CONTINUOUS)
        assertTrue(
            "Should instruct max 3 sentences",
            prompt.contains("3 frases") || prompt.contains("três frases")
        )
    }

    @Test
    fun questionPromptInjectsUserQuestion() {
        val prompt = SystemPrompts.forMode(AppMode.QUESTION, "tem alguma escada à frente?")
        assertTrue(
            "Question must appear verbatim in the prompt",
            prompt.contains("tem alguma escada à frente?")
        )
    }

    @Test
    fun questionPromptWithoutQuestionFallsBackToGenericInstruction() {
        val prompt = SystemPrompts.forMode(AppMode.QUESTION, null)
        assertFalse(
            "Should not contain a literal null marker",
            prompt.contains("null")
        )
    }

    @Test
    fun readingPromptInstructsTextOrdering() {
        val prompt = SystemPrompts.forMode(AppMode.READING)
        assertTrue(
            "Reading prompt should describe top-to-bottom left-to-right ordering",
            prompt.contains("cima para baixo") && prompt.contains("esquerda para a direita")
        )
    }

    @Test
    fun readingPromptHandlesNoTextCase() {
        val prompt = SystemPrompts.forMode(AppMode.READING)
        assertTrue(
            "Should instruct what to say if no text is visible",
            prompt.contains("Não vejo texto") || prompt.contains("sem texto")
        )
    }

    @Test
    fun allPromptsRequirePortugueseResponse() {
        AppMode.values().forEach { mode ->
            val prompt = SystemPrompts.forMode(mode, "amostra")
            assertTrue(
                "Mode $mode prompt must request PT-BR responses",
                prompt.contains("português") || prompt.contains("Português")
            )
        }
    }

    @Test
    fun allPromptsHaveAmbiguousFrameInstruction() {
        AppMode.values().forEach { mode ->
            val prompt = SystemPrompts.forMode(mode, "amostra")
            assertTrue(
                "Mode $mode must instruct what to say for blank/uniform frames",
                prompt.contains("parede") || prompt.contains("teto") || prompt.contains("Reaponte")
            )
        }
    }
}
