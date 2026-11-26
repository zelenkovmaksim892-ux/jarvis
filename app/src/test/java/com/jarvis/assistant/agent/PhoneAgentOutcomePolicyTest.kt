package com.jarvis.assistant.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhoneAgentOutcomePolicyTest {
    @Test
    fun proseOnlyReplyNeverClaimsTaskSuccess() {
        val result = unconfirmedAgentResult("I have completed the task.")
        assertFalse(result.success)
        assertEquals("I have completed the task.", result.summary)
    }

    @Test
    fun emptyProseReplyReturnsExplicitUnconfirmedStatus() {
        val result = unconfirmedAgentResult("")
        assertFalse(result.success)
        assertTrueMessage(result.summary)
    }

    private fun assertTrueMessage(message: String) {
        org.junit.Assert.assertTrue(message.contains("did not confirm"))
    }
}
