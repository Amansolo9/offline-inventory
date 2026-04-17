package com.eaglepoint.task136.shared.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.db.DeviceBindingEntity
import com.eaglepoint.task136.shared.db.ResourceEntity
import com.eaglepoint.task136.shared.di.databaseModule
import com.eaglepoint.task136.shared.di.sharedCoreModule
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.security.LocalAuthService
import com.eaglepoint.task136.shared.viewmodel.AuthViewModel
import com.eaglepoint.task136.shared.viewmodel.MeetingStatus
import com.eaglepoint.task136.shared.viewmodel.MeetingWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderFinanceViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end tests with **real** wiring:
 *  - real Koin container
 *  - real in-memory Room database
 *  - real ViewModels (no fakes)
 *  - real PermissionEvaluator/AbacPolicyEvaluator/SecurityRepository/DeviceBindingService
 *  - real LocalAuthService seeding
 *
 * These exercise complete flows through the DI graph and persistence boundary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RealKoinEndToEndTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        if (GlobalContext.getOrNull() == null) {
            startKoin { modules(databaseModule(db), sharedCoreModule(isDebug = true)) }
        }
        val localAuth: LocalAuthService = GlobalContext.get().get()
        runBlocking { localAuth.seedDemoAccountsIfNeeded() }
    }

    @After
    fun tearDown() {
        try { stopKoin() } catch (_: Exception) {}
        db.close()
    }

    private fun loginAs(user: String, pass: String): AuthViewModel {
        val authVm: AuthViewModel = GlobalContext.get().get()
        authVm.clearSessionIfAnyForTest()
        authVm.updateUsername(user)
        authVm.updatePassword(pass)
        authVm.login()
        Thread.sleep(400)
        return authVm
    }

    // Helper: shed any prior auth state before the next login
    private fun AuthViewModel.clearSessionIfAnyForTest() {
        if (state.value.isAuthenticated) this.logout()
    }

    @Test
    fun admin_login_then_resource_mutation_persists_via_real_koin_graph() {
        val authVm = loginAs("admin", "Admin1234!")
        assertEquals(Role.Admin, authVm.state.value.role)

        val resourceVm: ResourceListViewModel = GlobalContext.get().get()
        val ok = resourceVm.addResource(Role.Admin, "RealKoinRes", "Ops", 7, 4.5)
        assertTrue(ok)
        Thread.sleep(400)

        val persisted = runBlocking { db.resourceDao().page(10, 0) }
        assertTrue("admin add should be persisted via real VM+DAO", persisted.any { it.name == "RealKoinRes" })
    }

    @Test
    fun operator_login_cannot_mutate_resources_via_real_vm() {
        loginAs("operator", "Oper12345!")
        val resourceVm: ResourceListViewModel = GlobalContext.get().get()
        val blocked = resourceVm.addResource(Role.Operator, "Sneak", "Ops", 1, 1.0)
        assertFalse(blocked)
        Thread.sleep(200)
        val persisted = runBlocking { db.resourceDao().page(10, 0) }
        assertFalse(persisted.any { it.name == "Sneak" })
    }

    @Test
    fun viewer_login_cannot_submit_meeting_via_real_vm_and_room() {
        loginAs("viewer", "Viewer1234!")
        val meetingVm: MeetingWorkflowViewModel = GlobalContext.get().get()
        meetingVm.submitMeeting(
            role = Role.Viewer, organizerId = "viewer", actorId = "viewer",
            agenda = "blocked", attendeeNames = listOf("a", "b"),
        )
        Thread.sleep(400)

        val stored = runBlocking { db.meetingDao().page(limit = 10) }
        assertEquals(0, stored.size)
        assertTrue(meetingVm.state.value.note?.contains("denied") == true)
    }

    @Test
    fun operator_login_can_submit_meeting_with_attendees_via_real_vm_and_room() {
        loginAs("operator", "Oper12345!")
        val meetingVm: MeetingWorkflowViewModel = GlobalContext.get().get()
        meetingVm.submitMeeting(
            role = Role.Operator,
            organizerId = "operator",
            actorId = "operator",
            agenda = "Sprint plan",
            attendeeNames = listOf("Alice", "Bob"),
        )
        Thread.sleep(500)

        val stored = runBlocking { db.meetingDao().page(limit = 10) }
        assertEquals(1, stored.size)
        assertEquals("Sprint plan", stored[0].agenda)
        assertEquals(MeetingStatus.PendingApproval.name, stored[0].status)
        val attendees = runBlocking { db.meetingDao().getAttendees(stored[0].id) }
        assertEquals(2, attendees.size)
    }

    @Test
    fun admin_cart_add_through_real_vm_persists_cart_item() {
        loginAs("admin", "Admin1234!")
        val financeVm: OrderFinanceViewModel = GlobalContext.get().get()
        financeVm.addCartItem(Role.Admin, "admin", null, "res-seeded", "Seeded", 2, 5.0)
        Thread.sleep(300)

        val items = runBlocking { db.cartDao().getByUser("admin") }
        assertTrue("cart item should be persisted via real VM", items.isNotEmpty())
    }

    @Test
    fun session_expiry_logout_clears_authenticated_state_in_real_vm() {
        val authVm = loginAs("admin", "Admin1234!")
        assertTrue(authVm.state.value.isAuthenticated)
        authVm.logout()
        Thread.sleep(200)
        assertFalse(authVm.state.value.isAuthenticated)
    }

    @Test
    fun order_workflow_viewmodel_resolves_through_real_koin() {
        loginAs("admin", "Admin1234!")
        val orderVm: OrderWorkflowViewModel = GlobalContext.get().get()
        assertNotNull(orderVm)
        // Initial state should be clean
        assertEquals(null, orderVm.state.value.lastOrderId)
    }

    @Test
    fun companion_user_seeded_with_delegate_reference_in_room() {
        val user = runBlocking { db.userDao().getById("companion") }
        assertNotNull(user)
        assertEquals(Role.Companion.name, user!!.role)
        assertEquals("operator", user.delegateForUserId)
    }

    @Test
    fun direct_authenticate_call_returns_companion_principal_with_delegate() {
        val auth: LocalAuthService = GlobalContext.get().get()
        val principal = runBlocking { auth.authenticate("companion", "Companion1!") }
        assertNotNull(principal)
        assertEquals(Role.Companion, principal!!.role)
        assertEquals("operator", principal.delegateForUserId)
    }

    @Test
    fun direct_security_repo_records_failures_and_locks_user_in_room() {
        val securityRepo: com.eaglepoint.task136.shared.security.SecurityRepository = GlobalContext.get().get()
        runBlocking {
            repeat(5) { securityRepo.recordFailure("operator") }
        }
        val user = runBlocking { db.userDao().getById("operator") }
        assertNotNull(user)
        assertTrue("5 failures must set lockedUntil in Room", user!!.lockedUntil != null)
        // canAuthenticate should now return false
        val can = runBlocking { securityRepo.canAuthenticate("operator") }
        assertFalse("canAuthenticate should reject locked user", can)
    }
}
