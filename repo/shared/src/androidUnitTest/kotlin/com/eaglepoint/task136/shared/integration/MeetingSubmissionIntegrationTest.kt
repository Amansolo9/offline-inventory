package com.eaglepoint.task136.shared.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.db.MeetingAttendeeEntity
import com.eaglepoint.task136.shared.db.MeetingEntity
import com.eaglepoint.task136.shared.orders.BookingUseCase
import com.eaglepoint.task136.shared.orders.TimeWindow
import com.eaglepoint.task136.shared.viewmodel.MeetingStatus
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MeetingSubmissionIntegrationTest {
    private lateinit var db: AppDatabase
    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-04-01T10:00:00Z")
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun meeting_with_agenda_persists_canonical_fields() = runBlocking {
        val meeting = MeetingEntity(
            id = "m-1", organizerId = "operator", resourceId = "res-1",
            title = "Test", startTime = 100L, endTime = 200L,
            status = MeetingStatus.PendingApproval.name,
            agenda = "Q3 planning", requireCheckIn = true,
        )
        db.meetingDao().upsert(meeting)
        val loaded = db.meetingDao().getById("m-1")
        assertNotNull(loaded)
        assertEquals("Q3 planning", loaded!!.agenda)
        assertEquals(MeetingStatus.PendingApproval.name, loaded.status)
    }

    @Test
    fun attendees_persist_and_are_retrievable_per_meeting() = runBlocking {
        db.meetingDao().upsert(MeetingEntity(
            id = "m-1", organizerId = "op", resourceId = "r",
            title = "X", startTime = 0L, endTime = 100L,
            status = "PendingApproval",
        ))
        db.meetingDao().upsertAttendee(MeetingAttendeeEntity("a1", "m-1", "alice", "Alice"))
        db.meetingDao().upsertAttendee(MeetingAttendeeEntity("a2", "m-1", "bob", "Bob"))
        db.meetingDao().upsertAttendee(MeetingAttendeeEntity("a3", "m-1", "carol", "Carol"))

        val attendees = db.meetingDao().getAttendees("m-1")
        assertEquals(3, attendees.size)
    }

    @Test
    fun meeting_getByIdForOrganizer_enforces_ownership() = runBlocking {
        db.meetingDao().upsert(MeetingEntity(
            id = "m-owned", organizerId = "owner", resourceId = "r",
            title = "X", startTime = 0L, endTime = 0L, status = "Approved",
        ))
        assertNotNull(db.meetingDao().getByIdForOrganizer("m-owned", "owner"))
        assertNull(db.meetingDao().getByIdForOrganizer("m-owned", "other"))
    }

    @Test
    fun getAttendeesForOrganizer_scopes_to_organizer_only() = runBlocking {
        db.meetingDao().upsert(MeetingEntity(
            id = "m-scope", organizerId = "owner", resourceId = "r",
            title = "X", startTime = 0L, endTime = 0L, status = "Approved",
        ))
        db.meetingDao().upsertAttendee(MeetingAttendeeEntity("a1", "m-scope", "alice", "Alice"))

        // Correct organizer sees attendees
        val byOwner = db.meetingDao().getAttendeesForOrganizer("m-scope", "owner")
        assertEquals(1, byOwner.size)
        // Other user cannot see
        val byOther = db.meetingDao().getAttendeesForOrganizer("m-scope", "other")
        assertEquals(0, byOther.size)
    }

    @Test
    fun booking_use_case_detects_overlap_with_buffer() {
        val useCase = BookingUseCase(db.meetingDao(), fixedClock, buffer = 10.minutes)
        val existing = listOf(
            TimeWindow(
                start = Instant.parse("2026-04-01T10:00:00Z"),
                end = Instant.parse("2026-04-01T11:00:00Z"),
            ),
        )
        // Candidate overlaps with 10-min buffer
        val bufferOverlap = TimeWindow(
            start = Instant.parse("2026-04-01T11:05:00Z"),
            end = Instant.parse("2026-04-01T11:30:00Z"),
        )
        assertTrue(useCase.overlaps(existing, bufferOverlap))

        // Candidate outside buffer window
        val nonOverlap = TimeWindow(
            start = Instant.parse("2026-04-01T11:20:00Z"),
            end = Instant.parse("2026-04-01T11:50:00Z"),
        )
        assertFalse(useCase.overlaps(existing, nonOverlap))
    }

    @Test
    fun booking_recommends_three_slots_avoiding_conflicts() = runBlocking {
        val useCase = BookingUseCase(db.meetingDao(), fixedClock)
        db.meetingDao().upsert(MeetingEntity(
            id = "m-conflict", organizerId = "op", resourceId = "res-1",
            title = "C",
            startTime = Instant.parse("2026-04-01T10:00:00Z").toEpochMilliseconds(),
            endTime = Instant.parse("2026-04-01T11:00:00Z").toEpochMilliseconds(),
            status = MeetingStatus.Approved.name,
        ))
        val slots = useCase.findThreeAvailableSlots(
            resourceId = "res-1",
            duration = 30.minutes,
            anchor = Instant.parse("2026-04-01T09:00:00Z"),
        )
        assertEquals(3, slots.size)
        // First slot before the conflict
        assertTrue(slots[0].end <= Instant.parse("2026-04-01T10:00:00Z").plus(10.minutes))
    }

    @Test
    fun meeting_pageByResource_respects_time_range() = runBlocking {
        db.meetingDao().upsert(MeetingEntity(
            id = "m-early", organizerId = "op", resourceId = "r",
            title = "A", startTime = 1000L, endTime = 2000L, status = "Approved",
        ))
        db.meetingDao().upsert(MeetingEntity(
            id = "m-late", organizerId = "op", resourceId = "r",
            title = "B", startTime = 10000L, endTime = 20000L, status = "Approved",
        ))
        val page = db.meetingDao().pageByResource(
            resourceId = "r",
            rangeStart = 500L, rangeEnd = 3000L,
        )
        assertEquals(1, page.size)
        assertEquals("m-early", page[0].id)
    }

    @Test
    fun denied_meetings_are_excluded_from_resource_range_query() = runBlocking {
        db.meetingDao().upsert(MeetingEntity(
            id = "m-approved", organizerId = "op", resourceId = "r",
            title = "A", startTime = 1000L, endTime = 2000L, status = "Approved",
        ))
        db.meetingDao().upsert(MeetingEntity(
            id = "m-denied", organizerId = "op", resourceId = "r",
            title = "B", startTime = 1500L, endTime = 2500L, status = "Denied",
        ))
        val page = db.meetingDao().pageByResource(
            resourceId = "r", rangeStart = 0L, rangeEnd = 5000L,
        )
        assertEquals("denied meetings should not block bookings", 1, page.size)
        assertEquals("m-approved", page[0].id)
    }

    private fun assertNull(value: Any?) {
        org.junit.Assert.assertNull(value)
    }
}
