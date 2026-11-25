package com.hebi.tripcanvas.data.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapProxyTransportTest {
    private val origin = "https://maps.tripcanvas.example.com"
    private val endpoint = "$origin/v1/places"
    private val now = 10_000L
    private val payload = "{}".toByteArray()

    @Test fun unapprovedEndpointOrPathIsRejected() {
        assertTrue(MapProxyEndpointPolicy.approved(endpoint, "/v1/places"))
        listOf("http://maps.tripcanvas.example.com/v1/places",
            "$origin/v1/places?x=1", "$origin/v1/places#fragment",
            "$origin/v1%2fplaces", "$origin:443/v1/places",
            "https://photon.komoot.io/v1/places", "https://api.heigit.org/v1/places",
            "https://127.0.0.1/v1/places", "https://maps.local/v1/places")
            .forEach { assertFalse(MapProxyEndpointPolicy.approved(it, "/v1/places")) }
        assertFalse(MapProxyEndpointPolicy.approved(endpoint, "/v1/routes"))
    }

    @Test fun missingExpiredWrongAudienceOrMalformedCredentialNeverOpensConnection() = runBlocking {
        val invalid = listOf<MapProxySession?>(null,
            MapProxySession(origin, "fixture", now),
            MapProxySession("https://other.example.com", "fixture", now + 1000),
            MapProxySession(origin, "fixture\r\nheader", now + 1000),
            MapProxySession(origin, "", now + 1000))
        for (session in invalid) {
            var opened = false
            val transport = MapProxyTransport(endpoint, "/v1/places",
                MapProxySessionProvider { session }, { now }) {
                opened = true
                error("must not open")
            }
            assertTrue(runCatching { transport.post(payload, 1024) }.exceptionOrNull() is MapProxyAuthorizationUnavailable)
            assertFalse(opened)
        }
    }

    @Test fun freshOriginBoundSessionAndBoundedBodyForEachExplicitRequest() = runBlocking {
        val opened = mutableListOf<FakeConnection>()
        var sessionCalls = 0
        val session = MapProxySession(origin, "fixture-token", now + 1000)
        assertFalse(session.toString().contains("fixture-token"))
        val transport = MapProxyTransport(endpoint, "/v1/places",
            MapProxySessionProvider { requested ->
                assertEquals(origin, requested)
                sessionCalls++
                session
            }, { now }) { url -> FakeConnection(url, 200, "{}".toByteArray()).also(opened::add) }
        repeat(2) { assertEquals("{}", transport.post(payload, 1024).body) }
        assertEquals(2, sessionCalls)
        assertEquals(2, opened.size)
        for (connection in opened) {
            assertEquals("POST", connection.requestMethod)
            assertEquals("Bearer fixture-token", connection.getRequestProperty("Authorization"))
            assertEquals("{}", connection.sent.toString("UTF-8"))
            assertFalse(connection.instanceFollowRedirects)
            assertFalse(connection.useCaches)
            assertTrue(connection.disconnected)
        }
    }

    @Test fun errorBodiesAndRedirectsAreNotReadAndLargeResponsesAreRejected() = runBlocking {
        val sessions = MapProxySessionProvider { MapProxySession(origin, "fixture", now + 1000) }
        for (status in listOf(302, 401, 403, 429, 500)) {
            lateinit var connection: FakeConnection
            val transport = MapProxyTransport(endpoint, "/v1/places", sessions, { now }) {
                FakeConnection(it, status, "internal details".toByteArray()).also { c -> connection = c }
            }
            val result = transport.post(payload, 1024)
            assertEquals(status, result.status)
            assertNull(result.body)
            assertFalse(connection.inputOpened)
            assertTrue(connection.disconnected)
            assertEquals(if (status == 429) "120" else null, result.retryAfter)
        }
        lateinit var large: FakeConnection
        val transport = MapProxyTransport(endpoint, "/v1/places", sessions, { now }) {
            FakeConnection(it, 200, ByteArray(9)).also { c -> large = c }
        }
        assertTrue(runCatching { transport.post(payload, 8) }.isFailure)
        assertTrue(large.disconnected)
    }

    private class FakeConnection(url: URL, private val status: Int, private val body: ByteArray) :
        HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        var disconnected = false
        var inputOpened = false
        override fun connect() {}
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun getOutputStream() = sent
        override fun getResponseCode(): Int = status
        override fun getInputStream(): ByteArrayInputStream {
            inputOpened = true
            return ByteArrayInputStream(body)
        }
        override fun getHeaderField(name: String?): String? =
            if (name == "Retry-After") "120" else null
    }
}
