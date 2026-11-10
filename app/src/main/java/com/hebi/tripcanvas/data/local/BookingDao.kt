package com.hebi.tripcanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings WHERE tripId = :tripId ORDER BY startAtLocal,id")
    fun observe(tripId: String): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE tripId = :tripId AND id = :id LIMIT 1")
    suspend fun find(tripId: String, id: String): BookingEntity?

    @Query("SELECT * FROM bookings WHERE tripId = :tripId AND id = :id LIMIT 1")
    fun observeItem(tripId: String, id: String): Flow<BookingEntity?>

    @Insert
    suspend fun insert(item: BookingEntity)

    @Update
    suspend fun update(item: BookingEntity): Int

    @Query("DELETE FROM bookings WHERE tripId = :tripId AND id = :id")
    suspend fun delete(tripId: String, id: String): Int

    @Query("SELECT * FROM booking_attachments WHERE bookingId = :id ORDER BY addedAtMillis,id")
    fun observeAttachments(id: String): Flow<List<BookingAttachmentEntity>>

    @Insert
    suspend fun addAttachment(item: BookingAttachmentEntity)

    @Query("DELETE FROM booking_attachments WHERE id = :id AND bookingId = :bookingId")
    suspend fun deleteAttachment(bookingId: String, id: String): Int
}
