package com.jarvis.assistant.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerIntegrityPolicyTest {
    @Test
    fun appendsIntegrityRulesWithoutDroppingExistingPrompt() {
        val result = AnswerIntegrityPolicy.apply("You are Jarvis.")
        assertTrue(result.startsWith("You are Jarvis."))
        assertTrue(result.contains("Never invent a source"))
        assertTrue(result.contains("This policy does not grant permission"))
    }

    @Test
    fun applyingPolicyTwiceDoesNotDuplicateIt() {
        val once = AnswerIntegrityPolicy.apply("You are Jarvis.")
        assertEquals(once, AnswerIntegrityPolicy.apply(once))
    }

    @Test
    fun emptyPromptStillGetsIntegrityRules() {
        assertTrue(AnswerIntegrityPolicy.apply("").contains("ANSWER INTEGRITY AND EVIDENCE RULES"))
    }
}
