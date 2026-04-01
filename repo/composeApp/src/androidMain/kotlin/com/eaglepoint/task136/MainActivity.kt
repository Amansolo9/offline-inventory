package com.eaglepoint.task136

import android.os.Bundle
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.eaglepoint.task136.db.AndroidDatabaseFactory
import com.eaglepoint.task136.di.initKoinIfNeeded
import com.eaglepoint.task136.security.SecurePassphraseProvider
import com.eaglepoint.task136.shared.orders.OrderStateMachine
import com.eaglepoint.task136.shared.platform.AndroidPlatformContext
import com.eaglepoint.task136.shared.security.LocalAuthService
import com.eaglepoint.task136.shared.viewmodel.AuthViewModel
import com.eaglepoint.task136.shared.viewmodel.MeetingWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderFinanceViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import com.eaglepoint.task136.ui.CartFragment
import com.eaglepoint.task136.ui.DashboardFragment
import com.eaglepoint.task136.ui.LearningFragment
import com.eaglepoint.task136.ui.LoginFragment
import com.eaglepoint.task136.ui.NavigationHost
import com.eaglepoint.task136.ui.OrderDetailFragment
import com.eaglepoint.task136.shared.viewmodel.LearningViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class MainActivity : AppCompatActivity(), NavigationHost {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        AndroidPlatformContext.initialize(applicationContext)
        val passphrase = SecurePassphraseProvider(applicationContext).getOrCreatePassphrase()
        val database = AndroidDatabaseFactory(this).create("task136-android.db", passphrase)
        initKoinIfNeeded(database, isDebug = BuildConfig.DEBUG)

        val koin = GlobalContext.get()

        // Seed demo accounts (only in debug) + expire stale orders
        lifecycleScope.launch(Dispatchers.IO) {
            koin.get<LocalAuthService>().seedDemoAccountsIfNeeded()
            koin.get<OrderStateMachine>().expireStaleOrders()
        }

        // Session expiry monitoring — only react to transitions FROM authenticated TO unauthenticated
        val authVm: AuthViewModel = koin.get()
        var wasAuthenticated = authVm.state.value.isAuthenticated
        lifecycleScope.launch {
            authVm.state.collectLatest { state ->
                if (wasAuthenticated && !state.isAuthenticated) {
                    showLogin()
                }
                wasAuthenticated = state.isAuthenticated
            }
        }

        if (savedInstanceState == null) {
            showLogin()
        }
    }

    private fun showLogin() {
        replaceFragment(LoginFragment(), addToBackStack = false)
    }

    private fun showDashboard() {
        replaceFragment(DashboardFragment(), addToBackStack = false)
    }

    private fun replaceFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        val tx = supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
        if (addToBackStack) tx.addToBackStack(null)
        tx.commit()
    }

    // ── NavigationHost implementation ──

    override fun onAuthenticated() {
        showDashboard()
    }

    override fun onLogout() {
        val koin = GlobalContext.get()
        koin.get<ResourceListViewModel>().clearSessionState()
        koin.get<OrderWorkflowViewModel>().clearSessionState()
        koin.get<MeetingWorkflowViewModel>().clearSessionState()
        koin.get<OrderFinanceViewModel>().clearSessionState()
        koin.get<LearningViewModel>().clearSessionState()
        val authVm: AuthViewModel = koin.get()
        authVm.logout()
        showLogin()
    }

    override fun navigateToCalendar() {
        replaceFragment(DashboardFragment()) // Calendar not yet a Fragment
    }

    override fun navigateToCart() {
        replaceFragment(CartFragment())
    }

    override fun navigateToOrderDetail(orderId: String) {
        replaceFragment(OrderDetailFragment.newInstance(orderId))
    }

    override fun navigateToInvoiceDetail(invoiceId: String) {
        replaceFragment(DashboardFragment()) // Invoice detail not yet a Fragment
    }

    override fun navigateToMeetingDetail(meetingId: String) {
        replaceFragment(DashboardFragment()) // Meeting detail not yet a Fragment
    }

    override fun navigateToLearning() {
        replaceFragment(LearningFragment())
    }

    override fun navigateBack() {
        if (!supportFragmentManager.popBackStackImmediate()) {
            showDashboard()
        }
    }
}
