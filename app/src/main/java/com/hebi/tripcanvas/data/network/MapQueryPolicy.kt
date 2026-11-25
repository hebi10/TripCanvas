package com.hebi.tripcanvas.data.network

import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** In-memory, bounded LRU with monotonic expiry. Call under the owning client's mutex. */
internal class ExpiringQueryCache<K, V>(
    private val maxEntries: Int,
    private val ttlMillis: Long
) {
    init {
        require(maxEntries > 0 && ttlMillis > 0)
    }

    private data class Entry<V>(val time: Long, val value: V)
    private val entries = LinkedHashMap<K, Entry<V>>(maxEntries, 0.75f, true)

    operator fun get(key: K, now: Long): V? {
        expire(now)
        return entries[key]?.value
    }

    fun put(key: K, value: V, now: Long) {
        expire(now)
        entries[key] = Entry(now, value)
        while (entries.size > maxEntries) {
            val iterator = entries.entries.iterator()
            iterator.next()
            iterator.remove()
        }
    }

    fun remove(key: K) { entries.remove(key) }

    private fun expire(now: Long) {
        entries.entries.removeAll { (_, entry) ->
            now < entry.time || now - entry.time >= ttlMillis
        }
    }
}

/** No automatic retry: a later explicit request must respect the server cooldown. */
internal class QueryThrottle(private val minimumIntervalMillis: Long) {
    init { require(minimumIntervalMillis > 0) }
    private var lastRequestAt: Long? = null
    private var limitedUntil: Long? = null

    fun requireAvailable(now: Long) {
        if (limitedUntil?.let { now < it } == true) throw QueryRateLimitedException()
        limitedUntil = null
    }

    fun delayMillis(now: Long): Long {
        val last = lastRequestAt ?: return 0L
        return (minimumIntervalMillis - (now - last).coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    fun started(now: Long) { lastRequestAt = now }

    fun rateLimited(now: Long, retryAfter: String?, wallClockMillis: Long) {
        val wait = retryAfterMillis(retryAfter, wallClockMillis)
        val until = if (now > Long.MAX_VALUE - wait) Long.MAX_VALUE else now + wait
        limitedUntil = maxOf(limitedUntil ?: until, until)
    }

    companion object {
        private const val DEFAULT_COOLDOWN = 60_000L
        private const val MAX_COOLDOWN = 24 * 60 * 60_000L

        fun retryAfterMillis(value: String?, wallClockMillis: Long): Long {
            val header = value?.trim().orEmpty()
            val seconds = header.toLongOrNull()
            val wait = when {
                seconds != null && seconds >= 0 -> seconds.coerceAtMost(MAX_COOLDOWN / 1000) * 1000
                header.length in 1..100 -> runCatching {
                    val target = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli()
                    Math.subtractExact(target, wallClockMillis)
                }.getOrDefault(DEFAULT_COOLDOWN)
                else -> DEFAULT_COOLDOWN
            }
            return wait.coerceIn(DEFAULT_COOLDOWN, MAX_COOLDOWN)
        }
    }
}

internal class QueryRateLimitedException :
    IOException("외부 조회 요청이 제한되었습니다. 잠시 후 다시 조회하거나 직접 등록해 주세요.")
