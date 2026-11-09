package com.hebi.tripcanvas.data

import com.hebi.tripcanvas.data.local.ItineraryDao
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.PlaceDao
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.data.local.TripDao
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.data.search.RemotePlace
import com.hebi.tripcanvas.domain.PlaceInput
import com.hebi.tripcanvas.domain.ScheduleInput
import com.hebi.tripcanvas.domain.TripInput
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItineraryPlaceRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC)
    private val day = "2026-11-11"
    private val tripInput = TripInput(
        title = "여행", startDate = "2026-11-10",
        endDate = "2026-11-14", timeZoneId = "Asia/Seoul", currencyCode = "KRW"
    )

    @Test fun scheduleCrudDuplicateAndPersistedReorder() = runBlocking {
        val dao = FakeScheduleDao()
        val tripDao = FakeTravelDao(dao)
        val placeDao = FakePlaceDao(dao)
        val tripId = TripRepository(tripDao, clock).create(tripInput)
        val repository = ItineraryRepository(dao, tripDao, placeDao, clock)
        val morning = repository.save(
            tripId, ScheduleInput(title = "아침", date = day, startTime = "08:00", endTime = "09:00")
        )
        val lunch = repository.save(
            tripId, ScheduleInput(title = "점심", date = day, startTime = "12:00", endTime = "13:00")
        )
        assertEquals(listOf(morning, lunch), repository.observeDay(tripId, day).first().map { it.id })

        assertTrue(repository.moveWithinDay(tripId, day, lunch, -1))
        assertEquals(listOf(lunch, morning), repository.observeDay(tripId, day).first().map { it.id })

        val copied = repository.duplicate(tripId, morning)
        assertNotEquals(morning, copied)
        assertEquals(3, repository.observeTripCount(tripId).first())

        repository.save(tripId, ScheduleInput(title = "아침 수정", date = "2026-11-12"), itemId = morning)
        assertEquals(morning, repository.observeDay(tripId, "2026-11-12").first().single().id)
        repository.delete(tripId, copied)
        assertEquals(2, repository.observeTripCount(tripId).first())
    }

    @Test fun timeOverlapRequiresExplicitConfirmation() = runBlocking {
        val dao = FakeScheduleDao()
        val tripDao = FakeTravelDao(dao)
        val placeDao = FakePlaceDao(dao)
        val tripId = TripRepository(tripDao, clock).create(tripInput)
        val repository = ItineraryRepository(dao, tripDao, placeDao, clock)
        repository.save(
            tripId, ScheduleInput(title = "첫 일정", date = day, startTime = "09:00", endTime = "10:00")
        )
        val overlaps = ScheduleInput(title = "다음 일정", date = day, startTime = "09:30", endTime = "11:00")
        assertTrue(runCatching { repository.save(tripId, overlaps) }
            .exceptionOrNull() is ScheduleOverlapException)
        assertEquals(1, repository.observeTripCount(tripId).first())
        repository.save(tripId, overlaps, allowOverlap = true)
        assertEquals(2, repository.observeTripCount(tripId).first())
    }

    @Test fun placesAreScopedAndCanBeLinkedOnlyWithinTrip() = runBlocking {
        val dao = FakeScheduleDao()
        val tripDao = FakeTravelDao(dao)
        val placeDao = FakePlaceDao(dao)
        val tripRepository = TripRepository(tripDao, clock)
        val firstTrip = tripRepository.create(tripInput)
        val secondTrip = tripRepository.create(tripInput)
        val placeRepository = PlaceRepository(placeDao, tripDao, clock)
        val repository = ItineraryRepository(dao, tripDao, placeDao, clock)
        val firstPlace = placeRepository.create(firstTrip, PlaceInput(name = "서울역"))
        val secondPlace = placeRepository.create(secondTrip, PlaceInput(name = "서울역"))
        assertNotEquals(firstPlace, secondPlace)
        assertNull(placeRepository.find(firstTrip, secondPlace))
        val alienLink = ScheduleInput(title = "잘못된 장소", date = day, placeId = secondPlace)
        assertTrue(runCatching { repository.save(firstTrip, alienLink) }.isFailure)
        assertEquals(0, repository.observeTripCount(firstTrip).first())
        val itemId = repository.save(
            firstTrip, ScheduleInput(title = "정상 장소", date = day, placeId = firstPlace)
        )
        assertEquals(firstPlace, repository.observeItem(firstTrip, itemId).first()?.placeId)
        assertNull(repository.observeItem(secondTrip, itemId).first())
    }

    @Test fun externalPlaceImportDeduplicatesWithinTripButNotAcrossTrips() = runBlocking {
        val schedules = FakeScheduleDao()
        val trips = FakeTravelDao(schedules)
        val places = FakePlaceDao(schedules)
        val repo = PlaceRepository(places, trips, clock)
        val first = TripRepository(trips, clock).create(tripInput)
        val second = TripRepository(trips, clock).create(tripInput)
        val remote = RemotePlace("N:1234", "제주국제공항", "제주", 33.51, 126.49)
        val saved = repo.importRemote(first, remote)
        assertTrue(!saved.alreadySaved)
        val repeat = repo.importRemote(first, remote)
        assertTrue(repeat.alreadySaved)
        assertEquals(saved.id, repeat.id)
        assertEquals(1, repo.observePlaces(first).first().size)
        val elsewhere = repo.importRemote(second, remote)
        assertNotEquals(saved.id, elsewhere.id)
        assertEquals("Photon/OSM", repo.find(first, saved.id)?.provider)
    }

    @Test fun incompleteTimeAndDateOutsideTripCannotBeSaved() = runBlocking {
        val dao = FakeScheduleDao()
        val tripDao = FakeTravelDao(dao)
        val placeDao = FakePlaceDao(dao)
        val tripId = TripRepository(tripDao, clock).create(tripInput)
        val repository = ItineraryRepository(dao, tripDao, placeDao, clock)
        assertTrue(runCatching {
            repository.save(tripId, ScheduleInput(title = "시간", date = day, startTime = "10:00"))
        }.isFailure)
        assertTrue(runCatching {
            repository.save(tripId, ScheduleInput(title = "범위 밖", date = "2026-11-20"))
        }.isFailure)
        assertEquals(0, repository.observeTripCount(tripId).first())
    }
}

private class FakeScheduleDao : ItineraryDao {
    private val items = MutableStateFlow<List<ItineraryItemEntity>>(emptyList())
    fun snapshot(): List<ItineraryItemEntity> = items.value

    private fun day(tripId: String, date: String): List<ItineraryItemEntity> =
        items.value.filter { it.tripId == tripId && it.date == date }
            .sortedWith(compareBy<ItineraryItemEntity> { it.startTime == null }
                .thenBy { it.sortOrder }.thenBy { it.createdAtMillis }.thenBy { it.id })

    override fun observeDay(tripId: String, date: String): Flow<List<ItineraryItemEntity>> =
        items.map { day(tripId, date) }
    override suspend fun getDay(tripId: String, date: String): List<ItineraryItemEntity> =
        day(tripId, date)
    override fun observeItem(tripId: String, itemId: String): Flow<ItineraryItemEntity?> =
        items.map { entries -> entries.firstOrNull { it.tripId == tripId && it.id == itemId } }
    override suspend fun findForTrip(tripId: String, itemId: String): ItineraryItemEntity? =
        items.value.firstOrNull { it.tripId == tripId && it.id == itemId }
    override fun observeTrip(tripId: String): Flow<List<ItineraryItemEntity>> =
        items.map { values -> values.filter { it.tripId == tripId }.sortedWith(
            compareBy<ItineraryItemEntity> { it.date }.thenBy { it.sortOrder }
        ) }
    override fun observeLinkedPlace(tripId: String, placeId: String): Flow<List<ItineraryItemEntity>> =
        items.map { values -> values.filter { it.tripId == tripId && it.placeId == placeId } }
    override fun observeTripCount(tripId: String): Flow<Int> =
        items.map { entries -> entries.count { it.tripId == tripId } }
    override suspend fun nextOrder(tripId: String, date: String): Int =
        (day(tripId, date).maxOfOrNull { it.sortOrder } ?: -1) + 1
    override suspend fun insert(item: ItineraryItemEntity) {
        items.value = items.value + item
    }
    override suspend fun update(item: ItineraryItemEntity): Int {
        if (items.value.none { it.id == item.id && it.tripId == item.tripId }) return 0
        items.value = items.value.map { if (it.id == item.id) item else it }
        return 1
    }
    override suspend fun delete(tripId: String, itemId: String): Int {
        val old = items.value.size
        items.value = items.value.filterNot { it.tripId == tripId && it.id == itemId }
        return old - items.value.size
    }
    override suspend fun setPosition(tripId: String, itemId: String, position: Int) {
        items.value = items.value.map {
            if (it.tripId == tripId && it.id == itemId) it.copy(sortOrder = position) else it
        }
    }
}

private class FakeTravelDao(private val scheduleDao: FakeScheduleDao) : TripDao {
    private val trips = MutableStateFlow<List<TripEntity>>(emptyList())
    override fun observeAll(): Flow<List<TripEntity>> = trips
    override fun observeById(tripId: String): Flow<TripEntity?> =
        trips.map { all -> all.firstOrNull { it.id == tripId } }
    override suspend fun findById(tripId: String): TripEntity? =
        trips.value.firstOrNull { it.id == tripId }
    override suspend fun outOfRangeDates(
        tripId: String, startDate: String, endDate: String
    ): List<String> = scheduleDao.snapshot().filter {
        it.tripId == tripId && (it.date < startDate || it.date > endDate)
    }.map { it.date }
    override suspend fun insert(trip: TripEntity) { trips.value = trips.value + trip }
    override suspend fun update(trip: TripEntity): Int {
        if (trips.value.none { it.id == trip.id }) return 0
        trips.value = trips.value.map { if (it.id == trip.id) trip else it }
        return 1
    }
    override suspend fun deleteById(tripId: String): Int {
        val old = trips.value.size
        trips.value = trips.value.filterNot { it.id == tripId }
        return old - trips.value.size
    }
}

private class FakePlaceDao(private val schedules: FakeScheduleDao) : PlaceDao {
    private val places = MutableStateFlow<List<PlaceEntity>>(emptyList())
    override fun observeForTrip(tripId: String): Flow<List<PlaceEntity>> =
        places.map { all -> all.filter { it.tripId == tripId } }
    override fun observeById(tripId: String, placeId: String): Flow<PlaceEntity?> =
        places.map { all -> all.firstOrNull { it.tripId == tripId && it.id == placeId } }
    override suspend fun findForTrip(tripId: String, placeId: String): PlaceEntity? =
        places.value.firstOrNull { it.tripId == tripId && it.id == placeId }
    override suspend fun findByProvider(tripId: String, provider: String, providerPlaceId: String): PlaceEntity? =
        places.value.firstOrNull {
            it.tripId == tripId && it.provider == provider && it.providerPlaceId == providerPlaceId
        }
    override suspend fun insert(place: PlaceEntity) { places.value = places.value + place }
    override suspend fun update(place: PlaceEntity): Int {
        if (places.value.none { it.id == place.id && it.tripId == place.tripId }) return 0
        places.value = places.value.map { if (it.id == place.id) place else it }
        return 1
    }
    override suspend fun delete(tripId: String, placeId: String): Int {
        val old = places.value.size
        places.value = places.value.filterNot { it.tripId == tripId && it.id == placeId }
        return old - places.value.size
    }
    override suspend fun linkedCount(tripId: String, placeId: String): Int =
        schedules.snapshot().count { it.tripId == tripId && it.placeId == placeId }
}
