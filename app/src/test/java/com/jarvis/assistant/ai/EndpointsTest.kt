package com.jarvis.assistant.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointsTest {
    @Test fun remoteEndpointsRequireHttpsEvenWithoutCredentials() {
        assertTrue(Endpoints.isTransportSafe("https://api.example.com/v1"))
        assertFalse(Endpoints.isTransportSafe("http://api.example.com/v1"))
        assertFalse(Endpoints.isTransportSafe("http://192.168.1.20:1234/v1"))
        assertFalse(Endpoints.isTransportSafe("http://10.0.0.5:8080/v1"))
    }

    @Test fun loopbackHttpRemainsAvailableForOnDeviceModels() {
        assertTrue(Endpoints.isTransportSafe("http://127.0.0.1:8080/v1"))
        assertTrue(Endpoints.isTransportSafe("http://localhost:8080/v1"))
    }

    @Test fun malformedAndCredentialBearingUrlsAreRejected() {
        assertFalse(Endpoints.isTransportSafe("not a valid endpoint"))
        assertFalse(Endpoints.isTransportSafe("https://user:password@example.com/v1"))
        assertFalse(Endpoints.isTransportSafe("ftp://api.example.com/model"))
    }

    @Test fun deceptiveNumericHostnamesAreNeverTreatedAsLocal() {
        assertFalse(Endpoints.isLoopback("https://127.attacker.example"))
        assertFalse(Endpoints.isLoopback("https://127.0.0.1.attacker.example"))
        assertFalse(Endpoints.isLocal("https://10.0.0.5.attacker.example"))
        assertFalse(Endpoints.isLocal("https://192.168.1.20.attacker.example"))
        assertFalse(Endpoints.isLoopback("https://127.999.0.1"))
    }

    @Test fun onlyCompletePrivateIpLiteralsAreTreatedAsLocal() {
        assertTrue(Endpoints.isLoopback("http://127.0.0.1:8080"))
        assertTrue(Endpoints.isLocal("http://10.0.0.5:8080"))
        assertTrue(Endpoints.isLocal("http://192.168.1.20:8080"))
        assertFalse(Endpoints.isLocal("https://example.local"))
    }

    @Test fun compatibilityHelperCannotBypassTransportPolicy() {
        assertFalse(Endpoints.isCredentialTransportSafe("http://192.168.1.20:1234/v1", false))
        assertFalse(Endpoints.isCredentialTransportSafe("http://api.example.com/v1", true))
        assertTrue(Endpoints.isCredentialTransportSafe("http://127.0.0.1:8080/v1", true))
    }
}
