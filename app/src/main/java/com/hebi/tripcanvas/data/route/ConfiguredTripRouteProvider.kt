package com.hebi.tripcanvas.data.route

import android.os.SystemClock
import com.hebi.tripcanvas.BuildConfig
import com.hebi.tripcanvas.data.network.MapProxyEndpointPolicy
import com.hebi.tripcanvas.data.network.MapProxySessionProvider
import com.hebi.tripcanvas.data.network.MapProxyTransport
import com.hebi.tripcanvas.data.network.QueryThrottle
import com.hebi.tripcanvas.data.network.QueryRateLimitedException
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.domain.TripRouteEstimate
import com.hebi.tripcanvas.domain.TripRouteMode
import com.hebi.tripcanvas.domain.TripRouteProvider
import com.hebi.tripcanvas.domain.validatedRouteEstimate
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Routes are requested only through an explicitly configured, access-controlled HTTPS proxy.
 * The app never ships a commercial routing API key or queries public demo endpoints.
 */
object RouteEndpointPolicy {
    fun approved(url: String): Boolean = MapProxyEndpointPolicy.approved(url, "/v1/routes")
}

class ConfiguredTripRouteProvider private constructor(
    private val transport: MapProxyTransport
) : TripRouteProvider {
    private val mutex = Mutex()
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
        return mutex.withLock {
            val now = SystemClock.elapsedRealtime()
            throttle.requireAvailable(now)
            val pause = throttle.delayMillis(now)
            if (pause > 0) delay(pause)
            throttle.started(SystemClock.elapsedRealtime())
            // Do not reuse authenticated responses across sessions or after revocation.
            request(fromLat, fromLon, toLat, toLon, mode)
        }
    }

    private suspend fun request(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double,
        mode: TripRouteMode
    ): TripRouteEstimate? {
        val payload = JSONObject()
            .put("origin", JSONObject().put("latitude", fromLat).put("longitude", fromLon))
            .put("destination", JSONObject().put("latitude", toLat).put("longitude", toLon))
            .put("mode", mode.name)
            .toString().toByteArray(StandardCharsets.UTF_8)

        val response = transport.post(payload, 8 * 1024)
        when (response.status) {
            200 -> Unit
            204, 404, 422 -> return null
            401, 403 -> throw IOException("경로 서버의 접근 권한을 확인할 수 없습니다.")
            429 -> {
                throttle.rateLimited(SystemClock.elapsedRealtime(), response.retryAfter, System.currentTimeMillis())
                throw QueryRateLimitedException()
            }
            else -> throw IOException("경로 서버에 연결할 수 없습니다.")
        }
        val json = JSONObject(requireNotNull(response.body))
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
    }

    companion object {
        fun fromBuildConfig(sessions: MapProxySessionProvider? = null): ConfiguredTripRouteProvider? =
            create(BuildConfig.ROUTE_PROXY_URL, sessions)

        fun create(endpoint: String, sessions: MapProxySessionProvider?): ConfiguredTripRouteProvider? {
            if (sessions == null || !RouteEndpointPolicy.approved(endpoint)) return null
            return ConfiguredTripRouteProvider(MapProxyTransport(endpoint, "/v1/routes", sessions))
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
