package com.eaglepoint.task136.ui

import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.R
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.di.databaseModule
import com.eaglepoint.task136.shared.di.sharedCoreModule
import com.eaglepoint.task136.shared.viewmodel.OrderFinanceViewModel
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
class CartFragmentTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        if (GlobalContext.getOrNull() == null) {
            startKoin { modules(databaseModule(db), sharedCoreModule(isDebug = true)) }
        }
        // Seed + login as operator so cart ops are permitted
        val localAuth: com.eaglepoint.task136.shared.security.LocalAuthService = GlobalContext.get().get()
        val authVm: com.eaglepoint.task136.shared.viewmodel.AuthViewModel = GlobalContext.get().get()
        kotlinx.coroutines.runBlocking { localAuth.seedDemoAccountsIfNeeded() }
        authVm.updateUsername("operator")
        authVm.updatePassword("Oper12345!")
        authVm.login()
        Thread.sleep(500)
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
    fun cart_fragment_inflates_with_all_action_buttons() {
        host(CartFragment()) { f ->
            val v = f.requireView()
            assertNotNull(v.findViewById<MaterialButton>(R.id.backButton))
            assertNotNull(v.findViewById<MaterialButton>(R.id.addItemBtn))
            assertNotNull(v.findViewById<MaterialButton>(R.id.splitBtn))
            assertNotNull(v.findViewById<MaterialButton>(R.id.mergeBtn))
            assertNotNull(v.findViewById<MaterialButton>(R.id.checkoutBtn))
            assertNotNull(v.findViewById<MaterialButton>(R.id.invoiceBtn))
        }
    }

    @Test
    fun cart_recyclerview_is_configured_with_diffutil_adapter() {
        host(CartFragment()) { f ->
            val rv = f.requireView().findViewById<RecyclerView>(R.id.cartRecyclerView)
            assertNotNull("RecyclerView must be present", rv)
            assertNotNull("LayoutManager must be configured", rv.layoutManager)
            assertNotNull("Adapter must be set", rv.adapter)
            assertTrue("Adapter must be CartItemAdapter (ListAdapter+DiffUtil)", rv.adapter is CartItemAdapter)
        }
    }

    @Test
    fun cart_add_item_button_click_adds_to_vm_state() {
        host(CartFragment()) { f ->
            val financeVm: OrderFinanceViewModel = GlobalContext.get().get()
            val before = financeVm.state.value.cart.size
            f.requireView().findViewById<MaterialButton>(R.id.addItemBtn).performClick()
            Thread.sleep(200)
            assertTrue("Clicking add should produce a cart item", financeVm.state.value.cart.size > before)
        }
    }

    @Test
    fun cart_note_text_reflects_summary_state() {
        host(CartFragment()) { f ->
            f.requireView().findViewById<MaterialButton>(R.id.addItemBtn).performClick()
            Thread.sleep(200)
            val note = f.requireView().findViewById<TextView>(R.id.noteText)
            assertEquals(View.VISIBLE, note.visibility)
            assertTrue("note must reflect cart state", note.text.toString().contains("Cart"))
        }
    }
}
