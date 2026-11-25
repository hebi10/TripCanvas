package com.hebi.tripcanvas.data.route

import android.os.SystemClock
import com.hebi.tripcanvas.BuildConfig
import com.hebi.tripcanvas.data.network.ExpiringQueryCache
import com.hebi.tripcanvas.data.network.QueryThrottle
import com.hebi.tripcanvas.data.network.QueryRateLimitedException
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.domain.TripRouteEstimate
import com.hebi.tripcanvas.domain.TripRouteMode
import com.hebi.tripcanvas.domain.TripRouteProvider
import com.hebi.tripcanvas.domain.validatedRouteEstimate
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Routes are requested only through an explicitly configured, access-controlled HTTPS proxy.
 * The app never ships a commercial routing API key or queries public demo endpoints.
 */
object RouteEndpointPolicy {
    fun approved(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return@runCatching false
        uri.scheme == "https" &&
            uri.userInfo == null &&
            uri.port == -1 &&
            uri.query == null &&
            uri.fragment == null &&
            uri.path == "/v1/routes" &&
            host.matches(Regex("""[a-z0-9-]+(\.[a-z0-9-]+)+""")) &&
            !host.matches(Regex("""[0-9]+(\.[0-9]+){3}""")) &&
            !host.endsWith(".local") &&
            !host.endsWith(".internal") &&
            !host.endsWith(".test") &&
            !host.endsWith(".example") &&
            !host.endsWith(".localhost") &&
            !host.startsWith("localhost.")
    }.getOrDefault(false)
}

class ConfiguredTripRouteProvider private constructor(
    private val endpoint: URL
) : TripRouteProvider {
    private val mutex = Mutex()
    private val cached = ExpiringQueryCache<String, TripRouteEstimate>(64, 600_000L)
    private val throttle = QueryThrottle(1_200L)

    override suspend fun estimate(
        from: PlaceEntity,
        to: PlaceEntity,
        mode: TripRouteMode
    ): TripRouteEstimate? {
        val fromLat = from.latitude ?: return null
        val fromLon = from.longitude ?: return null
        val toLat = to.latitude ?: return null
        val toLon = to.longitude ?: return null
        if (!validCoordinates(fromLat, fromLon) || !validCoordinates(toLat, toLon)) return null
        val key = listOf(fromLat, fromLon, toLat, toLon, mode.name).joinToString("|")
        return mutex.withLock {
            val now = SystemClock.elapsedRealtime()
            cached[key, now]?.takeIf { isFreshRouteEstimate(it, System.currentTimeMillis()) }
                ?.let { return@withLock it }
            cached.remove(key)
            throttle.requireAvailable(now)
            val pause = throttle.delayMillis(now)
            if (pause > 0) delay(pause)
            throttle.started(SystemClock.elapsedRealtime())
            val result = withContext(Dispatchers.IO) {
                request(fromLat, fromLon, toLat, toLon, mode)
            }
            if (result != null) cached.put(key, result, SystemClock.elapsedRealtime())
            result
        }
    }

    private fun request(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double,
        mode: TripRouteMode
    ): TripRouteEstimate? {
        val payload = JSONObject()
            .put("origin", JSONObject().put("latitude", fromLat).put("longitude", fromLon))
            .put("destination", JSONObject().put("latitude", toLat).put("longitude", toLon))
            .put("mode", mode.name)
            .toString().toByteArray(StandardCharsets.UTF_8)

        val connection = endpoint.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 7_000
        connection.readTimeout = 7_000
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        try {
            connection.outputStream.use { it.write(payload) }
            when (connection.responseCode) {
                200 -> Unit
                204, 404, 422 -> return null
                401, 403 -> throw IOException("경로 서버의 접근 권한을 확인할 수 없습니다.")
                429 -> {
                    throttle.rateLimited(SystemClock.elapsedRealtime(), connection.getHeaderField("Retry-After"),
                        System.currentTimeMillis())
                    throw QueryRateLimitedException()
                }
                else -> throw IOException("경로 서버에 연결할 수 없습니다.")
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(2048)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    if (output.size() + size > 8 * 1024) throw IOException("경로 응답 크기를 초과했습니다.")
                    output.write(buffer, 0, size)
                }
            }
            val json = JSONObject(output.toString(StandardCharsets.UTF_8.name()))
            fun integer(key: String): Long = when (val value = json.get(key)) {
                is Int -> value.toLong()
                is Long -> value
                else -> throw IOException("경로 응답의 숫자 형식이 올바르지 않습니다.")
            }
            val result = TripRouteEstimate(
                distanceMeters = integer("distanceMeters"),
                durationSeconds = integer("durationSeconds"),
                provider = json.get("provider") as? String
                    ?: throw IOException("경로 응답의 공급자 형식이 올바르지 않습니다."),
                queriedAtMillis = integer("queriedAtMillis")
            )
            return result.takeIf { isFreshRouteEstimate(it, System.currentTimeMillis()) }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        fun fromBuildConfig(): ConfiguredTripRouteProvider? =
            BuildConfig.ROUTE_PROXY_URL.takeIf(RouteEndpointPolicy::approved)?.let {
                ConfiguredTripRouteProvider(URI(it).toURL())
            }

        fun validCoordinates(latitude: Double, longitude: Double): Boolean =
            latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}

/** A cache hit must not extend the provider's original freshness window. */
internal fun isFreshRouteEstimate(estimate: TripRouteEstimate, now: Long): Boolean {
    if (validatedRouteEstimate(estimate) == null || estimate.provider.length > 100 ||
        estimate.provider.any { it.isISOControl() } || now <= 0L
    ) return false
    return estimate.queriedAtMillis >= now - 600_000L &&
        (estimate.queriedAtMillis <= now || estimate.queriedAtMillis - now <= 60_000L)
}
