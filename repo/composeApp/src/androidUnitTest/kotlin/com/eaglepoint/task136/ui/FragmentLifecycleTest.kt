package com.eaglepoint.task136.ui

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.R
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.di.databaseModule
import com.eaglepoint.task136.shared.di.sharedCoreModule
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
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
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

class TestHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Task136)
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply { id = android.R.id.content }
        setContentView(container)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class FragmentLifecycleTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        if (GlobalContext.getOrNull() == null) {
            startKoin { modules(databaseModule(db), sharedCoreModule(isDebug = true)) }
        }
    }

    @After
    fun tearDown() {
        try { stopKoin() } catch (_: Exception) {}
        db.close()
    }

    private fun <T : androidx.fragment.app.Fragment> launchFragment(fragment: T, block: (T) -> Unit) {
        val controller = Robolectric.buildActivity(TestHostActivity::class.java).create().start().resume()
        val activity = controller.get() as FragmentActivity
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "test")
            .commitNow()
        block(fragment)
    }

    @Test
    fun LoginFragment_view_is_inflated_with_inputs_and_button() {
        launchFragment(LoginFragment()) { fragment ->
            val view = fragment.requireView()
            assertNotNull(view.findViewById<TextInputEditText>(R.id.usernameInput))
            assertNotNull(view.findViewById<TextInputEditText>(R.id.passwordInput))
            val signInBtn = view.findViewById<MaterialButton>(R.id.signInButton)
            assertNotNull(signInBtn)
            assertTrue(signInBtn.isEnabled)
        }
    }

    @Test
    fun LoginFragment_error_text_hidden_on_first_show() {
        launchFragment(LoginFragment()) { fragment ->
            val error = fragment.requireView().findViewById<TextView>(R.id.errorText)
            assertEquals(View.GONE, error.visibility)
        }
    }

    @Test
    fun LoginFragment_typing_username_updates_viewmodel() {
        launchFragment(LoginFragment()) { fragment ->
            val input = fragment.requireView().findViewById<TextInputEditText>(R.id.usernameInput)
            input.setText("admin")
            val authVm: com.eaglepoint.task136.shared.viewmodel.AuthViewModel = GlobalContext.get().get()
            assertEquals("admin", authVm.state.value.username)
        }
    }

    @Test
    fun LoginFragment_sign_in_with_invalid_password_shows_error() {
        val fragment = LoginFragment()
        launchFragment(fragment) {
            val view = fragment.requireView()
            view.findViewById<TextInputEditText>(R.id.usernameInput).setText("admin")
            view.findViewById<TextInputEditText>(R.id.passwordInput).setText("short")
            view.findViewById<MaterialButton>(R.id.signInButton).performClick()
        }
        Thread.sleep(500)
        val view = fragment.requireView()
        val error = view.findViewById<TextView>(R.id.errorText)
        assertEquals(View.VISIBLE, error.visibility)
        assertFalse(error.text.isNullOrBlank())
    }

    @Test
    fun DashboardFragment_admin_button_visibility_reflects_role() {
        val authVm: com.eaglepoint.task136.shared.viewmodel.AuthViewModel = GlobalContext.get().get()
        val localAuth: com.eaglepoint.task136.shared.security.LocalAuthService = GlobalContext.get().get()
        kotlinx.coroutines.runBlocking { localAuth.seedDemoAccountsIfNeeded() }

        // Login as admin
        authVm.updateUsername("admin")
        authVm.updatePassword("Admin1234!")
        authVm.login()
        Thread.sleep(500)

        launchFragment(DashboardFragment()) { fragment ->
            val adminBtn = fragment.requireView().findViewById<MaterialButton>(R.id.navAdmin)
            assertEquals("admin should see admin button", View.VISIBLE, adminBtn.visibility)
        }
    }

    @Test
    fun DashboardFragment_non_admin_hides_admin_button() {
        val authVm: com.eaglepoint.task136.shared.viewmodel.AuthViewModel = GlobalContext.get().get()
        val localAuth: com.eaglepoint.task136.shared.security.LocalAuthService = GlobalContext.get().get()
        kotlinx.coroutines.runBlocking { localAuth.seedDemoAccountsIfNeeded() }

        authVm.updateUsername("operator")
        authVm.updatePassword("Oper12345!")
        authVm.login()
        Thread.sleep(500)

        launchFragment(DashboardFragment()) { fragment ->
            val adminBtn = fragment.requireView().findViewById<MaterialButton>(R.id.navAdmin)
            assertEquals("operator should NOT see admin button", View.GONE, adminBtn.visibility)
        }
    }

    @Test
    fun DashboardFragment_exposes_logout_and_nav_buttons() {
        val authVm: com.eaglepoint.task136.shared.viewmodel.AuthViewModel = GlobalContext.get().get()
        val localAuth: com.eaglepoint.task136.shared.security.LocalAuthService = GlobalContext.get().get()
        kotlinx.coroutines.runBlocking { localAuth.seedDemoAccountsIfNeeded() }
        authVm.updateUsername("operator")
        authVm.updatePassword("Oper12345!")
        authVm.login()
        Thread.sleep(500)

        launchFragment(DashboardFragment()) { fragment ->
            val view = fragment.requireView()
            assertNotNull(view.findViewById<MaterialButton>(R.id.logoutButton))
            assertNotNull(view.findViewById<MaterialButton>(R.id.navCalendar))
            assertNotNull(view.findViewById<MaterialButton>(R.id.navCart))
            assertNotNull(view.findViewById<MaterialButton>(R.id.navLearning))
        }
    }
}
