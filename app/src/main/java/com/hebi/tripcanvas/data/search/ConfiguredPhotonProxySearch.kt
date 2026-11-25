package com.hebi.tripcanvas.data.search

import android.os.SystemClock
import com.hebi.tripcanvas.data.network.MapProxyEndpointPolicy
import com.hebi.tripcanvas.data.network.MapProxySessionProvider
import com.hebi.tripcanvas.data.network.MapProxyTransport
import com.hebi.tripcanvas.data.network.QueryRateLimitedException
import com.hebi.tripcanvas.data.network.QueryThrottle
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Candidate contract for an approved private Photon/OSM backend.
 * Intentionally not installed in TripApp until the real authentication adapter,
 * endpoint, storage rights and server-side budgets have been approved.
 */
class ConfiguredPhotonProxySearch private constructor(
    private val transport: MapProxyTransport
) {
    private val mutex = Mutex()
    private val throttle = QueryThrottle(1_500L)

    suspend fun search(query: String): List<RemotePlace> = mutex.withLock {
        val term = query.trim()
        require(term.length in 2..80 && term.none { it.isISOControl() })
        val now = SystemClock.elapsedRealtime()
        throttle.requireAvailable(now)
        val pause = throttle.delayMillis(now)
        if (pause > 0L) delay(pause)
        throttle.started(SystemClock.elapsedRealtime())
        val payload = JSONObject().put("query", term).put("language", "ko").put("limit", 8)
            .toString().toByteArray(Charsets.UTF_8)
        val response = transport.post(payload, 256 * 1024)
        when (response.status) {
            200 -> parseResponse(requireNotNull(response.body))
            204 -> emptyList()
            401, 403 -> throw IOException("장소 검색 서버의 접근 권한을 확인할 수 없습니다.")
            429 -> {
                throttle.rateLimited(SystemClock.elapsedRealtime(), response.retryAfter, System.currentTimeMillis())
                throw QueryRateLimitedException()
            }
            else -> throw IOException("운영 장소 검색 서버에 연결할 수 없습니다.")
        }
    }

    companion object {
        fun create(endpoint: String, sessions: MapProxySessionProvider?): ConfiguredPhotonProxySearch? {
            if (sessions == null || !MapProxyEndpointPolicy.approved(endpoint, "/v1/places")) return null
            return ConfiguredPhotonProxySearch(MapProxyTransport(endpoint, "/v1/places", sessions))
        }

        internal fun parseResponse(body: String): List<RemotePlace> {
            val root = JSONObject(body)
            require(root.get("provider") == "Photon/OSM") { "지원하지 않는 장소 데이터 공급자입니다." }
            val places = root.getJSONArray("places")
            require(places.length() <= 8)
            val seen = mutableSetOf<String>()
            return buildList {
                for (i in 0 until places.length()) {
                    val item = places.getJSONObject(i)
                    fun string(key: String): String = item.get(key) as? String
                        ?: throw IOException("장소 응답 형식이 올바르지 않습니다.")
                    fun number(key: String): Double = (item.get(key) as? Number)?.toDouble()
                        ?: throw IOException("장소 좌표 형식이 올바르지 않습니다.")
                    val id = string("providerId")
                    require(id.matches(Regex("[NWR]:[1-9][0-9]{0,19}")) && seen.add(id))
                    val name = string("name")
                    val address = string("address")
                    require(name.none { it.isISOControl() } && address.none { it.isISOControl() })
                    add(RemotePlace(id, name, address, number("latitude"), number("longitude"), "Photon/OSM"))
                }
            }
        }
    }
}
