package com.eaglepoint.task136.ui

import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.R
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.db.DeviceBindingEntity
import com.eaglepoint.task136.shared.db.InvoiceEntity
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.di.databaseModule
import com.eaglepoint.task136.shared.di.sharedCoreModule
import com.google.android.material.button.MaterialButton
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class InvoiceDetailFragmentTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        if (GlobalContext.getOrNull() == null) {
            startKoin { modules(databaseModule(db), sharedCoreModule(isDebug = true)) }
        }
        val localAuth: com.eaglepoint.task136.shared.security.LocalAuthService = GlobalContext.get().get()
        kotlinx.coroutines.runBlocking {
            localAuth.seedDemoAccountsIfNeeded()
            // Seed invoice + linked order + device binding so detail can load + refund is attempted
            db.orderDao().upsert(OrderEntity(
                id = "ord-1", userId = "admin", resourceId = "res-1",
                state = "Confirmed", startTime = 0L, endTime = 0L, expiresAt = null,
                quantity = 1, totalPrice = 50.0, createdAt = 0L,
            ))
            db.invoiceDao().upsert(InvoiceEntity(
                id = "inv-1", subtotal = 50.0, tax = 6.0, total = 56.0,
                orderId = "ord-1", ownerId = "admin", actorId = "admin", createdAt = 0L,
            ))
            db.deviceBindingDao().upsert(DeviceBindingEntity("b1", "admin", "test", 0L))
            db.deviceBindingDao().upsert(DeviceBindingEntity("b2", "operator", "test", 0L))
        }
    }

    @After
    fun tearDown() {
        try { stopKoin() } catch (_: Exception) {}
        db.close()
    }

    private fun loginAs(user: String, pass: String) {
        val authVm: com.eaglepoint.task136.shared.viewmodel.AuthViewModel = GlobalContext.get().get()
        authVm.updateUsername(user); authVm.updatePassword(pass); authVm.login()
        Thread.sleep(500)
    }

    private fun <T : androidx.fragment.app.Fragment> host(fragment: T, block: (T) -> Unit) {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).create().start().resume().get() as FragmentActivity
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment, "test").commitNow()
        block(fragment)
    }

    @Test
    fun invoice_detail_inflates_with_id_and_refund_button() {
        loginAs("admin", "Admin1234!")
        host(InvoiceDetailFragment.newInstance("inv-1")) { f ->
            val v = f.requireView()
            assertNotNull(v.findViewById<MaterialButton>(R.id.backButton))
            assertNotNull(v.findViewById<MaterialButton>(R.id.refundBtn))
            assertEquals("inv-1", v.findViewById<TextView>(R.id.invoiceIdText).text.toString())
        }
    }

    @Test
    fun invoice_detail_has_all_amount_text_views() {
        loginAs("admin", "Admin1234!")
        host(InvoiceDetailFragment.newInstance("inv-1")) { f ->
            val v = f.requireView()
            assertNotNull(v.findViewById<TextView>(R.id.subtotalText))
            assertNotNull(v.findViewById<TextView>(R.id.taxText))
            assertNotNull(v.findViewById<TextView>(R.id.totalText))
            assertNotNull(v.findViewById<TextView>(R.id.noteText))
        }
    }

    @Test
    fun invoice_detail_refund_button_clickable_for_admin() {
        loginAs("admin", "Admin1234!")
        host(InvoiceDetailFragment.newInstance("inv-1")) { f ->
            val refund = f.requireView().findViewById<MaterialButton>(R.id.refundBtn)
            assertTrue(refund.isEnabled)
        }
    }
}
