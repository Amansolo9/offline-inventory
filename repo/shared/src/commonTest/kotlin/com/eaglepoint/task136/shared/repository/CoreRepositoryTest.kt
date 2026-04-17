package com.eaglepoint.task136.shared.repository

import com.eaglepoint.task136.shared.db.OrderDao
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.db.ResourceDao
import com.eaglepoint.task136.shared.db.ResourceEntity
import com.eaglepoint.task136.shared.db.UserDao
import com.eaglepoint.task136.shared.db.UserEntity
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.rbac.defaultRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CoreRepositoryTest {

    private val testUser = UserEntity(
        id = "user1", fullName = "User One", email = "u1@x", role = Role.Operator.name,
        maskedPII = "masked", encryptedWalletRef = "wallet", isActive = true,
    )

    private val testResource = ResourceEntity(
        id = "res1", name = "Resource One", category = "Operations",
        availableUnits = 10, unitPrice = 5.0, allergens = "none",
    )

    private val testOrder = OrderEntity(
        id = "ord1", userId = "operator", resourceId = "res1",
        state = "Confirmed", startTime = 0L, endTime = 0L, expiresAt = null,
        quantity = 1, totalPrice = 5.0, createdAt = 0L,
    )

    private val fakeUserDao = object : UserDao {
        override suspend fun upsert(user: UserEntity) = Unit
        override suspend fun getById(id: String) = if (id == testUser.id) testUser else null
        override suspend fun countAll() = 1
        override suspend fun update(user: UserEntity) = Unit
        override suspend fun getAllActive() = listOf(testUser)
    }

    private val fakeResourceDao = object : ResourceDao {
        override suspend fun upsert(resource: ResourceEntity) = Unit
        override suspend fun upsertAll(resources: List<ResourceEntity>) = Unit
        override suspend fun update(resource: ResourceEntity) = Unit
        override suspend fun getById(id: String) = if (id == testResource.id) testResource else null
        override suspend fun page(limit: Int, offset: Int) = listOf(testResource)
        override suspend fun countAll() = 1
        override suspend fun deleteById(id: String) = Unit
    }

    private val fakeOrderDao = object : OrderDao {
        override suspend fun upsert(order: OrderEntity) = Unit
        override suspend fun update(order: OrderEntity) = Unit
        override suspend fun getById(orderId: String) = if (orderId == testOrder.id) testOrder else null
        override suspend fun getByIdForActor(orderId: String, actorId: String) =
            if (orderId == testOrder.id && actorId == testOrder.userId) testOrder else null
        override suspend fun getByIdForOwnerOrDelegate(orderId: String, ownerId: String, delegateOwnerId: String) =
            if (orderId == testOrder.id && (ownerId == testOrder.userId || delegateOwnerId == testOrder.userId)) testOrder else null
        override fun observeById(orderId: String): Flow<OrderEntity?> = emptyFlow()
        override suspend fun getActiveByResource(resourceId: String) = emptyList<OrderEntity>()
        override suspend fun deleteById(orderId: String) = Unit
        override suspend fun page(limit: Int) = emptyList<OrderEntity>()
        override suspend fun getExpiredPendingOrders(nowMillis: Long) = emptyList<OrderEntity>()
        override suspend fun sumGrossByDateRange(fromMillis: Long, toMillis: Long) = 0.0
        override suspend fun sumRefundsByDateRange(fromMillis: Long, toMillis: Long) = 0.0
    }

    private val repo = CoreRepository(
        userDao = fakeUserDao,
        resourceDao = fakeResourceDao,
        orderDao = fakeOrderDao,
        permissionEvaluator = PermissionEvaluator(defaultRules()),
    )

    @Test
    fun `getUser allows admin to read user`() = runTest {
        val user = repo.getUser(Role.Admin, "user1")
        assertNotNull(user)
        assertEquals("user1", user.id)
    }

    @Test
    fun `getUser allows supervisor to read masked PII`() = runTest {
        val user = repo.getUser(Role.Supervisor, "user1")
        assertNotNull(user)
    }

    @Test
    fun `getUser returns null for role without user read permission`() = runTest {
        // Viewer has no User.maskedPII Read rule in defaultRules
        val user = repo.getUser(Role.Viewer, "user1")
        assertNull(user)
    }

    @Test
    fun `getResource allows admin read`() = runTest {
        val resource = repo.getResource(Role.Admin, "res1")
        assertNotNull(resource)
        assertEquals("res1", resource.id)
    }

    @Test
    fun `getResource allows viewer read`() = runTest {
        val resource = repo.getResource(Role.Viewer, "res1")
        assertNotNull(resource)
    }

    @Test
    fun `getResource returns null for unknown id`() = runTest {
        val resource = repo.getResource(Role.Admin, "no-such-res")
        assertNull(resource)
    }

    @Test
    fun `getOrder as admin returns any order`() = runTest {
        val order = repo.getOrder(Role.Admin, "ord1", "some-admin")
        assertNotNull(order)
        assertEquals("ord1", order.id)
    }

    @Test
    fun `getOrder as supervisor returns any order`() = runTest {
        val order = repo.getOrder(Role.Supervisor, "ord1", "some-supervisor")
        assertNotNull(order)
    }

    @Test
    fun `getOrder as operator returns own order only`() = runTest {
        val ownOrder = repo.getOrder(Role.Operator, "ord1", "operator")
        assertNotNull(ownOrder)
        val othersOrder = repo.getOrder(Role.Operator, "ord1", "other-user")
        assertNull(othersOrder)
    }

    @Test
    fun `getOrder as companion with delegation returns delegated order`() = runTest {
        val order = repo.getOrder(Role.Companion, "ord1", "companion-x", "operator")
        assertNotNull(order)
    }

    @Test
    fun `getOrder as companion without delegation cannot access other user order`() = runTest {
        val order = repo.getOrder(Role.Companion, "ord1", "companion-x", null)
        assertNull(order)
    }
}
