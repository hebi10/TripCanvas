package com.hebi.tripcanvas.data

import com.hebi.tripcanvas.data.local.ChecklistDao
import com.hebi.tripcanvas.data.local.ChecklistItemEntity
import com.hebi.tripcanvas.data.local.TripDao
import com.hebi.tripcanvas.data.local.TripEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC)

    @Test fun createCheckRenameReorderAndUndo() = runBlocking {
        val dao = FakeChecklistDao()
        val repository = ChecklistRepository(dao, FakeChecklistTripDao(), clock)
        val first = repository.create("trip", " 여권 ")
        val second = repository.create("trip", "충전기")
        assertEquals(listOf(first, second), repository.observe("trip").first().map { it.id })
        repository.setChecked("trip", first, true)
        assertTrue(repository.find("trip", first)!!.checked)
        assertTrue(repository.move("trip", second, -1))
        assertEquals(listOf(second, first), repository.observe("trip").first().map { it.id })
        repository.rename("trip", second, "보조배터리")
        assertEquals("보조배터리", repository.find("trip", second)?.title)
        val deleted = repository.delete("trip", first)
        assertEquals(1, repository.observe("trip").first().size)
        repository.undoDelete(deleted)
        assertEquals(2, repository.observe("trip").first().size)
        repository.setChecked("trip", first, false)
        assertFalse(repository.find("trip", first)!!.checked)
    }

    @Test fun boundaryMovesAndUndoPreserveSavedChecklistState() = runBlocking {
        val dao = FakeChecklistDao()
        val repository = ChecklistRepository(dao, FakeChecklistTripDao(), clock)
        val first = repository.create("trip", "여권")
        val second = repository.create("trip", "충전기")
        val third = repository.create("trip", "상비약")

        repository.setChecked("trip", first, true)
        assertFalse(repository.move("trip", first, -1))
        assertFalse(repository.move("trip", third, 1))
        assertTrue(repository.move("trip", second, 1))
        assertEquals(listOf(first, third, second), dao.ordered("trip").map { it.id })
        assertTrue(repository.move("trip", second, -1))
        assertEquals(listOf(first, second, third), dao.ordered("trip").map { it.id })

        val deleted = repository.delete("trip", second)
        assertEquals(listOf(first, third), dao.ordered("trip").map { it.id })
        repository.undoDelete(deleted)
        assertEquals(listOf(first, second, third), dao.ordered("trip").map { it.id })
        assertTrue(repository.find("trip", first)!!.checked)
        assertFalse(repository.find("trip", second)!!.checked)
    }

    @Test fun rejectsEmptyNamesAndCrossTripOperations() = runBlocking {
        val repository = ChecklistRepository(FakeChecklistDao(), FakeChecklistTripDao(), clock)
        assertTrue(runCatching { repository.create("trip", " ") }.isFailure)
        assertTrue(runCatching { repository.create("wrong", "여권") }.isFailure)
        val id = repository.create("trip", "여권")
        assertTrue(runCatching { repository.setChecked("other", id, true) }.isFailure)
    }
}

private class FakeChecklistDao : ChecklistDao {
    private val items = MutableStateFlow<List<ChecklistItemEntity>>(emptyList())
    override fun observeForTrip(tripId: String): Flow<List<ChecklistItemEntity>> =
        items.map { entries -> entries.filter { it.tripId == tripId }.sortedBy { it.position } }
    override suspend fun ordered(tripId: String): List<ChecklistItemEntity> =
        items.value.filter { it.tripId == tripId }.sortedBy { it.position }
    override suspend fun find(tripId: String, id: String): ChecklistItemEntity? =
        items.value.firstOrNull { it.tripId == tripId && it.id == id }
    override suspend fun nextPosition(tripId: String): Int =
        (ordered(tripId).maxOfOrNull { it.position } ?: -1) + 1
    override suspend fun insert(item: ChecklistItemEntity) {
        require(items.value.none { it.id == item.id })
        items.value = items.value + item
    }
    override suspend fun update(item: ChecklistItemEntity): Int {
        if (items.value.none { it.tripId == item.tripId && it.id == item.id }) return 0
        items.value = items.value.map { if (it.id == item.id) item else it }
        return 1
    }
    override suspend fun delete(tripId: String, id: String): Int {
        val size = items.value.size
        items.value = items.value.filterNot { it.tripId == tripId && it.id == id }
        return size - items.value.size
    }
    override suspend fun setPosition(tripId: String, id: String, position: Int) {
        items.value = items.value.map {
            if (it.tripId == tripId && it.id == id) it.copy(position = position) else it
        }
    }
}

private class FakeChecklistTripDao : TripDao {
    private val trip = TripEntity(
        id = "trip", title = "국내 여행",
        startDate = "2026-11-09", endDate = "2026-11-12",
        country = "", city = "", timeZoneId = "Asia/Seoul", currencyCode = "KRW",
        purpose = "", note = "", createdAtMillis = 1L, updatedAtMillis = 1L
    )
    override fun observeAll(): Flow<List<TripEntity>> = MutableStateFlow(listOf(trip))
    override fun observeById(tripId: String): Flow<TripEntity?> =
        MutableStateFlow(trip.takeIf { it.id == tripId })
    override suspend fun findById(tripId: String): TripEntity? = trip.takeIf { it.id == tripId }
    override suspend fun outOfRangeDates(tripId: String, startDate: String, endDate: String) = emptyList<String>()
    override suspend fun insert(trip: TripEntity) {}
    override suspend fun update(trip: TripEntity): Int = 0
    override suspend fun deleteById(tripId: String): Int = 0
}
