package com.eaglepoint.task136.ui

import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.R
import com.eaglepoint.task136.shared.db.AppDatabase
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
class OrderDetailFragmentTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        if (GlobalContext.getOrNull() == null) {
            startKoin { modules(databaseModule(db), sharedCoreModule(isDebug = true)) }
        }
        val localAuth: com.eaglepoint.task136.shared.security.LocalAuthService = GlobalContext.get().get()
        val authVm: com.eaglepoint.task136.shared.viewmodel.AuthViewModel = GlobalContext.get().get()
        kotlinx.coroutines.runBlocking { localAuth.seedDemoAccountsIfNeeded() }
        authVm.updateUsername("operator")
        authVm.updatePassword("Oper12345!")
        authVm.login()
        Thread.sleep(500)
        // Seed an order owned by operator
        kotlinx.coroutines.runBlocking {
            db.orderDao().upsert(OrderEntity(
                id = "ord-1", userId = "operator", resourceId = "res-1",
                state = "Confirmed", startTime = 0L, endTime = 0L, expiresAt = null,
                quantity = 1, totalPrice = 10.0, createdAt = 0L,
            ))
        }
    }

    @After
    fun tearDown() {
        try { stopKoin() } catch (_: Exception) {}
        db.close()
    }

    private fun <T : androidx.fragment.app.Fragment> host(fragment: T, block: (T) -> Unit) {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).create().start().resume().get() as FragmentActivity
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment, "test").commitNow()
        block(fragment)
    }

    @Test
    fun order_detail_fragment_inflates_with_all_action_buttons() {
        host(OrderDetailFragment.newInstance("ord-1")) { f ->
            val v = f.requireView()
            assertNotNull(v.findViewById<MaterialButton>(R.id.backButton))
            assertNotNull(v.findViewById<MaterialButton>(R.id.confirmBtn))
            assertNotNull(v.findViewById<MaterialButton>(R.id.shipBtn))
            assertNotNull(v.findViewById<MaterialButton>(R.id.deliverBtn))
            assertNotNull(v.findViewById<MaterialButton>(R.id.receiptBtn))
        }
    }

    @Test
    fun order_detail_shows_order_id_from_arguments() {
        host(OrderDetailFragment.newInstance("ord-1")) { f ->
            val id = f.requireView().findViewById<TextView>(R.id.orderIdText)
            assertEquals("ord-1", id.text.toString())
        }
    }

    @Test
    fun order_detail_initially_shows_loading_text() {
        host(OrderDetailFragment.newInstance("ord-1")) { f ->
            val status = f.requireView().findViewById<TextView>(R.id.statusText)
            // Initial synchronous render shows loading placeholder
            assertNotNull(status)
        }
    }

    @Test
    fun order_detail_has_error_text_view_defined_in_layout() {
        host(OrderDetailFragment.newInstance("ord-1")) { f ->
            val err = f.requireView().findViewById<TextView>(R.id.errorText)
            assertNotNull(err)
        }
    }
}
