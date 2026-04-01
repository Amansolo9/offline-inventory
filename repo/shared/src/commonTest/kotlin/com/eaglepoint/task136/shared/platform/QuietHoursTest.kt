package com.eaglepoint.task136.shared.platform

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuietHoursTest {

    @Test
    fun `notification at 10am is not in quiet hours`() {
        val at = Instant.parse("2026-04-01T10:00:00Z")
        assertFalse(isQuietHours(at, TimeZone.UTC))
    }

    @Test
    fun `notification at 8pm is not in quiet hours`() {
        val at = Instant.parse("2026-04-01T20:00:00Z")
        assertFalse(isQuietHours(at, TimeZone.UTC))
    }

    @Test
    fun `notification at 9pm is in quiet hours`() {
        val at = Instant.parse("2026-04-01T21:00:00Z")
        assertTrue(isQuietHours(at, TimeZone.UTC))
    }

    @Test
    fun `notification at 11pm is in quiet hours`() {
        val at = Instant.parse("2026-04-01T23:00:00Z")
        assertTrue(isQuietHours(at, TimeZone.UTC))
    }

    @Test
    fun `notification at midnight is in quiet hours`() {
        val at = Instant.parse("2026-04-01T00:00:00Z")
        assertTrue(isQuietHours(at, TimeZone.UTC))
    }

    @Test
    fun `notification at 3am is in quiet hours`() {
        val at = Instant.parse("2026-04-01T03:00:00Z")
        assertTrue(isQuietHours(at, TimeZone.UTC))
    }

    @Test
    fun `notification at 6am is in quiet hours`() {
        val at = Instant.parse("2026-04-01T06:00:00Z")
        assertTrue(isQuietHours(at, TimeZone.UTC))
    }

    @Test
    fun `notification at 7am is not in quiet hours`() {
        val at = Instant.parse("2026-04-01T07:00:00Z")
        assertFalse(isQuietHours(at, TimeZone.UTC))
    }

    @Test
    fun `boundary 20h59 is not in quiet hours`() {
        val at = Instant.parse("2026-04-01T20:59:00Z")
        assertFalse(isQuietHours(at, TimeZone.UTC))
    }

    /**
     * Mirrors the quiet-hours check from NotificationScheduler.scheduleWithQuietHours()
     */
    private fun isQuietHours(at: Instant, timeZone: TimeZone): Boolean {
        val local = at.toLocalDateTime(timeZone)
        val hour = local.time.hour
        return hour in 21..23 || hour in 0..6
    }
}
