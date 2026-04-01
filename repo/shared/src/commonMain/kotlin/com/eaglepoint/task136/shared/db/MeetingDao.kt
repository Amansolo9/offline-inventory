package com.eaglepoint.task136.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meeting: MeetingEntity)

    @Update
    suspend fun update(meeting: MeetingEntity)

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getById(id: String): MeetingEntity?

    @Query("SELECT * FROM meetings WHERE id = :id")
    fun observeById(id: String): Flow<MeetingEntity?>

    @Query("SELECT * FROM meetings WHERE organizerId = :userId ORDER BY startTime DESC LIMIT :limit")
    suspend fun getByOrganizer(userId: String, limit: Int = 50): List<MeetingEntity>

    @Query("SELECT * FROM meetings ORDER BY startTime DESC LIMIT :limit")
    suspend fun page(limit: Int = 50): List<MeetingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendee(attendee: MeetingAttendeeEntity)

    @Query("SELECT * FROM meeting_attendees WHERE meetingId = :meetingId")
    suspend fun getAttendees(meetingId: String): List<MeetingAttendeeEntity>

    @Query("DELETE FROM meeting_attendees WHERE id = :id")
    suspend fun removeAttendee(id: String)
}
