package com.eaglepoint.task136.shared.di

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.shared.config.CanaryEvaluator
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.governance.GovernanceAnalytics
import com.eaglepoint.task136.shared.governance.ReconciliationService
import com.eaglepoint.task136.shared.governance.RuleHitObserver
import com.eaglepoint.task136.shared.orders.BookingUseCase
import com.eaglepoint.task136.shared.orders.OrderStateMachine
import com.eaglepoint.task136.shared.platform.NotificationGateway
import com.eaglepoint.task136.shared.platform.ReceiptGateway
import com.eaglepoint.task136.shared.rbac.AbacPolicyEvaluator
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.security.DeviceBindingService
import com.eaglepoint.task136.shared.security.LocalAuthService
import com.eaglepoint.task136.shared.security.SecurityRepository
import com.eaglepoint.task136.shared.services.ValidationService
import com.eaglepoint.task136.shared.viewmodel.AuthViewModel
import com.eaglepoint.task136.shared.viewmodel.LearningViewModel
import com.eaglepoint.task136.shared.viewmodel.MeetingWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderFinanceViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SharedModuleWiringTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                modules(databaseModule(db), sharedCoreModule(isDebug = true))
            }
        }
    }

    @After
    fun tearDown() {
        try { stopKoin() } catch (_: Exception) { /* already stopped */ }
        db.close()
    }

    @Test
    fun `all database DAOs are resolvable`() {
        val koin = GlobalContext.get()
        assertNotNull(koin.get<AppDatabase>())
        assertNotNull(koin.get<com.eaglepoint.task136.shared.db.UserDao>())
        assertNotNull(koin.get<com.eaglepoint.task136.shared.db.ResourceDao>())
        assertNotNull(koin.get<com.eaglepoint.task136.shared.db.CartDao>())
        assertNotNull(koin.get<com.eaglepoint.task136.shared.db.OrderDao>())
        assertNotNull(koin.get<com.eaglepoint.task136.shared.db.OrderLineItemDao>())
        assertNotNull(koin.get<com.eaglepoint.task136.shared.db.DeviceBindingDao>())
        assertNotNull(koin.get<com.eaglepoint.task136.shared.db.GovernanceDao>())
        assertNotNull(koin.get<com.eaglepoint.task136.shared.db.MeetingDao>())
        assertNotNull(koin.get<com.eaglepoint.task136.shared.db.WalletDao>())
        assertNotNull(koin.get<com.eaglepoint.task136.shared.db.InvoiceDao>())
    }

    @Test
    fun `security singletons are resolvable`() {
        val koin = GlobalContext.get()
        assertNotNull(koin.get<SecurityRepository>())
        assertNotNull(koin.get<LocalAuthService>())
        assertNotNull(koin.get<DeviceBindingService>())
    }

    @Test
    fun `rbac and validation singletons resolve`() {
        val koin = GlobalContext.get()
        assertNotNull(koin.get<AbacPolicyEvaluator>())
        assertNotNull(koin.get<PermissionEvaluator>())
        assertNotNull(koin.get<ValidationService>())
    }

    @Test
    fun `platform gateways resolve`() {
        val koin = GlobalContext.get()
        assertNotNull(koin.get<NotificationGateway>())
        assertNotNull(koin.get<ReceiptGateway>())
    }

    @Test
    fun `canary evaluator resolves with production manifest`() {
        val koin = GlobalContext.get()
        val canary = koin.get<CanaryEvaluator>()
        assertNotNull(canary)
        // Feature defined in sharedCoreModule production manifest
        val version = canary.resolveFormVersion("meeting_form_v2", 1, com.eaglepoint.task136.shared.rbac.Role.Admin, "default", "admin")
        assertTrue("canary manifest must include meeting_form_v2 feature", version == 2)
    }

    @Test
    fun `order workflow viewmodel resolves with all deps`() {
        val koin = GlobalContext.get()
        val vm = koin.get<OrderWorkflowViewModel>()
        assertNotNull(vm)
    }

    @Test
    fun `order finance viewmodel resolves with all deps`() {
        val koin = GlobalContext.get()
        val vm = koin.get<OrderFinanceViewModel>()
        assertNotNull(vm)
    }

    @Test
    fun `meeting workflow viewmodel resolves with canary evaluator`() {
        val koin = GlobalContext.get()
        val vm = koin.get<MeetingWorkflowViewModel>()
        assertNotNull(vm)
    }

    @Test
    fun `auth viewmodel resolves with full security stack`() {
        val koin = GlobalContext.get()
        val vm = koin.get<AuthViewModel>()
        assertNotNull(vm)
    }

    @Test
    fun `resource list and learning viewmodels resolve`() {
        val koin = GlobalContext.get()
        assertNotNull(koin.get<ResourceListViewModel>())
        assertNotNull(koin.get<LearningViewModel>())
    }

    @Test
    fun `order state machine and booking use case resolve`() {
        val koin = GlobalContext.get()
        assertNotNull(koin.get<OrderStateMachine>())
        assertNotNull(koin.get<BookingUseCase>())
    }

    @Test
    fun `governance services resolve`() {
        val koin = GlobalContext.get()
        assertNotNull(koin.get<GovernanceAnalytics>())
        assertNotNull(koin.get<ReconciliationService>())
        assertNotNull(koin.get<RuleHitObserver>())
    }

    @Test
    fun `repeated resolution returns same singleton instance`() {
        val koin = GlobalContext.get()
        val first = koin.get<OrderWorkflowViewModel>()
        val second = koin.get<OrderWorkflowViewModel>()
        assertSame("singletons must return the same instance", first, second)
    }

    @Test
    fun `database and dao are same underlying instance`() {
        val koin = GlobalContext.get()
        val dbResolved = koin.get<AppDatabase>()
        assertSame(db, dbResolved)
    }
}
