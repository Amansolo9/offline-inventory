package com.eaglepoint.task136.ui

import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.R
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.db.MeetingEntity
import com.eaglepoint.task136.shared.di.databaseModule
import com.eaglepoint.task136.shared.di.sharedCoreModule
import com.google.android.material.button.MaterialButton
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
class MeetingDetailFragmentTest {
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
            db.meetingDao().upsert(MeetingEntity(
                id = "mtg-1", organizerId = "operator", resourceId = "res-1",
                title = "T", startTime = 0L, endTime = 0L,
                status = com.eaglepoint.task136.shared.viewmodel.MeetingStatus.PendingApproval.name,
                agenda = "Review",
            ))
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
    fun meeting_detail_inflates_with_all_controls() {
        loginAs("supervisor", "Super1234!")
        host(MeetingDetailFragment.newInstance("mtg-1")) { f ->
            val v = f.requireView()
            assertNotNull(v.findViewById<MaterialButton>(R.id.backButton))
            assertNotNull(v.findViewById<MaterialButton>(R.id.addAttendeeBtn))
            assertNotNull(v.findViewById<MaterialButton>(R.id.approveBtn))
            assertNotNull(v.findViewById<MaterialButton>(R.id.denyBtn))
            assertNotNull(v.findViewById<MaterialButton>(R.id.checkInBtn))
            assertNotNull(v.findViewById<MaterialButton>(R.id.addAttachmentBtn))
            assertNotNull(v.findViewById<MaterialButton>(R.id.removeAttachmentBtn))
            assertEquals("mtg-1", v.findViewById<TextView>(R.id.meetingIdText).text.toString())
        }
    }

    @Test
    fun meeting_detail_approve_enabled_for_supervisor_disabled_for_operator() {
        loginAs("supervisor", "Super1234!")
        host(MeetingDetailFragment.newInstance("mtg-1")) { f ->
            val approve = f.requireView().findViewById<MaterialButton>(R.id.approveBtn)
            assertTrue("supervisor can approve", approve.isEnabled)
        }

        val authVm: com.eaglepoint.task136.shared.viewmodel.AuthViewModel = GlobalContext.get().get()
        authVm.logout()
        loginAs("operator", "Oper12345!")
        val meetingVm: com.eaglepoint.task136.shared.viewmodel.MeetingWorkflowViewModel = GlobalContext.get().get()
        meetingVm.clearSessionState()

        host(MeetingDetailFragment.newInstance("mtg-1")) { f ->
            val approve = f.requireView().findViewById<MaterialButton>(R.id.approveBtn)
            assertFalse("operator cannot approve", approve.isEnabled)
        }
    }

    @Test
    fun meeting_detail_viewer_cannot_manage_attendees() {
        loginAs("viewer", "Viewer1234!")
        host(MeetingDetailFragment.newInstance("mtg-1")) { f ->
            val addAttendee = f.requireView().findViewById<MaterialButton>(R.id.addAttendeeBtn)
            assertFalse("viewer cannot add attendees", addAttendee.isEnabled)
        }
    }

    @Test
    fun meeting_detail_has_agenda_and_status_views() {
        loginAs("supervisor", "Super1234!")
        host(MeetingDetailFragment.newInstance("mtg-1")) { f ->
            assertNotNull(f.requireView().findViewById<TextView>(R.id.agendaText))
            assertNotNull(f.requireView().findViewById<TextView>(R.id.statusText))
            assertNotNull(f.requireView().findViewById<TextView>(R.id.attendeesText))
        }
    }

    @Test
    fun meeting_detail_has_toggle_check_in_and_attachment_buttons() {
        loginAs("supervisor", "Super1234!")
        host(MeetingDetailFragment.newInstance("mtg-1")) { f ->
            assertNotNull(f.requireView().findViewById<MaterialButton>(R.id.toggleCheckInBtn))
            assertNotNull(f.requireView().findViewById<MaterialButton>(R.id.addAttachmentBtn))
        }
    }
}
