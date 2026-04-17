package com.eaglepoint.task136.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LoginFragmentTest {

    @Test
    fun `LoginFragment class is available`() {
        val clazz = LoginFragment::class.java
        assertNotNull(clazz)
        // Must extend Fragment
        assertTrue(androidx.fragment.app.Fragment::class.java.isAssignableFrom(clazz))
    }

    @Test
    fun `NavigationHost interface exposes navigateToAdmin for admin flow`() {
        // Compile-time contract check: navigateToAdmin must exist on NavigationHost
        val method = NavigationHost::class.java.methods.firstOrNull { it.name == "navigateToAdmin" }
        assertNotNull("NavigationHost must declare navigateToAdmin for admin flow", method)
    }

    @Test
    fun `NavigationHost declares complete navigation surface`() {
        val methods = NavigationHost::class.java.methods.map { it.name }.toSet()
        assertTrue("onAuthenticated" in methods)
        assertTrue("onLogout" in methods)
        assertTrue("navigateToCalendar" in methods)
        assertTrue("navigateToCart" in methods)
        assertTrue("navigateToOrderDetail" in methods)
        assertTrue("navigateToInvoiceDetail" in methods)
        assertTrue("navigateToMeetingDetail" in methods)
        assertTrue("navigateToLearning" in methods)
        assertTrue("navigateToAdmin" in methods)
        assertTrue("navigateBack" in methods)
    }

    @Test
    fun `Fragment subclasses exist for all primary screens`() {
        assertNotNull(LoginFragment::class.java)
        assertNotNull(DashboardFragment::class.java)
        assertNotNull(AdminFragment::class.java)
        assertNotNull(CalendarFragment::class.java)
        assertNotNull(CartFragment::class.java)
        assertNotNull(OrderDetailFragment::class.java)
        assertNotNull(InvoiceDetailFragment::class.java)
        assertNotNull(MeetingDetailFragment::class.java)
        assertNotNull(LearningFragment::class.java)
    }

    @Test
    fun `OrderDetailFragment newInstance attaches arguments`() {
        val fragment = OrderDetailFragment.newInstance("ord-test")
        val args = fragment.arguments
        assertNotNull(args)
        assertEquals("ord-test", args?.getString("orderId"))
    }

    @Test
    fun `InvoiceDetailFragment newInstance attaches arguments`() {
        val fragment = InvoiceDetailFragment.newInstance("inv-42")
        assertEquals("inv-42", fragment.arguments?.getString("invoiceId"))
    }

    @Test
    fun `MeetingDetailFragment newInstance attaches arguments`() {
        val fragment = MeetingDetailFragment.newInstance("mtg-xyz")
        assertEquals("mtg-xyz", fragment.arguments?.getString("meetingId"))
    }
}
