package com.jarvis.assistant.research

import org.junit.Assert.assertThrows
import org.junit.Test

class SearxngSearchProviderTest {
    @Test
    fun requiresHttpsInstance() {
        assertThrows(IllegalArgumentException::class.java) {
            SearxngSearchProvider("http://search.example")
        }
    }

    @Test
    fun rejectsCredentialsAndQueryInInstanceUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            SearxngSearchProvider("https://user:pass@search.example")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SearxngSearchProvider("https://search.example/?token=secret")
        }
    }

    @Test
    fun rejectsNonStandardPortsAndFragments() {
        assertThrows(IllegalArgumentException::class.java) {
            SearxngSearchProvider("https://search.example:8443")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SearxngSearchProvider("https://search.example/#admin")
        }
    }

    @Test
    fun validatesQueryAndResultLimitBeforeNetworkAccess() {
        val provider = SearxngSearchProvider("https://search.example")
        assertThrows(IllegalArgumentException::class.java) { provider.search(" ", 5) }
        assertThrows(IllegalArgumentException::class.java) { provider.search("firmware", 0) }
        assertThrows(IllegalArgumentException::class.java) { provider.search("firmware", 11) }
    }
}
