package com.eaglepoint.task136.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NavigationHostContractTest {

    private class FakeNavigationHost : NavigationHost {
        val calls = mutableListOf<String>()
        override fun onAuthenticated() { calls.add("onAuthenticated") }
        override fun onLogout() { calls.add("onLogout") }
        override fun navigateToCalendar() { calls.add("calendar") }
        override fun navigateToCart() { calls.add("cart") }
        override fun navigateToOrderDetail(orderId: String) { calls.add("orderDetail:$orderId") }
        override fun navigateToInvoiceDetail(invoiceId: String) { calls.add("invoiceDetail:$invoiceId") }
        override fun navigateToMeetingDetail(meetingId: String) { calls.add("meetingDetail:$meetingId") }
        override fun navigateToLearning() { calls.add("learning") }
        override fun navigateToAdmin() { calls.add("admin") }
        override fun navigateBack() { calls.add("back") }
    }

    @Test
    fun `NavigationHost has every required navigation endpoint`() {
        val host = FakeNavigationHost()
        host.onAuthenticated()
        host.navigateToCalendar()
        host.navigateToCart()
        host.navigateToOrderDetail("ord-1")
        host.navigateToInvoiceDetail("inv-1")
        host.navigateToMeetingDetail("mtg-1")
        host.navigateToLearning()
        host.navigateToAdmin()
        host.navigateBack()
        host.onLogout()

        assertEquals(
            listOf(
                "onAuthenticated", "calendar", "cart",
                "orderDetail:ord-1", "invoiceDetail:inv-1", "meetingDetail:mtg-1",
                "learning", "admin", "back", "onLogout",
            ),
            host.calls,
        )
    }

    @Test
    fun `orderDetail navigation preserves order id`() {
        val host = FakeNavigationHost()
        host.navigateToOrderDetail("ord-special-123")
        assertTrue(host.calls.contains("orderDetail:ord-special-123"))
    }

    @Test
    fun `invoice detail navigation preserves invoice id`() {
        val host = FakeNavigationHost()
        host.navigateToInvoiceDetail("inv-abc-999")
        assertTrue(host.calls.contains("invoiceDetail:inv-abc-999"))
    }
}
