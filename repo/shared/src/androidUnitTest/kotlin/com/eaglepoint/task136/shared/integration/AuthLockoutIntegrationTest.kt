package com.eaglepoint.task136.shared.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.security.LocalAuthService
import com.eaglepoint.task136.shared.security.SecurityRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AuthLockoutIntegrationTest {
    private lateinit var db: AppDatabase
    private var currentTime = Instant.parse("2026-04-01T10:00:00Z")
    private val movingClock = object : Clock { override fun now(): Instant = currentTime }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() { db.close() }

    private fun securityRepo() = SecurityRepository(movingClock, db.userDao())

    @Test
    fun password_policy_rejects_short_password() {
        val repo = securityRepo()
        val result = repo.validatePassword("short1")
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("10 characters") })
    }

    @Test
    fun password_policy_rejects_password_without_digit() {
        val repo = securityRepo()
        val result = repo.validatePassword("nodigitshere")
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("numeric") })
    }

    @Test
    fun password_policy_accepts_compliant_password() {
        val repo = securityRepo()
        assertTrue(repo.validatePassword("GoodPass12").isValid)
    }

    @Test
    fun five_failures_triggers_15_minute_lockout_persisted_to_room() = runBlocking {
        val auth = LocalAuthService(db.userDao(), enableDemoSeed = true)
        auth.seedDemoAccountsIfNeeded()
        val repo = securityRepo()

        // Simulate 5 failures
        repeat(5) {
            repo.recordFailure("operator")
        }

        val user = db.userDao().getById("operator")
        assertNotNull(user)
        assertTrue("lockedUntil should be set after threshold", user!!.lockedUntil != null)
        // Cannot authenticate during lockout
        assertFalse(repo.canAuthenticate("operator"))

        // Advance past lockout window
        currentTime = currentTime.plus(16.minutes)
        assertTrue("after lockout window must be able to authenticate", repo.canAuthenticate("operator"))
    }

    @Test
    fun recordSuccess_clears_failure_count() = runBlocking {
        val auth = LocalAuthService(db.userDao(), enableDemoSeed = true)
        auth.seedDemoAccountsIfNeeded()
        val repo = securityRepo()
        repeat(3) { repo.recordFailure("admin") }
        repo.recordSuccess("admin")
        val user = db.userDao().getById("admin")
        assertNull(user!!.lockedUntil)
    }

    @Test
    fun demo_seed_produces_authenticatable_accounts_through_room() = runBlocking {
        val auth = LocalAuthService(db.userDao(), enableDemoSeed = true)
        auth.seedDemoAccountsIfNeeded()

        val admin = auth.authenticate("admin", "Admin1234!")
        assertNotNull(admin)
        val viewer = auth.authenticate("viewer", "Viewer1234!")
        assertNotNull(viewer)
        val wrong = auth.authenticate("admin", "WrongPassword123!")
        assertNull(wrong)
    }
}
