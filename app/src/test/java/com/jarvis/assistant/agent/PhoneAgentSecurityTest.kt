package com.jarvis.assistant.agent

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAgentSecurityTest {
    @Test fun coordinateTapsAlwaysNeedSecondConfirmation() {
        assertTrue(requiresSecondConfirmation("tap_point", JSONObject(), "Tap at screen coordinates"))
    }

    @Test fun longPressAlwaysNeedsSecondConfirmation() {
        assertTrue(requiresSecondConfirmation("long_press", JSONObject(), "Long-press an element"))
    }

    @Test fun submitViaEnterNeedsSecondConfirmation() {
        assertTrue(requiresSecondConfirmation("type_text", JSONObject().put("press_enter", true), "Enter text"))
        assertFalse(requiresSecondConfirmation("type_text", JSONObject().put("press_enter", false), "Enter text"))
    }

    @Test fun dangerousButtonLabelsNeedSecondConfirmation() {
        assertTrue(requiresSecondConfirmation("tap", JSONObject(), "Tap element Send message in WhatsApp"))
        assertTrue(requiresSecondConfirmation("tap", JSONObject(), "Нажать элемент «Удалить»"))
        assertTrue(requiresSecondConfirmation("tap", JSONObject(), "Нажать элемент «Перевести деньги»"))
        assertTrue(requiresSecondConfirmation("tap", JSONObject(), "Нажать элемент «Подтвердить платёж»"))
    }

    @Test fun harmlessActionsDoNotGetExtraConfirmation() {
        assertFalse(requiresSecondConfirmation("scroll", JSONObject(), "Scroll down"))
        assertFalse(requiresSecondConfirmation("open_app", JSONObject(), "Open Settings"))
        assertFalse(requiresSecondConfirmation("tap", JSONObject(), "Tap element Home"))
    }
}
