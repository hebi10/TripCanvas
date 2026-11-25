package com.hebi.tripcanvas.data.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Syntax guard only. The host and its authentication provider must also be approved. */
internal object MapProxyEndpointPolicy {
    fun approved(url: String, path: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return@runCatching false
        path in setOf("/v1/places", "/v1/routes") &&
            uri.scheme == "https" && uri.rawAuthority.equals(host, ignoreCase = true) &&
            uri.userInfo == null && uri.port == -1 &&
            uri.rawQuery == null && uri.rawFragment == null && uri.rawPath == path &&
            host.matches(Regex("[a-z0-9-]+(\\.[a-z0-9-]+)+")) &&
            !host.matches(Regex("[0-9]+(\\.[0-9]+){3}")) &&
            !listOf(".local", ".internal", ".test", ".example", ".localhost").any(host::endsWith) &&
            !host.startsWith("localhost.") &&
            host != "photon.komoot.io" && host != "api.openrouteservice.org" &&
            host != "api.heigit.org" && host != "maps.googleapis.com" &&
            host != "routes.googleapis.com" && host != "places.googleapis.com"
    }.getOrDefault(false)

    fun origin(url: String): String = "https://" + requireNotNull(URI(url).host).lowercase(Locale.ROOT)
}

/**
 * Short-lived backend access credential. Never persist or include it in log messages.
 * The approved authentication adapter must bind the credential to this exact origin.
 * Server-side verification, authorization and quotas remain mandatory.
 */
class MapProxySession(
    private val audienceOrigin: String,
    private val bearerToken: String,
    private val expiresAtMillis: Long
) {
    internal fun authorizationFor(origin: String, now: Long): String? {
        if (audienceOrigin != origin || now <= 0L || expiresAtMillis <= now ||
            bearerToken.length !in 1..8192 || !bearerToken.matches(BEARER_TOKEN)
        ) return null
        return "Bearer " + bearerToken
    }

    override fun toString(): String = "MapProxySession(redacted)"

    private companion object {
        val BEARER_TOKEN = Regex("[A-Za-z0-9._~+/\\-]+=*")
    }
}

fun interface MapProxySessionProvider {
    suspend fun sessionFor(origin: String): MapProxySession?
}

internal class MapProxyAuthorizationUnavailable :
    IOException("운영 지도 서버 인증이 필요합니다.")

internal data class MapProxyResponse(val status: Int, val body: String?, val retryAfter: String? = null)

/** No provider key in the APK, no redirects, no app-level retry, no error-body exposure. */
internal class MapProxyTransport(
    private val endpoint: String,
    path: String,
    private val sessions: MapProxySessionProvider,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpsURLConnection }
) {
    init { require(MapProxyEndpointPolicy.approved(endpoint, path)) }
    private val origin = MapProxyEndpointPolicy.origin(endpoint)

    suspend fun post(payload: ByteArray, maxResponseBytes: Int): MapProxyResponse = withContext(Dispatchers.IO) {
        require(payload.size in 1..4096 && maxResponseBytes in 1..256 * 1024)
        val authorization = try {
            sessions.sessionFor(origin)?.authorizationFor(origin, clockMillis())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: throw MapProxyAuthorizationUnavailable()
        val connection = openConnection(URI(endpoint).toURL())
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 7_000
            connection.readTimeout = 7_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(payload.size)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authorization)
            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            if (status != 200) {
                return@withContext MapProxyResponse(
                    status, null, if (status == 429) connection.getHeaderField("Retry-After")?.take(100) else null
                )
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(count > 0 && count <= maxResponseBytes - output.size()) {
                        "지도 응답의 허용 용량을 초과했습니다."
                    }
                    output.write(buffer, 0, count)
                }
            }
            val body = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray())).toString()
            MapProxyResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }
}
