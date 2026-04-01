package com.eaglepoint.task136.shared.services

import com.eaglepoint.task136.shared.db.ResourceEntity
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidationServiceTest {
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-03-30T10:00:00Z")
    }
    private val service = ValidationService(clock)

    @Test
    fun `price below minimum is rejected`() {
        assertNotNull(service.validatePrice(0.001))
    }

    @Test
    fun `price above maximum is rejected`() {
        assertNotNull(service.validatePrice(10_000.00))
    }

    @Test
    fun `price at minimum boundary is accepted`() {
        assertNull(service.validatePrice(0.01))
    }

    @Test
    fun `price at maximum boundary is accepted`() {
        assertNull(service.validatePrice(9_999.99))
    }

    @Test
    fun `normal price is accepted`() {
        assertNull(service.validatePrice(49.99))
    }

    @Test
    fun `blank allergens are rejected`() {
        val resource = ResourceEntity(id = "1", name = "Test", category = "A", availableUnits = 1, unitPrice = 10.0, allergens = "")
        assertNotNull(service.validateAllergens(resource))
    }

    @Test
    fun `non-blank allergens are accepted`() {
        val resource = ResourceEntity(id = "1", name = "Test", category = "A", availableUnits = 1, unitPrice = 10.0, allergens = "none")
        assertNull(service.validateAllergens(resource))
    }

    @Test
    fun `allergen flag validation rejects blank`() {
        assertFalse(service.validateAllergenFlags(""))
        assertFalse(service.validateAllergenFlags("   "))
    }

    @Test
    fun `allergen flag validation accepts non-blank`() {
        assertTrue(service.validateAllergenFlags("gluten"))
    }

    @Test
    fun `check-in within window is valid`() {
        val meetingStart = Instant.parse("2026-03-30T10:00:00Z")
        val checkInTime = Instant.parse("2026-03-30T10:05:00Z")
        assertTrue(service.isWithinSupervisorWindow(meetingStart, checkInTime))
    }

    @Test
    fun `check-in outside window is invalid`() {
        val meetingStart = Instant.parse("2026-03-30T10:00:00Z")
        val checkInTime = Instant.parse("2026-03-30T10:15:00Z")
        assertFalse(service.isWithinSupervisorWindow(meetingStart, checkInTime))
    }

    @Test
    fun `check-in before window is valid within 10 min`() {
        val meetingStart = Instant.parse("2026-03-30T10:00:00Z")
        val earlyTime = Instant.parse("2026-03-30T09:52:00Z")
        assertTrue(service.isWithinSupervisorWindow(meetingStart, earlyTime))
    }
}
