package com.hebi.tripcanvas.data

import com.hebi.tripcanvas.data.local.TripDao
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.domain.TripInput
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC)
    private val input = TripInput(
        title = " 제주 여행 ",
        startDate = "2026-11-12",
        endDate = "2026-11-15",
        timeZoneId = "Asia/Seoul",
        currencyCode = "KRW"
    )

    @Test fun createsDistinctIdsForIdenticalNames() = runBlocking {
        val dao = FakeTripDao()
        val repository = TripRepository(dao, clock)
        val first = repository.create(input)
        val second = repository.create(input)
        assertNotEquals(first, second)
        assertEquals(2, repository.trips.first().size)
        assertEquals("제주 여행", repository.trips.first().first().title)
    }

    @Test fun updatesPreserveIdAndCreatedTimestamp() = runBlocking {
        val dao = FakeTripDao()
        val repository = TripRepository(dao, clock)
        val id = repository.create(input)
        repository.update(id, input.copy(title = "새 이름", city = "서귀포"))
        val updated = repository.observeTrip(id).first()!!
        assertEquals(id, updated.id)
        assertEquals("새 이름", updated.title)
        assertEquals("서귀포", updated.city)
        assertEquals(clock.millis(), updated.createdAtMillis)
    }

    @Test fun deletesOnlyRequestedTripAndRejectsMissingId() = runBlocking {
        val dao = FakeTripDao()
        val repository = TripRepository(dao, clock)
        val kept = repository.create(input)
        val removed = repository.create(input.copy(title = "다른 여행"))
        repository.delete(removed)
        assertNull(repository.observeTrip(removed).first())
        assertEquals(kept, repository.trips.first().single().id)
        assertTrue(runCatching { repository.delete(removed) }.isFailure)
    }

    @Test fun rejectsInvalidDataWithoutInserting() = runBlocking {
        val repository = TripRepository(FakeTripDao(), clock)
        assertTrue(runCatching { repository.create(input.copy(title = " ")) }.isFailure)
        assertTrue(repository.trips.first().isEmpty())
    }
}

private class FakeTripDao : TripDao {
    private val trips = MutableStateFlow<List<TripEntity>>(emptyList())

    override fun observeAll(): Flow<List<TripEntity>> = trips

    override fun observeById(tripId: String): Flow<TripEntity?> =
        kotlinx.coroutines.flow.map(trips) { items -> items.firstOrNull { it.id == tripId } }

    override suspend fun findById(tripId: String): TripEntity? =
        trips.value.firstOrNull { it.id == tripId }

    override suspend fun insert(trip: TripEntity) {
        require(trips.value.none { it.id == trip.id })
        trips.value = trips.value + trip
    }

    override suspend fun update(trip: TripEntity): Int {
        if (trips.value.none { it.id == trip.id }) return 0
        trips.value = trips.value.map { if (it.id == trip.id) trip else it }
        return 1
    }

    override suspend fun deleteById(tripId: String): Int {
        if (trips.value.none { it.id == tripId }) return 0
        trips.value = trips.value.filterNot { it.id == tripId }
        return 1
    }
}
