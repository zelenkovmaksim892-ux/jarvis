package com.jarvis.assistant.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAgentOutcomePolicyTest {
    @Test fun proseOnlyReplyNeverClaimsTaskSuccess() {
        val result = unconfirmedAgentResult("I have completed the task.")
        assertFalse(result.success)
        assertEquals("I have completed the task.", result.summary)
    }
    @Test fun emptyProseReplyReturnsExplicitUnconfirmedStatus() {
        val result = unconfirmedAgentResult("")
        assertFalse(result.success)
        assertTrue(result.summary.contains("did not confirm"))
    }
}
