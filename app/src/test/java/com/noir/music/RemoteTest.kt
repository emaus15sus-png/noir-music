package com.noir.music

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteTest {
    private fun req(method: String, vararg headers: String) = listOf("$method /next HTTP/1.1") + headers

    @Test
    fun onlyTheRemotePageGetsIn() {
        // The PC remote: TV's IP as Host, X-Noir on commands
        assertNull(refusal(req("GET", "Host: 192.168.1.128:8686")))
        assertNull(refusal(req("POST", "Host: 192.168.1.128:8686", "X-Noir: 1")))
        assertNull(refusal(req("POST", "host: localhost:8686", "x-noir: 1")))

        // Another website's form or fetch: no X-Noir
        assertNotNull(refusal(req("POST", "Host: 192.168.1.128:8686")))
        // DNS rebinding: the attacker's domain as Host, even with X-Noir
        assertNotNull(refusal(req("POST", "Host: evil.example:8686", "X-Noir: 1")))
        assertNotNull(refusal(req("GET", "Host: 192.168.1.128.evil.example")))
        assertNotNull(refusal(req("GET"))) // no Host at all
    }
}
