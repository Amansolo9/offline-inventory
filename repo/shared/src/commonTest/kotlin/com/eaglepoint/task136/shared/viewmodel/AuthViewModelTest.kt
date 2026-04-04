package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.db.UserDao
import com.eaglepoint.task136.shared.db.UserEntity
import com.eaglepoint.task136.shared.db.DeviceBindingDao
import com.eaglepoint.task136.shared.db.DeviceBindingEntity
import com.eaglepoint.task136.shared.security.DeviceBindingService
import com.eaglepoint.task136.shared.security.LocalAuthService
import com.eaglepoint.task136.shared.security.SecurityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class AuthViewModelTest {
    private fun createFakeUserDao(): UserDao = object : UserDao {
        private val users = mutableMapOf<String, UserEntity>()
        override suspend fun upsert(user: UserEntity) { users[user.id] = user }
        override suspend fun getById(id: String) = users[id]
        override suspend fun countAll() = users.size
        override suspend fun update(user: UserEntity) { users[user.id] = user }
        override suspend fun getAllActive() = users.values.filter { it.isActive }
    }

    private suspend fun createSeededVm(
        clock: Clock = testClock,
    ): Pair<AuthViewModel, UserDao> {
        val dao = createFakeUserDao()
        val authService = LocalAuthService(dao, enableDemoSeed = true)
        authService.seedDemoAccountsIfNeeded()
        val vm = AuthViewModel(
            securityRepository = SecurityRepository(clock = clock, userDao = dao),
            authService = authService,
            clock = clock,
            ioDispatcher = Dispatchers.Unconfined,
        )
        return vm to dao
    }

    @Test
    fun `valid credentials authenticate`() = runBlocking {
        val (vm, _) = createSeededVm()

        vm.updateUsername("admin")
        vm.updatePassword("Admin1234!")
        vm.login()

        assertTrue(vm.state.value.isAuthenticated)
    }

    @Test
    fun `invalid credentials stay unauthenticated`() = runBlocking {
        val (vm, _) = createSeededVm()

        vm.updateUsername("admin")
        vm.updatePassword("wrong11111")
        vm.login()

        assertFalse(vm.state.value.isAuthenticated)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `session expires after timeout`() = runBlocking {
        val mutableClock = MutableClock(Instant.parse("2026-03-30T10:00:00Z"))
        val (vm, _) = createSeededVm(clock = mutableClock)

        vm.updateUsername("admin")
        vm.updatePassword("Admin1234!")
        vm.login()
        assertTrue(vm.state.value.isAuthenticated)

        mutableClock.current = Instant.parse("2026-03-30T10:31:00Z")
        vm.ensureSessionActive()
        assertFalse(vm.state.value.isAuthenticated)
    }

    @Test
    fun `wrong password fails authentication`() = runBlocking {
        val (vm, _) = createSeededVm()

        vm.updateUsername("admin")
        vm.updatePassword("short")
        vm.login()

        assertFalse(vm.state.value.isAuthenticated)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `blank username rejected synchronously`() = runBlocking {
        val (vm, _) = createSeededVm()

        vm.updateUsername("")
        vm.updatePassword("Admin1234!")
        vm.login()

        assertFalse(vm.state.value.isAuthenticated)
        assertTrue(vm.state.value.error?.contains("required") == true)
    }

    @Test
    fun `touch session extends expiry`() = runBlocking {
        val mutableClock = MutableClock(Instant.parse("2026-03-30T10:00:00Z"))
        val (vm, _) = createSeededVm(clock = mutableClock)

        vm.updateUsername("admin")
        vm.updatePassword("Admin1234!")
        vm.login()
        assertTrue(vm.state.value.isAuthenticated)

        mutableClock.current = Instant.parse("2026-03-30T10:25:00Z")
        vm.touchSession()

        mutableClock.current = Instant.parse("2026-03-30T10:50:00Z")
        vm.ensureSessionActive()
        assertTrue(vm.state.value.isAuthenticated)
    }

    @Test
    fun `device limit exceeded blocks login`() = runBlocking {
        val dao = createFakeUserDao()
        val authService = LocalAuthService(dao, enableDemoSeed = true)
        authService.seedDemoAccountsIfNeeded()

        // Fake DAO that always says device limit is exceeded (count >= max)
        val fullDeviceDao = object : DeviceBindingDao {
            override suspend fun upsert(binding: DeviceBindingEntity) = Unit
            override suspend fun getByUserId(userId: String) = listOf(
                DeviceBindingEntity("d1", userId, "fp1", 0L),
                DeviceBindingEntity("d2", userId, "fp2", 0L),
            )
            override suspend fun countByUserId(userId: String) = 2
            override suspend fun deleteAllForUser(userId: String) = Unit
            override suspend fun findByUserAndDevice(userId: String, fingerprint: String): DeviceBindingEntity? = null
        }
        val deviceService = DeviceBindingService(fullDeviceDao, testClock, maxDevices = 2)

        val vm = AuthViewModel(
            securityRepository = SecurityRepository(clock = testClock, userDao = dao),
            authService = authService,
            deviceBindingService = deviceService,
            deviceFingerprint = "new-device-fp",
            clock = testClock,
            ioDispatcher = Dispatchers.Unconfined,
        )

        vm.updateUsername("admin")
        vm.updatePassword("Admin1234!")
        vm.login()

        assertFalse(vm.state.value.isAuthenticated)
        assertTrue(vm.state.value.error?.contains("Device limit") == true)
    }

    @Test
    fun `absolute session limit expires even with touch`() = runBlocking {
        val mutableClock = MutableClock(Instant.parse("2026-03-30T10:00:00Z"))
        val dao = createFakeUserDao()
        val authService = LocalAuthService(dao, enableDemoSeed = true)
        authService.seedDemoAccountsIfNeeded()
        val vm = AuthViewModel(
            securityRepository = SecurityRepository(clock = mutableClock, userDao = dao),
            authService = authService,
            clock = mutableClock,
            absoluteSessionLimit = 2.hours,
            ioDispatcher = Dispatchers.Unconfined,
        )

        vm.updateUsername("admin")
        vm.updatePassword("Admin1234!")
        vm.login()
        assertTrue(vm.state.value.isAuthenticated)

        // Keep touching to prevent idle expiry, but exceed absolute limit
        mutableClock.current = Instant.parse("2026-03-30T11:50:00Z")
        vm.touchSession()
        vm.ensureSessionActive()
        assertTrue(vm.state.value.isAuthenticated)

        mutableClock.current = Instant.parse("2026-03-30T12:01:00Z")
        vm.touchSession()
        vm.ensureSessionActive()
        assertFalse(vm.state.value.isAuthenticated)
    }

    @Test
    fun `logout clears principal`() = runBlocking {
        val (vm, _) = createSeededVm()

        vm.updateUsername("admin")
        vm.updatePassword("Admin1234!")
        vm.login()
        assertTrue(vm.state.value.isAuthenticated)

        vm.logout()
        assertFalse(vm.state.value.isAuthenticated)
    }
}

private val testClock = object : Clock {
    override fun now(): Instant = Instant.parse("2026-03-30T10:00:00Z")
}

private class MutableClock(var current: Instant) : Clock {
    override fun now(): Instant = current
}
