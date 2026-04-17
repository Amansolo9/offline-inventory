package com.eaglepoint.task136.shared.security

import com.eaglepoint.task136.shared.db.UserDao
import com.eaglepoint.task136.shared.db.UserEntity
import com.eaglepoint.task136.shared.rbac.Role
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalAuthServiceTest {

    private val storedUsers = mutableMapOf<String, UserEntity>()

    private val fakeUserDao = object : UserDao {
        override suspend fun upsert(user: UserEntity) { storedUsers[user.id] = user }
        override suspend fun getById(id: String) = storedUsers[id]
        override suspend fun countAll() = storedUsers.size
        override suspend fun update(user: UserEntity) { storedUsers[user.id] = user }
        override suspend fun getAllActive() = storedUsers.values.filter { it.isActive }
    }

    @Test
    fun `seedDemoAccountsIfNeeded seeds five demo accounts in debug`() = runTest {
        storedUsers.clear()
        val service = LocalAuthService(fakeUserDao, enableDemoSeed = true)
        service.seedDemoAccountsIfNeeded()
        assertEquals(5, storedUsers.size)
        assertTrue(storedUsers.containsKey("admin"))
        assertTrue(storedUsers.containsKey("supervisor"))
        assertTrue(storedUsers.containsKey("operator"))
        assertTrue(storedUsers.containsKey("viewer"))
        assertTrue(storedUsers.containsKey("companion"))
    }

    @Test
    fun `seedDemoAccountsIfNeeded is noop when not debug`() = runTest {
        storedUsers.clear()
        val service = LocalAuthService(fakeUserDao, enableDemoSeed = false)
        service.seedDemoAccountsIfNeeded()
        assertEquals(0, storedUsers.size)
    }

    @Test
    fun `seedDemoAccountsIfNeeded skips if users already exist`() = runTest {
        storedUsers.clear()
        storedUsers["preexisting"] = UserEntity(
            id = "preexisting", fullName = "x", email = "x@x", role = Role.Viewer.name,
            maskedPII = "", encryptedWalletRef = "", isActive = true,
        )
        val service = LocalAuthService(fakeUserDao, enableDemoSeed = true)
        service.seedDemoAccountsIfNeeded()
        assertEquals(1, storedUsers.size)
    }

    @Test
    fun `companion account is delegated to operator`() = runTest {
        storedUsers.clear()
        val service = LocalAuthService(fakeUserDao, enableDemoSeed = true)
        service.seedDemoAccountsIfNeeded()
        val companion = storedUsers["companion"]
        assertNotNull(companion)
        assertEquals("operator", companion.delegateForUserId)
    }

    @Test
    fun `authenticate returns principal for valid credentials`() = runTest {
        storedUsers.clear()
        val service = LocalAuthService(fakeUserDao, enableDemoSeed = true)
        service.seedDemoAccountsIfNeeded()
        val principal = service.authenticate("admin", "Admin1234!")
        assertNotNull(principal)
        assertEquals("admin", principal.userId)
        assertEquals(Role.Admin, principal.role)
    }

    @Test
    fun `authenticate returns null for wrong password`() = runTest {
        storedUsers.clear()
        val service = LocalAuthService(fakeUserDao, enableDemoSeed = true)
        service.seedDemoAccountsIfNeeded()
        val principal = service.authenticate("admin", "WrongPassword123!")
        assertNull(principal)
    }

    @Test
    fun `authenticate returns null for unknown username`() = runTest {
        storedUsers.clear()
        val service = LocalAuthService(fakeUserDao, enableDemoSeed = true)
        service.seedDemoAccountsIfNeeded()
        val principal = service.authenticate("no-such-user", "Admin1234!")
        assertNull(principal)
    }

    @Test
    fun `authenticate returns null for inactive user`() = runTest {
        storedUsers.clear()
        val service = LocalAuthService(fakeUserDao, enableDemoSeed = true)
        service.seedDemoAccountsIfNeeded()
        // Deactivate admin
        val admin = storedUsers["admin"]!!
        storedUsers["admin"] = admin.copy(isActive = false)
        val principal = service.authenticate("admin", "Admin1234!")
        assertNull(principal)
    }

    @Test
    fun `authenticate returns supervisor principal with Supervisor role`() = runTest {
        storedUsers.clear()
        val service = LocalAuthService(fakeUserDao, enableDemoSeed = true)
        service.seedDemoAccountsIfNeeded()
        val principal = service.authenticate("supervisor", "Super1234!")
        assertNotNull(principal)
        assertEquals(Role.Supervisor, principal.role)
    }

    @Test
    fun `authenticate companion principal has delegate reference`() = runTest {
        storedUsers.clear()
        val service = LocalAuthService(fakeUserDao, enableDemoSeed = true)
        service.seedDemoAccountsIfNeeded()
        val principal = service.authenticate("companion", "Companion1!")
        assertNotNull(principal)
        assertEquals(Role.Companion, principal.role)
        assertEquals("operator", principal.delegateForUserId)
    }

    @Test
    fun `password hashes are different across users`() = runTest {
        storedUsers.clear()
        val service = LocalAuthService(fakeUserDao, enableDemoSeed = true)
        service.seedDemoAccountsIfNeeded()
        val hashes = storedUsers.values.map { it.passwordHash }.toSet()
        assertEquals(5, hashes.size, "Each user should have unique hash even for simple passwords (salted)")
    }

    @Test
    fun `password salt is non-empty after seeding`() = runTest {
        storedUsers.clear()
        val service = LocalAuthService(fakeUserDao, enableDemoSeed = true)
        service.seedDemoAccountsIfNeeded()
        storedUsers.values.forEach {
            assertTrue(it.passwordSalt.isNotBlank(), "Salt must be present for ${it.id}")
            assertTrue(it.passwordHash.isNotBlank(), "Hash must be present for ${it.id}")
        }
    }
}
