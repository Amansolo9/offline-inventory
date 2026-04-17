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
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
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
class AdminFragmentTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        if (GlobalContext.getOrNull() == null) {
            startKoin { modules(databaseModule(db), sharedCoreModule(isDebug = true)) }
        }
        val localAuth: com.eaglepoint.task136.shared.security.LocalAuthService = GlobalContext.get().get()
        kotlinx.coroutines.runBlocking { localAuth.seedDemoAccountsIfNeeded() }
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
    fun admin_fragment_inflates_with_all_controls_for_admin() {
        loginAs("admin", "Admin1234!")
        host(AdminFragment()) { f ->
            val v = f.requireView()
            assertNotNull(v.findViewById<MaterialButton>(R.id.backButton))
            assertNotNull(v.findViewById<MaterialButton>(R.id.addResourceBtn))
            assertNotNull(v.findViewById<MaterialButton>(R.id.resetBindingsBtn))
            assertNotNull(v.findViewById<TextInputEditText>(R.id.resourceNameInput))
            assertNotNull(v.findViewById<RecyclerView>(R.id.adminResourceRecyclerView))
        }
    }

    @Test
    fun admin_fragment_shows_business_rules_text() {
        loginAs("admin", "Admin1234!")
        host(AdminFragment()) { f ->
            val price = f.requireView().findViewById<TextView>(R.id.priceRangeText)
            val allergen = f.requireView().findViewById<TextView>(R.id.allergenRuleText)
            val auth = f.requireView().findViewById<TextView>(R.id.authPolicyText)
            assertTrue(price.text.toString().contains("Price"))
            assertTrue(allergen.text.toString().contains("Allergen"))
            assertTrue(auth.text.toString().contains("password"))
        }
    }

    @Test
    fun admin_add_resource_button_click_synchronously_shows_status() {
        loginAs("admin", "Admin1234!")
        host(AdminFragment()) { f ->
            val v = f.requireView()
            v.findViewById<TextInputEditText>(R.id.resourceNameInput).setText("NewResource")
            v.findViewById<TextInputEditText>(R.id.resourceCategoryInput).setText("Ops")
            v.findViewById<TextInputEditText>(R.id.resourceUnitsInput).setText("5")
            v.findViewById<TextInputEditText>(R.id.resourcePriceInput).setText("12.5")
            v.findViewById<MaterialButton>(R.id.addResourceBtn).performClick()

            // addResource(role, ...) returns true synchronously for admin
            // and the fragment calls showStatus immediately
            val status = v.findViewById<TextView>(R.id.statusText)
            assertEquals(View.VISIBLE, status.visibility)
            assertTrue(status.text.toString().contains("added"))
        }
    }

    @Test
    fun admin_add_resource_with_empty_name_shows_validation_error() {
        loginAs("admin", "Admin1234!")
        host(AdminFragment()) { f ->
            val v = f.requireView()
            v.findViewById<TextInputEditText>(R.id.resourceNameInput).setText("")
            v.findViewById<MaterialButton>(R.id.addResourceBtn).performClick()
            val status = v.findViewById<TextView>(R.id.statusText)
            assertEquals(View.VISIBLE, status.visibility)
            assertTrue(status.text.toString().contains("required"))
        }
    }

    @Test
    fun admin_reset_bindings_with_empty_userid_shows_validation_error() {
        loginAs("admin", "Admin1234!")
        host(AdminFragment()) { f ->
            val v = f.requireView()
            v.findViewById<TextInputEditText>(R.id.resetUserIdInput).setText("")
            v.findViewById<MaterialButton>(R.id.resetBindingsBtn).performClick()
            val status = v.findViewById<TextView>(R.id.statusText)
            assertEquals(View.VISIBLE, status.visibility)
            assertTrue(status.text.toString().contains("required"))
        }
    }

    @Test
    fun non_admin_cannot_reach_admin_fragment_functionality() {
        loginAs("operator", "Oper12345!")
        // Fragment should inflate but the onViewCreated guard should navigate back early.
        // Even if inflation returns, non-admin mutations are rejected at the VM layer.
        host(AdminFragment()) { f ->
            // Confirm the fragment instantiated
            assertNotNull(f)
        }
        // Verify core mutation is blocked even if the UI is reached
        val resourceVm: com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel = GlobalContext.get().get()
        val blocked = resourceVm.addResource(
            com.eaglepoint.task136.shared.rbac.Role.Operator,
            "Sneaky", "Ops", 1, 1.0,
        )
        org.junit.Assert.assertFalse("non-admin add resource must be blocked in core", blocked)
    }
}
