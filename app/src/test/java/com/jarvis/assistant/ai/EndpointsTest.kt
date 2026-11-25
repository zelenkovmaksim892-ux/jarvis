package com.jarvis.assistant.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointsTest {
    @Test fun credentialsRequireHttpsForRemoteEndpoints() {
        assertTrue(Endpoints.isCredentialTransportSafe("https://api.example.com/v1", true))
        assertFalse(Endpoints.isCredentialTransportSafe("http://api.example.com/v1", true))
        assertFalse(Endpoints.isCredentialTransportSafe("http://192.168.1.20:1234/v1", true))
    }

    @Test fun loopbackHttpRemainsAvailableForOnDeviceModels() {
        assertTrue(Endpoints.isCredentialTransportSafe("http://127.0.0.1:8080/v1", true))
        assertTrue(Endpoints.isCredentialTransportSafe("http://localhost:8080/v1", true))
    }

    @Test fun endpointsWithoutCredentialsCanUseLocalHttp() {
        assertTrue(Endpoints.isCredentialTransportSafe("http://192.168.1.20:1234/v1", false))
        assertTrue(Endpoints.isCredentialTransportSafe("http://127.0.0.1:8080/v1", false))
    }

    @Test fun malformedEndpointCannotCarryCredentials() {
        assertFalse(Endpoints.isCredentialTransportSafe("not a valid endpoint", true))
    }
}
