package com.eaglepoint.task136.shared.security

import com.eaglepoint.task136.shared.db.UserDao
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class PasswordPolicyResult(
    val isValid: Boolean,
    val errors: List<String>,
)

class SecurityRepository(
    private val clock: Clock,
    private val userDao: UserDao? = null,
    private val lockoutThreshold: Int = 5,
    private val lockoutDuration: Duration = 15.minutes,
) {
    fun validatePassword(password: String): PasswordPolicyResult {
        val errors = buildList {
            if (password.length < 10) add("Password must be at least 10 characters.")
            if (!password.any(Char::isDigit)) add("Password must include at least one numeric character.")
        }
        return PasswordPolicyResult(isValid = errors.isEmpty(), errors = errors)
    }

    suspend fun canAuthenticate(userId: String, now: Instant = clock.now()): Boolean {
        val dao = userDao ?: return false
        val user = dao.getById(userId) ?: return true
        val lockedUntil = user.lockedUntil ?: return true
        return now.toEpochMilliseconds() >= lockedUntil
    }

    suspend fun recordFailure(userId: String, now: Instant = clock.now()) {
        val dao = userDao ?: return
        val user = dao.getById(userId) ?: return
        val newFailures = user.failedAttempts + 1
        val lockUntil = if (newFailures >= lockoutThreshold) {
            now.plus(lockoutDuration).toEpochMilliseconds()
        } else null
        dao.update(user.copy(failedAttempts = newFailures, lockedUntil = lockUntil))
    }

    suspend fun recordSuccess(userId: String) {
        val dao = userDao ?: return
        val user = dao.getById(userId) ?: return
        dao.update(user.copy(failedAttempts = 0, lockedUntil = null))
    }
}
