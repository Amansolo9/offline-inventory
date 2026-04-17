package com.eaglepoint.task136.ui

import androidx.fragment.app.Fragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FragmentContractTest {

    @Test
    fun all_primary_fragments_extend_Fragment() {
        val classes = listOf(
            LoginFragment::class.java,
            DashboardFragment::class.java,
            AdminFragment::class.java,
            CalendarFragment::class.java,
            CartFragment::class.java,
            OrderDetailFragment::class.java,
            InvoiceDetailFragment::class.java,
            MeetingDetailFragment::class.java,
            LearningFragment::class.java,
        )
        classes.forEach { cls ->
            assertTrue("${cls.simpleName} must extend Fragment",
                Fragment::class.java.isAssignableFrom(cls))
        }
    }

    @Test
    fun fragments_with_arguments_provide_newInstance_factory() {
        assertNotNull(OrderDetailFragment.newInstance("x"))
        assertNotNull(InvoiceDetailFragment.newInstance("x"))
        assertNotNull(MeetingDetailFragment.newInstance("x"))
    }

    @Test
    fun detail_fragments_preserve_id_argument_through_newInstance() {
        assertEquals("ord-123", OrderDetailFragment.newInstance("ord-123").arguments?.getString("orderId"))
        assertEquals("inv-456", InvoiceDetailFragment.newInstance("inv-456").arguments?.getString("invoiceId"))
        assertEquals("mtg-789", MeetingDetailFragment.newInstance("mtg-789").arguments?.getString("meetingId"))
    }

    @Test
    fun NavigationHost_exposes_all_required_destinations() {
        val methods = NavigationHost::class.java.methods.map { it.name }.toSet()
        listOf(
            "onAuthenticated",
            "onLogout",
            "navigateToCalendar",
            "navigateToCart",
            "navigateToOrderDetail",
            "navigateToInvoiceDetail",
            "navigateToMeetingDetail",
            "navigateToLearning",
            "navigateToAdmin",
            "navigateBack",
        ).forEach { name ->
            assertTrue("NavigationHost must expose $name", name in methods)
        }
    }

    @Test
    fun NavigationHost_admin_route_has_no_parameters() {
        val method = NavigationHost::class.java.methods.firstOrNull { it.name == "navigateToAdmin" }
        assertNotNull(method)
        assertEquals("navigateToAdmin should not take parameters", 0, method!!.parameterCount)
    }

    @Test
    fun detail_navigation_methods_take_string_id_parameter() {
        listOf("navigateToOrderDetail", "navigateToInvoiceDetail", "navigateToMeetingDetail").forEach { name ->
            val method = NavigationHost::class.java.methods.first { it.name == name }
            assertEquals("$name should take 1 parameter", 1, method.parameterCount)
            assertEquals("$name param should be String", String::class.java, method.parameterTypes[0])
        }
    }

    @Test
    fun fragment_classes_have_default_public_constructor() {
        // Fragment subclasses must have a no-arg public constructor per Android spec
        listOf(
            LoginFragment::class.java,
            DashboardFragment::class.java,
            AdminFragment::class.java,
            CalendarFragment::class.java,
            CartFragment::class.java,
            OrderDetailFragment::class.java,
            InvoiceDetailFragment::class.java,
            MeetingDetailFragment::class.java,
            LearningFragment::class.java,
        ).forEach { cls ->
            val ctor = cls.getDeclaredConstructor()
            assertNotNull("${cls.simpleName} must have a no-arg ctor", ctor)
            assertTrue("${cls.simpleName} ctor must be accessible",
                java.lang.reflect.Modifier.isPublic(ctor.modifiers))
        }
    }
}
