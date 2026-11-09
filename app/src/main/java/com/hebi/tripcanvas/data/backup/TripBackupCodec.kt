package com.hebi.tripcanvas.data.backup

import com.hebi.tripcanvas.data.local.ChecklistItemEntity
import com.hebi.tripcanvas.data.local.ExpenseEntity
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.data.local.TripBackupSnapshot
import com.hebi.tripcanvas.data.local.TripBudgetEntity
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.domain.ExpenseCategory
import com.hebi.tripcanvas.domain.toInput
import com.hebi.tripcanvas.domain.validateExpense
import com.hebi.tripcanvas.domain.validateMoney
import com.hebi.tripcanvas.domain.validatePlace
import com.hebi.tripcanvas.domain.validateSchedule
import com.hebi.tripcanvas.domain.validateTrip
import java.time.LocalDateTime
import org.json.JSONArray
import org.json.JSONObject

object TripBackupCodec {
    const val FORMAT = "tripcanvas-structured-backup"
    const val VERSION = 2
    const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_ROWS = 100_000

    fun encode(snapshot: TripBackupSnapshot): String {
        validate(snapshot)
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("trips", array(snapshot.trips) {
                JSONObject()
                    .put("id", it.id).put("title", it.title)
                    .put("startDate", it.startDate).put("endDate", it.endDate)
                    .put("country", it.country).put("city", it.city)
                    .put("timeZoneId", it.timeZoneId).put("currencyCode", it.currencyCode)
                    .put("purpose", it.purpose).put("note", it.note)
                    .put("createdAtMillis", it.createdAtMillis)
                    .put("updatedAtMillis", it.updatedAtMillis)
            })
            .put("places", array(snapshot.places) {
                JSONObject()
                    .put("id", it.id).put("tripId", it.tripId)
                    .put("name", it.name).put("address", it.address)
                    .put("note", it.note)
                    .put("latitude", it.latitude ?: JSONObject.NULL)
                    .put("longitude", it.longitude ?: JSONObject.NULL)
                    .put("createdAtMillis", it.createdAtMillis)
                    .put("updatedAtMillis", it.updatedAtMillis)
                    .put("provider", it.provider ?: JSONObject.NULL)
                    .put("providerPlaceId", it.providerPlaceId ?: JSONObject.NULL)
                    .put("sourceFetchedAtMillis", it.sourceFetchedAtMillis ?: JSONObject.NULL)
            })
            .put("itineraries", array(snapshot.itineraries) {
                JSONObject()
                    .put("id", it.id).put("tripId", it.tripId).put("date", it.date)
                    .put("title", it.title)
                    .put("startTime", it.startTime ?: JSONObject.NULL)
                    .put("endTime", it.endTime ?: JSONObject.NULL)
                    .put("placeId", it.placeId ?: JSONObject.NULL)
                    .put("note", it.note).put("important", it.important)
                    .put("confirmed", it.confirmed).put("sortOrder", it.sortOrder)
                    .put("createdAtMillis", it.createdAtMillis)
                    .put("updatedAtMillis", it.updatedAtMillis)
            })
            .put("expenses", array(snapshot.expenses) {
                JSONObject()
                    .put("id", it.id).put("tripId", it.tripId)
                    .put("amountDecimal", it.amountDecimal)
                    .put("currencyCode", it.currencyCode)
                    .put("occurredAtLocal", it.occurredAtLocal)
                    .put("category", it.category).put("note", it.note)
                    .put("placeId", it.placeId ?: JSONObject.NULL)
                    .put("createdAtMillis", it.createdAtMillis)
                    .put("updatedAtMillis", it.updatedAtMillis)
            })
            .put("budgets", array(snapshot.budgets) {
                JSONObject().put("tripId", it.tripId)
                    .put("amountDecimal", it.amountDecimal)
                    .put("currencyCode", it.currencyCode)
                    .put("updatedAtMillis", it.updatedAtMillis)
            })
            .put("checklist", array(snapshot.checklist) {
                JSONObject()
                    .put("id", it.id).put("tripId", it.tripId)
                    .put("title", it.title).put("checked", it.checked)
                    .put("position", it.position)
                    .put("createdAtMillis", it.createdAtMillis)
                    .put("updatedAtMillis", it.updatedAtMillis)
            })
        return root.toString(2)
    }

    fun decode(text: String): TripBackupSnapshot {
        val json = JSONObject(text)
        require(json.getString("format") == FORMAT) { "Unknown backup format" }
        require(json.getInt("version") in 1..VERSION) { "Unsupported backup version" }
        val snapshot = TripBackupSnapshot(
            trips = parse(json, "trips") {
                TripEntity(
                    id = it.getString("id"), title = it.getString("title"),
                    startDate = it.getString("startDate"), endDate = it.getString("endDate"),
                    country = it.getString("country"), city = it.getString("city"),
                    timeZoneId = it.getString("timeZoneId"),
                    currencyCode = it.getString("currencyCode"),
                    purpose = it.getString("purpose"), note = it.getString("note"),
                    createdAtMillis = it.getLong("createdAtMillis"),
                    updatedAtMillis = it.getLong("updatedAtMillis")
                )
            },
            places = parse(json, "places") {
                PlaceEntity(
                    id = it.getString("id"), tripId = it.getString("tripId"),
                    name = it.getString("name"), address = it.getString("address"),
                    note = it.getString("note"), latitude = it.nullableDouble("latitude"),
                    longitude = it.nullableDouble("longitude"),
                    createdAtMillis = it.getLong("createdAtMillis"),
                    updatedAtMillis = it.getLong("updatedAtMillis"),
                    provider = it.nullableString("provider"),
                    providerPlaceId = it.nullableString("providerPlaceId"),
                    sourceFetchedAtMillis = if (it.isNull("sourceFetchedAtMillis")) null
                        else it.getLong("sourceFetchedAtMillis")
                )
            },
            itineraries = parse(json, "itineraries") {
                ItineraryItemEntity(
                    id = it.getString("id"), tripId = it.getString("tripId"),
                    date = it.getString("date"), title = it.getString("title"),
                    startTime = it.nullableString("startTime"),
                    endTime = it.nullableString("endTime"),
                    placeId = it.nullableString("placeId"), note = it.getString("note"),
                    important = it.getBoolean("important"),
                    confirmed = it.getBoolean("confirmed"), sortOrder = it.getInt("sortOrder"),
                    createdAtMillis = it.getLong("createdAtMillis"),
                    updatedAtMillis = it.getLong("updatedAtMillis")
                )
            },
            expenses = parse(json, "expenses") {
                ExpenseEntity(
                    id = it.getString("id"), tripId = it.getString("tripId"),
                    amountDecimal = it.getString("amountDecimal"),
                    currencyCode = it.getString("currencyCode"),
                    occurredAtLocal = it.getString("occurredAtLocal"),
                    category = it.getString("category"), note = it.getString("note"),
                    placeId = it.nullableString("placeId"),
                    createdAtMillis = it.getLong("createdAtMillis"),
                    updatedAtMillis = it.getLong("updatedAtMillis")
                )
            },
            budgets = parse(json, "budgets") {
                TripBudgetEntity(
                    tripId = it.getString("tripId"),
                    amountDecimal = it.getString("amountDecimal"),
                    currencyCode = it.getString("currencyCode"),
                    updatedAtMillis = it.getLong("updatedAtMillis")
                )
            },
            checklist = parse(json, "checklist") {
                ChecklistItemEntity(
                    id = it.getString("id"), tripId = it.getString("tripId"),
                    title = it.getString("title"), checked = it.getBoolean("checked"),
                    position = it.getInt("position"),
                    createdAtMillis = it.getLong("createdAtMillis"),
                    updatedAtMillis = it.getLong("updatedAtMillis")
                )
            }
        )
        validate(snapshot)
        return snapshot
    }

    fun validate(snapshot: TripBackupSnapshot) {
        require(snapshot.totalRows <= MAX_ROWS) { "Too many entries" }
        requireUnique(snapshot.trips.map { it.id })
        requireUnique(snapshot.places.map { it.id })
        requireUnique(snapshot.itineraries.map { it.id })
        requireUnique(snapshot.expenses.map { it.id })
        requireUnique(snapshot.checklist.map { it.id })
        requireUnique(snapshot.budgets.map { it.tripId })

        val trips = snapshot.trips.associateBy { it.id }
        for (trip in snapshot.trips) {
            require(validId(trip.id) && validateTrip(trip.toInput()).isValid) { "Invalid trip" }
        }
        val places = snapshot.places.associateBy { it.id }
        for (place in snapshot.places) {
            require(validId(place.id) && trips.containsKey(place.tripId)) { "Invalid place owner" }
            require(validatePlace(place.toInput()).normalized != null) { "Invalid place" }
            require(
                if (place.provider == null) {
                    place.providerPlaceId == null && place.sourceFetchedAtMillis == null
                } else {
                    place.provider == "Photon/OSM" &&
                        !place.providerPlaceId.isNullOrBlank() &&
                        place.providerPlaceId.length <= 200 &&
                        place.sourceFetchedAtMillis != null &&
                        place.sourceFetchedAtMillis >= 0L
                }
            ) { "Invalid place source" }
        }
        for (item in snapshot.itineraries) {
            val trip = trips[item.tripId] ?: throw IllegalArgumentException("Invalid itinerary trip")
            require(validId(item.id) && item.sortOrder >= 0) { "Invalid itinerary ID or order" }
            require(validateSchedule(item.toInput(), trip).normalized != null) { "Invalid itinerary" }
            val place = item.placeId?.let { places[it] }
            require(item.placeId == null || place?.tripId == item.tripId) { "Foreign place link" }
        }
        for (expense in snapshot.expenses) {
            require(validId(expense.id) && trips.containsKey(expense.tripId)) { "Invalid expense owner" }
            require(ExpenseCategory.entries.any { it.name == expense.category }) { "Invalid category" }
            require(validateExpense(expense.toInput()).valid != null) { "Invalid expense" }
            require(expense.currencyCode == expense.currencyCode.uppercase()) { "Invalid currency casing" }
            require(LocalDateTime.parse(expense.occurredAtLocal).second == 0) {
                "Expense seconds unsupported"
            }
            val place = expense.placeId?.let { places[it] }
            require(expense.placeId == null || place?.tripId == expense.tripId) { "Foreign expense place" }
        }
        for (budget in snapshot.budgets) {
            require(trips.containsKey(budget.tripId)) { "Invalid budget owner" }
            require(validateMoney(budget.amountDecimal, budget.currencyCode) != null) {
                "Invalid budget amount"
            }
        }
        for (item in snapshot.checklist) {
            require(validId(item.id) && trips.containsKey(item.tripId)) { "Invalid checklist owner" }
            require(item.title.trim().length in 1..100 && item.position >= 0) { "Invalid checklist entry" }
        }
    }

    private fun validId(id: String): Boolean = id.isNotBlank() && id.length <= 128

    private fun requireUnique(ids: List<String>) {
        require(ids.size == ids.toSet().size) { "Duplicate IDs" }
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (isNull(key)) null else getDouble(key)

    private fun <T> array(values: List<T>, convert: (T) -> JSONObject): JSONArray =
        JSONArray().apply { values.forEach { put(convert(it)) } }

    private fun <T> parse(json: JSONObject, key: String, convert: (JSONObject) -> T): List<T> {
        val array = json.getJSONArray(key)
        require(array.length() <= MAX_ROWS) { "Too many entries" }
        return (0 until array.length()).map { convert(array.getJSONObject(it)) }
    }
}
