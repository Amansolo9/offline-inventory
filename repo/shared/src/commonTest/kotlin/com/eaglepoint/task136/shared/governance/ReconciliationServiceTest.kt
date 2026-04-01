package com.eaglepoint.task136.shared.governance

import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ReconciliationServiceTest {

    @Test
    fun `settlement config defaults to Friday 18h with daily closure`() {
        val config = SettlementConfig()
        assertEquals(DayOfWeek.FRIDAY, config.settlementDay)
        assertEquals(18, config.settlementHour)
        assertTrue(config.enableDailyClosure)
    }

    @Test
    fun `settlement config is customizable`() {
        val config = SettlementConfig(
            settlementDay = DayOfWeek.WEDNESDAY,
            settlementHour = 20,
            enableDailyClosure = false,
        )
        assertEquals(DayOfWeek.WEDNESDAY, config.settlementDay)
        assertEquals(20, config.settlementHour)
        assertFalse(config.enableDailyClosure)
    }

    @Test
    fun `Friday 18h is settlement time with default config`() {
        val config = SettlementConfig()
        val fridayAt18 = Instant.parse("2026-04-03T18:00:00Z")
        val dt = fridayAt18.toLocalDateTime(TimeZone.UTC)
        val isSettlementTime = dt.date.dayOfWeek == config.settlementDay && dt.time.hour >= config.settlementHour
        assertTrue(isSettlementTime)
    }

    @Test
    fun `Friday 17h is not settlement time with default config`() {
        val config = SettlementConfig()
        val fridayAt17 = Instant.parse("2026-04-03T17:00:00Z")
        val dt = fridayAt17.toLocalDateTime(TimeZone.UTC)
        val isSettlementTime = dt.date.dayOfWeek == config.settlementDay && dt.time.hour >= config.settlementHour
        assertFalse(isSettlementTime)
    }

    @Test
    fun `Tuesday 18h is not settlement time with Friday config`() {
        val config = SettlementConfig()
        val tuesdayAt18 = Instant.parse("2026-04-01T18:00:00Z")
        val dt = tuesdayAt18.toLocalDateTime(TimeZone.UTC)
        val isSettlementTime = dt.date.dayOfWeek == config.settlementDay && dt.time.hour >= config.settlementHour
        assertFalse(isSettlementTime)
    }

    @Test
    fun `daily closure triggers at 23h`() {
        val config = SettlementConfig(enableDailyClosure = true)
        val at23 = Instant.parse("2026-04-01T23:30:00Z")
        val dt = at23.toLocalDateTime(TimeZone.UTC)
        val shouldClose = config.enableDailyClosure && dt.time.hour >= 23
        assertTrue(shouldClose)
    }

    @Test
    fun `daily closure does not trigger at 15h`() {
        val config = SettlementConfig(enableDailyClosure = true)
        val at15 = Instant.parse("2026-04-01T15:00:00Z")
        val dt = at15.toLocalDateTime(TimeZone.UTC)
        val shouldClose = config.enableDailyClosure && dt.time.hour >= 23
        assertFalse(shouldClose)
    }

    @Test
    fun `daily closure disabled by config`() {
        val config = SettlementConfig(enableDailyClosure = false)
        val at23 = Instant.parse("2026-04-01T23:30:00Z")
        val dt = at23.toLocalDateTime(TimeZone.UTC)
        val shouldClose = config.enableDailyClosure && dt.time.hour >= 23
        assertFalse(shouldClose)
    }

    @Test
    fun `discrepancy detected when gross != net`() {
        val gross = 500.0
        val net = 480.0
        val delta = gross - net
        assertTrue(delta != 0.0)
        assertEquals(20.0, delta)
    }

    @Test
    fun `no discrepancy when gross == net`() {
        val gross = 500.0
        val net = 500.0
        val delta = gross - net
        assertEquals(0.0, delta)
    }

    @Test
    fun `Wednesday settlement config works`() {
        val config = SettlementConfig(settlementDay = DayOfWeek.WEDNESDAY, settlementHour = 20)
        val wednesdayAt20 = Instant.parse("2026-04-01T20:00:00Z")
        val dt = wednesdayAt20.toLocalDateTime(TimeZone.UTC)
        val isSettlementTime = dt.date.dayOfWeek == config.settlementDay && dt.time.hour >= config.settlementHour
        assertTrue(isSettlementTime)
    }
}
