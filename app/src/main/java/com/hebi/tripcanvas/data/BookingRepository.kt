package com.hebi.tripcanvas.data

import com.hebi.tripcanvas.data.local.BookingAttachmentEntity
import com.hebi.tripcanvas.data.local.BookingDao
import com.hebi.tripcanvas.data.local.BookingEntity
import com.hebi.tripcanvas.data.local.TripDao
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

enum class BookingType(val label: String) {
    FLIGHT("항공"), LODGING("숙소"), TRANSIT("교통"), ADMISSION("입장권"), OTHER("기타")
}

data class BookingInput(
    val type: BookingType = BookingType.OTHER,
    val title: String = "",
    val startAtLocal: String = "",
    val referenceNumber: String = "",
    val note: String = ""
)

fun BookingEntity.toInput(): BookingInput = BookingInput(
    type = BookingType.valueOf(type),
    title = title,
    startAtLocal = startAtLocal.orEmpty(),
    referenceNumber = referenceNumber,
    note = note
)

class BookingRepository(
    private val dao: BookingDao,
    private val trips: TripDao,
    private val clock: Clock = Clock.systemUTC()
) {
    fun observe(tripId: String) = dao.observe(tripId)
    fun observeItem(tripId: String, bookingId: String) = dao.observeItem(tripId, bookingId)
    fun attachments(bookingId: String) = dao.observeAttachments(bookingId)

    suspend fun save(tripId: String, input: BookingInput, bookingId: String? = null): String {
        requireNotNull(trips.findById(tripId)) { "여행을 찾을 수 없습니다." }
        val name = input.title.trim()
        val reference = input.referenceNumber.trim()
        val note = input.note.trim()
        val startAt = input.startAtLocal.trim().ifBlank { null }
        require(name.length in 1..100) { "예약명은 1~100자입니다." }
        require(reference.length <= 180 && note.length <= 2000)
        require(startAt == null || runCatching { LocalDateTime.parse(startAt) }.isSuccess) {
            "날짜·시간은 yyyy-MM-ddTHH:mm 형식입니다."
        }
        val now = clock.millis()
        val existing = bookingId?.let { requireNotNull(dao.find(tripId, it)) }
        val id = bookingId ?: UUID.randomUUID().toString()
        val row = BookingEntity(
            id, tripId, input.type.name, name, startAt, reference, note,
            existing?.createdAtMillis ?: now, now
        )
        if (existing == null) dao.insert(row)
        else require(dao.update(row) == 1)
        return id
    }

    suspend fun delete(tripId: String, id: String) {
        require(dao.delete(tripId, id) == 1)
    }

    suspend fun attachDocument(
        tripId: String,
        bookingId: String,
        uri: String,
        displayName: String,
        mimeType: String
    ): String {
        requireNotNull(dao.find(tripId, bookingId))
        require(uri.startsWith("content://")) { "파일 제공자 URI만 연결할 수 있습니다." }
        require(displayName.length in 1..250 && mimeType.length in 1..120)
        require(mimeType == "application/pdf" || mimeType.startsWith("image/")) {
            "이미지 또는 PDF만 첨부할 수 있습니다."
        }
        val id = UUID.randomUUID().toString()
        dao.addAttachment(BookingAttachmentEntity(
            id, bookingId, uri, displayName, mimeType, clock.millis()
        ))
        return id
    }

    suspend fun detachDocument(tripId: String, bookingId: String, attachmentId: String) {
        requireNotNull(dao.find(tripId, bookingId))
        require(dao.deleteAttachment(bookingId, attachmentId) == 1)
    }
}
