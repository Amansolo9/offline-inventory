package com.eaglepoint.task136.shared.security

import com.eaglepoint.task136.shared.db.UserDao
import com.eaglepoint.task136.shared.db.UserEntity
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class SecurityRepositoryTest {
    @Test
    fun `password policy requires at least 10 chars and a number`() {
        val repository = SecurityRepository(clock = FixedClock(Instant.parse("2026-03-30T10:00:00Z")))

        assertFalse(repository.validatePassword("short").isValid)
        assertFalse(repository.validatePassword("longpassword").isValid)
        assertTrue(repository.validatePassword("longpassword1").isValid)
    }

    @Test
    fun `user gets locked after five failures`() = runBlocking {
        val clock = FixedClock(Instant.parse("2026-03-30T10:00:00Z"))
        val fakeDao = object : UserDao {
            private val users = mutableMapOf<String, UserEntity>()
            init {
                users["admin"] = UserEntity(
                    id = "admin", fullName = "Admin", email = "a@b.c", role = "Admin",
                    passwordHash = "", passwordSalt = "", maskedPII = "", encryptedWalletRef = "",
                    isActive = true, failedAttempts = 0, lockedUntil = null,
                )
            }
            override suspend fun upsert(user: UserEntity) { users[user.id] = user }
            override suspend fun getById(id: String) = users[id]
            override suspend fun countAll() = users.size
            override suspend fun update(user: UserEntity) { users[user.id] = user }
            override suspend fun getAllActive() = users.values.filter { it.isActive }
        }

        val repository = SecurityRepository(clock = clock, userDao = fakeDao)

        repeat(5) { repository.recordFailure("admin", clock.now()) }

        assertFalse(repository.canAuthenticate("admin", clock.now()))
        clock.current = clock.current.plus(16.minutes)
        assertTrue(repository.canAuthenticate("admin", clock.now()))
    }
}

private class FixedClock(var current: Instant) : Clock {
    override fun now(): Instant = current
}
