package com.hebi.tripcanvas.data.search

import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Photon public demo is an opt-in development/small-scale discovery source.
 * No autocomplete or background requests. Replace service for production use.
 */
class PhotonPlaceSearch(
    private val endpoint: String = "https://photon.komoot.io/api/"
) {
    private val mutex = Mutex()
    private val cached = mutableMapOf<String, Pair<Long, List<RemotePlace>>>()
    private var lastRequestAt = 0L

    suspend fun search(query: String): List<RemotePlace> = mutex.withLock {
        val term = query.trim()
        require(term.length in 2..80) { "검색어는 2~80자로 입력해 주세요." }
        val cacheKey = term.lowercase()
        val cachedResult = cached[cacheKey]
        val now = SystemClock.elapsedRealtime()
        if (cachedResult != null && now - cachedResult.first < 10 * 60_000L) {
            return@withLock cachedResult.second
        }
        val pause = 1_500L - (now - lastRequestAt)
        if (pause > 0L) delay(pause)
        lastRequestAt = SystemClock.elapsedRealtime()
        val results = withContext(Dispatchers.IO) { request(term) }
        cached[cacheKey] = SystemClock.elapsedRealtime() to results
        results
    }

    private fun request(term: String): List<RemotePlace> {
        require(endpoint.startsWith("https://"))
        val url = URL(endpoint + "?q=" + URLEncoder.encode(term, "UTF-8") + "&limit=8&lang=ko")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 7_000
        connection.readTimeout = 7_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty(
            "User-Agent", "TripCanvas/0.1.0 (https://github.com/hebi10/TripCanvas)"
        )
        connection.setRequestProperty("Accept", "application/json")
        try {
            when (connection.responseCode) {
                200 -> Unit
                429 -> throw IOException("검색 요청이 제한됐습니다. 잠시 후 다시 이용해 주세요.")
                else -> throw IOException("외부 장소 검색에 연결하지 못했습니다.")
            }
            val buffer = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(4096)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    require(buffer.size() + count <= 256 * 1024) { "검색 응답이 너무 큽니다." }
                    buffer.write(chunk, 0, count)
                }
            }
            return parseResponse(buffer.toString(StandardCharsets.UTF_8.name()))
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        fun parseResponse(json: String): List<RemotePlace> {
            val features = JSONObject(json).getJSONArray("features")
            return buildList {
                for (index in 0 until minOf(features.length(), 8)) {
                    val feature = features.getJSONObject(index)
                    val props = feature.getJSONObject("properties")
                    val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
                    val id = props.optString("osm_id", "")
                    val type = props.optString("osm_type", "")
                    val name = props.optString("name", "").trim().take(100)
                    if (id.isBlank() || type.isBlank() || name.isBlank()) continue
                    val address = listOf(
                        props.optString("housenumber", ""),
                        props.optString("street", ""),
                        props.optString("city", ""),
                        props.optString("state", ""),
                        props.optString("country", "")
                    ).filter { it.isNotBlank() }.distinct().joinToString(" ").take(500)
                    val lon = coordinates.getDouble(0)
                    val lat = coordinates.getDouble(1)
                    val item = runCatching {
                        RemotePlace(
                            providerId = "$type:$id", name = name,
                            address = address, latitude = lat, longitude = lon
                        )
                    }.getOrNull()
                    if (item != null && none { it.providerId == item.providerId }) add(item)
                }
            }
        }
    }
}
