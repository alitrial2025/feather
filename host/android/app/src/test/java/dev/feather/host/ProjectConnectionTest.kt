package dev.feather.host

import org.junit.Assert.*
import org.junit.Test

class ProjectConnectionTest {
    private val key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    @Test fun pairingPreservesHttpsPathPortAndKey() {
        val connection = ProjectConnection.parse("feather://open?url=https%3A%2F%2Fdemo.test%3A8443%2Fsession%2Fabc%2F&key=$key&name=Demo", true)
        assertEquals("https://demo.test:8443/session/abc", connection.baseUrl)
        assertEquals("wss://demo.test:8443/session/abc/ws", connection.socketUrl)
        assertEquals(key, connection.publicKey)
    }
    @Test fun localAddressGetsDefaultPort() {
        assertEquals("http://192.168.1.7:8080", ProjectConnection.parse("192.168.1.7", true).baseUrl)
        assertEquals("ws://localhost:9000/ws", ProjectConnection.parse("localhost:9000", true).socketUrl)
    }
    @Test fun malformedAndReleasePairingAreRejected() {
        for (input in listOf("file:///tmp/app", "https://user:pass@demo.test", "https://demo.test?url=other", "https://demo.test/../secret", "https://demo.test/%2e%2e/secret", "feather://open?url=https%3A%2F%2Fdemo.test&key=bad")) {
            assertTrue(input, runCatching { ProjectConnection.parse(input, true) }.isFailure)
        }
        assertTrue(runCatching { ProjectConnection.parse("feather://open?url=https%3A%2F%2Fdemo.test&key=$key", false) }.isFailure)
    }
}
