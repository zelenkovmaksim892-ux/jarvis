package com.jarvis.assistant.ai

import com.jarvis.assistant.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalServerManagerSecurityTest {
    @Test fun onlyTheInstallerOwnedTermuxScriptCanBeStarted() {
        assertTrue(LocalServerManager.isAllowedTermuxScriptPath(Settings.DEFAULT_TERMUX_SCRIPT))
        assertFalse(LocalServerManager.isAllowedTermuxScriptPath("/data/data/com.termux/files/home/evil.sh"))
        assertFalse(LocalServerManager.isAllowedTermuxScriptPath("/sdcard/start-llama.sh"))
        assertFalse(LocalServerManager.isAllowedTermuxScriptPath(""))
    }
}
