package com.hebi.tripcanvas.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapQueryPolicyTest {
    @Test fun expiryUsesMonotonicTimeAndPrunesOldEntries() {
        val cache = ExpiringQueryCache<String, String>(2, 100L)
        cache.put("old", "value", 10L)
        assertEquals("value", cache["old", 109L])
        assertNull(cache["old", 110L])
        cache.put("future", "value", 120L)
        assertNull(cache["future", 119L])
    }

    @Test fun cacheEvictsLeastRecentlyUsedAndNeverGrowsBeyondLimit() {
        val cache = ExpiringQueryCache<String, Int>(2, 1000L)
        cache.put("a", 1, 0L)
        cache.put("b", 2, 1L)
        assertEquals(1, cache["a", 2L])
        cache.put("c", 3, 3L)
        assertNull(cache["b", 3L])
        assertEquals(1, cache["a", 3L])
        assertEquals(3, cache["c", 3L])
        cache.remove("a")
        assertNull(cache["a", 4L])
    }

    @Test fun firstRequestIsImmediateAndLaterRequestsRespectMinimumInterval() {
        val throttle = QueryThrottle(1500L)
        assertEquals(0L, throttle.delayMillis(0L))
        throttle.started(50L)
        assertEquals(1000L, throttle.delayMillis(550L))
        assertEquals(0L, throttle.delayMillis(1550L))
    }

    @Test fun retryAfterIsBoundedAndManualRequestsAreBlockedDuringCooldown() {
        val throttle = QueryThrottle(1200L)
        throttle.rateLimited(100L, "120", 0L)
        assertTrue(runCatching { throttle.requireAvailable(120099L) }.exceptionOrNull() is QueryRateLimitedException)
        throttle.requireAvailable(120100L)
        assertEquals(60000L, QueryThrottle.retryAfterMillis(null, 0L))
        assertEquals(60000L, QueryThrottle.retryAfterMillis("1", 0L))
        assertEquals(86400000L, QueryThrottle.retryAfterMillis(Long.MAX_VALUE.toString(), 0L))
        assertEquals(120000L, QueryThrottle.retryAfterMillis("Thu, 1 Jan 1970 00:02:00 GMT", 0L))
        assertEquals(60000L, QueryThrottle.retryAfterMillis("invalid", 0L))
    }
}
