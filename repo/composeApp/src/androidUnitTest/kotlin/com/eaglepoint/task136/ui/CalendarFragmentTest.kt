package com.eaglepoint.task136.ui

import androidx.fragment.app.FragmentActivity
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
import org.junit.Assert.assertFalse
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
class CalendarFragmentTest {
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

    private fun loginAs(username: String, password: String) {
        val authVm: com.eaglepoint.task136.shared.viewmodel.AuthViewModel = GlobalContext.get().get()
        authVm.updateUsername(username)
        authVm.updatePassword(password)
        authVm.login()
        Thread.sleep(500)
    }

    private fun <T : androidx.fragment.app.Fragment> host(fragment: T, block: (T) -> Unit) {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).create().start().resume().get() as FragmentActivity
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment, "test").commitNow()
        block(fragment)
    }

    @Test
    fun calendar_inflates_with_agenda_and_attendee_inputs() {
        loginAs("operator", "Oper12345!")
        host(CalendarFragment()) { f ->
            val v = f.requireView()
            assertNotNull(v.findViewById<TextInputEditText>(R.id.agendaInput))
            assertNotNull(v.findViewById<TextInputEditText>(R.id.attendeesInput))
            assertNotNull(v.findViewById<MaterialButton>(R.id.suggestButton))
            assertNotNull(v.findViewById<MaterialButton>(R.id.submitMeetingButton))
            assertNotNull(v.findViewById<MaterialButton>(R.id.openMeetingButton))
        }
    }

    @Test
    fun calendar_submit_button_disabled_for_viewer() {
        loginAs("viewer", "Viewer1234!")
        host(CalendarFragment()) { f ->
            val submit = f.requireView().findViewById<MaterialButton>(R.id.submitMeetingButton)
            assertFalse("Viewer cannot submit meetings", submit.isEnabled)
        }
    }

    @Test
    fun calendar_submit_button_enabled_for_operator() {
        loginAs("operator", "Oper12345!")
        host(CalendarFragment()) { f ->
            val submit = f.requireView().findViewById<MaterialButton>(R.id.submitMeetingButton)
            assertTrue("Operator can submit meetings", submit.isEnabled)
        }
    }

    @Test
    fun calendar_submit_with_agenda_and_attendees_persists_meeting() {
        loginAs("operator", "Oper12345!")
        host(CalendarFragment()) { f ->
            val agenda = f.requireView().findViewById<TextInputEditText>(R.id.agendaInput)
            val attendees = f.requireView().findViewById<TextInputEditText>(R.id.attendeesInput)
            agenda.setText("Q3 review")
            attendees.setText("Alice, Bob")
            f.requireView().findViewById<MaterialButton>(R.id.submitMeetingButton).performClick()
            Thread.sleep(500)
        }
        val stored = kotlinx.coroutines.runBlocking { db.meetingDao().page(limit = 10) }
        assertTrue("Meeting should be persisted", stored.isNotEmpty())
        assertEquals("Q3 review", stored[0].agenda)
    }
}
