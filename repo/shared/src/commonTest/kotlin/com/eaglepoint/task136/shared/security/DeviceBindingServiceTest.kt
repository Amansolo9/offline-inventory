package com.eaglepoint.task136.shared.security

import com.eaglepoint.task136.shared.db.DeviceBindingDao
import com.eaglepoint.task136.shared.db.DeviceBindingEntity
import com.eaglepoint.task136.shared.rbac.Role
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertIs

class DeviceBindingServiceTest {

    private val testClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-03-30T10:00:00Z")
    }

    private val bindings = mutableListOf<DeviceBindingEntity>()

    private val fakeDao = object : DeviceBindingDao {
        override suspend fun upsert(binding: DeviceBindingEntity) {
            bindings.removeAll { it.id == binding.id }
            bindings.add(binding)
        }
        override suspend fun getByUserId(userId: String) = bindings.filter { it.userId == userId }
        override suspend fun countByUserId(userId: String) = bindings.count { it.userId == userId }
        override suspend fun deleteAllForUser(userId: String) { bindings.removeAll { it.userId == userId } }
        override suspend fun findByUserAndDevice(userId: String, fingerprint: String) =
            bindings.firstOrNull { it.userId == userId && it.deviceFingerprint == fingerprint }
    }

    private fun service(maxDevices: Int = 2) = DeviceBindingService(fakeDao, testClock, maxDevices)

    @Test
    fun `first device bind allowed`() = runTest {
        bindings.clear()
        val result = service().checkAndBindDevice("user1", "device-a")
        assertIs<DeviceBindingResult.Allowed>(result)
        assertEquals(1, bindings.size)
    }

    @Test
    fun `second device bind allowed up to max`() = runTest {
        bindings.clear()
        service().checkAndBindDevice("user1", "device-a")
        val result = service().checkAndBindDevice("user1", "device-b")
        assertIs<DeviceBindingResult.Allowed>(result)
        assertEquals(2, bindings.size)
    }

    @Test
    fun `third device bind blocked at max 2`() = runTest {
        bindings.clear()
        service().checkAndBindDevice("user1", "device-a")
        service().checkAndBindDevice("user1", "device-b")
        val result = service().checkAndBindDevice("user1", "device-c")
        val limitExceeded = assertIs<DeviceBindingResult.LimitExceeded>(result)
        assertEquals(2, limitExceeded.bound)
        assertEquals(2, limitExceeded.max)
    }

    @Test
    fun `rebinding same device is idempotent`() = runTest {
        bindings.clear()
        val s = service()
        s.checkAndBindDevice("user1", "device-a")
        val result = s.checkAndBindDevice("user1", "device-a")
        assertIs<DeviceBindingResult.Allowed>(result)
        assertEquals(1, bindings.size)
    }

    @Test
    fun `isDeviceTrusted returns true only for bound devices`() = runTest {
        bindings.clear()
        val s = service()
        s.checkAndBindDevice("user1", "device-a")
        assertTrue(s.isDeviceTrusted("user1", "device-a"))
        assertFalse(s.isDeviceTrusted("user1", "device-b"))
        assertFalse(s.isDeviceTrusted("user2", "device-a"))
    }

    @Test
    fun `adminResetBindings rejects non-admin role`() = runTest {
        bindings.clear()
        service().checkAndBindDevice("user1", "device-a")
        val viewer = service().adminResetBindings(Role.Viewer, "user1")
        assertFalse(viewer)
        val operator = service().adminResetBindings(Role.Operator, "user1")
        assertFalse(operator)
        val supervisor = service().adminResetBindings(Role.Supervisor, "user1")
        assertFalse(supervisor)
        val companion = service().adminResetBindings(Role.Companion, "user1")
        assertFalse(companion)
        // Bindings should still be intact after non-admin calls
        assertEquals(1, bindings.size)
    }

    @Test
    fun `adminResetBindings clears bindings for admin role`() = runTest {
        bindings.clear()
        val s = service()
        s.checkAndBindDevice("user1", "device-a")
        s.checkAndBindDevice("user1", "device-b")
        val result = s.adminResetBindings(Role.Admin, "user1")
        assertTrue(result)
        assertEquals(0, bindings.count { it.userId == "user1" })
    }

    @Test
    fun `adminResetBindings scopes to given user only`() = runTest {
        bindings.clear()
        val s = service()
        s.checkAndBindDevice("user1", "device-a")
        s.checkAndBindDevice("user2", "device-x")
        s.adminResetBindings(Role.Admin, "user1")
        assertEquals(0, bindings.count { it.userId == "user1" })
        assertEquals(1, bindings.count { it.userId == "user2" })
    }

    @Test
    fun `getDeviceCount reflects bound devices for user`() = runTest {
        bindings.clear()
        val s = service()
        assertEquals(0, s.getDeviceCount("user1"))
        s.checkAndBindDevice("user1", "device-a")
        assertEquals(1, s.getDeviceCount("user1"))
        s.checkAndBindDevice("user1", "device-b")
        assertEquals(2, s.getDeviceCount("user1"))
    }

    @Test
    fun `after admin reset user can bind fresh devices up to max`() = runTest {
        bindings.clear()
        val s = service()
        s.checkAndBindDevice("user1", "device-a")
        s.checkAndBindDevice("user1", "device-b")
        // At limit
        val blocked = s.checkAndBindDevice("user1", "device-c")
        assertIs<DeviceBindingResult.LimitExceeded>(blocked)
        // Admin resets
        s.adminResetBindings(Role.Admin, "user1")
        // Now can bind again
        val retry = s.checkAndBindDevice("user1", "device-c")
        assertIs<DeviceBindingResult.Allowed>(retry)
    }
}
