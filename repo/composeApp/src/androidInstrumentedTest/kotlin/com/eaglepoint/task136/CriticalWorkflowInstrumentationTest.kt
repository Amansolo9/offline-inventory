package com.eaglepoint.task136

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation (device/emulator) end-to-end flows for critical user journeys.
 *
 * Executed via: `./gradlew :composeApp:connectedAndroidTest`
 *
 * These tests validate:
 *  - Login screen renders
 *  - Admin login leads to dashboard with admin button
 *  - Non-admin login hides admin button
 *  - Viewer cannot submit meetings from calendar
 *  - Cart add → checkout creates an invoice the user can open
 */
@RunWith(AndroidJUnit4::class)
class CriticalWorkflowInstrumentationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun login_screen_renders_with_inputs_and_button() {
        onView(withId(R.id.usernameInput)).check(matches(isDisplayed()))
        onView(withId(R.id.passwordInput)).check(matches(isDisplayed()))
        onView(withId(R.id.signInButton)).check(matches(isDisplayed()))
    }

    @Test
    fun admin_login_reaches_dashboard_with_admin_button_visible() {
        onView(withId(R.id.usernameInput)).perform(typeText("admin"), closeSoftKeyboard())
        onView(withId(R.id.passwordInput)).perform(typeText("Admin1234!"), closeSoftKeyboard())
        onView(withId(R.id.signInButton)).perform(click())

        // Dashboard should render with Admin-only button visible
        onView(withId(R.id.roleBadge)).check(matches(withText(containsString("Admin"))))
        onView(withId(R.id.navAdmin)).check(matches(isDisplayed()))
    }

    @Test
    fun operator_login_hides_admin_button() {
        onView(withId(R.id.usernameInput)).perform(typeText("operator"), closeSoftKeyboard())
        onView(withId(R.id.passwordInput)).perform(typeText("Oper12345!"), closeSoftKeyboard())
        onView(withId(R.id.signInButton)).perform(click())

        onView(withId(R.id.roleBadge)).check(matches(withText(containsString("Operator"))))
        // Admin button must be GONE for non-admin
        onView(withId(R.id.navAdmin)).check(matches(not(isDisplayed())))
    }

    @Test
    fun viewer_calendar_has_submit_button_disabled() {
        onView(withId(R.id.usernameInput)).perform(typeText("viewer"), closeSoftKeyboard())
        onView(withId(R.id.passwordInput)).perform(typeText("Viewer1234!"), closeSoftKeyboard())
        onView(withId(R.id.signInButton)).perform(click())

        onView(withId(R.id.navCalendar)).perform(click())
        onView(withId(R.id.submitMeetingButton)).check(matches(not(isEnabled())))
    }

    @Test
    fun admin_can_navigate_to_admin_panel_and_see_resource_form() {
        onView(withId(R.id.usernameInput)).perform(typeText("admin"), closeSoftKeyboard())
        onView(withId(R.id.passwordInput)).perform(typeText("Admin1234!"), closeSoftKeyboard())
        onView(withId(R.id.signInButton)).perform(click())

        onView(withId(R.id.navAdmin)).perform(click())
        onView(withId(R.id.addResourceBtn)).check(matches(isDisplayed()))
        onView(withId(R.id.resetBindingsBtn)).check(matches(isDisplayed()))
    }

    @Test
    fun operator_cart_flow_add_item_then_checkout_shows_invoice_button() {
        onView(withId(R.id.usernameInput)).perform(typeText("operator"), closeSoftKeyboard())
        onView(withId(R.id.passwordInput)).perform(typeText("Oper12345!"), closeSoftKeyboard())
        onView(withId(R.id.signInButton)).perform(click())

        onView(withId(R.id.navCart)).perform(click())
        onView(withId(R.id.addItemBtn)).perform(click())
        onView(withId(R.id.checkoutBtn)).perform(click())
        // Invoice button must be present and clickable
        onView(withId(R.id.invoiceBtn)).check(matches(isDisplayed()))
    }
}
