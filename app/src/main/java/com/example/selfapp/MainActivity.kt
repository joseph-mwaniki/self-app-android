package com.example.selfapp

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.selfapp.ui.screens.MainAppScreen
import com.example.selfapp.ui.theme.SelfAppTheme
import com.example.selfapp.ui.viewmodel.*
import com.example.selfapp.util.PanicManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val courseViewModel: CourseViewModel by viewModels()
    private val purgeViewModel: PurgeViewModel by viewModels()
    private val testViewModel: TestViewModel by viewModels()
    private val appLockViewModel: AppLockViewModel by viewModels()
    private val dnsBlockViewModel: DnsBlockViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val panicViewModel: PanicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure background persistent service is running
        try {
            val serviceIntent = Intent(this, com.example.selfapp.service.PurgeTimerService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Always enforce Spin Browser and Device Owner policies
        if (PanicManager.isDeviceOwner(this)) {
            PanicManager.applyStrictSpinKioskPolicy(this)
        }

        lifecycleScope.launch {
            panicViewModel.panicConfig.collectLatest { config ->
                if (config != null && config.isActive && System.currentTimeMillis() < config.expiresAt) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val allowed = PanicManager.parseAllowedPackages(config.allowedPackagesJson)
                    PanicManager.enterLockTaskIfPossible(this@MainActivity, allowed)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    PanicManager.exitLockTaskIfPossible(this@MainActivity)
                }
            }
        }

        setContent {
            SelfAppTheme {
                MainAppScreen(
                    dashboardViewModel = dashboardViewModel,
                    courseViewModel = courseViewModel,
                    purgeViewModel = purgeViewModel,
                    testViewModel = testViewModel,
                    appLockViewModel = appLockViewModel,
                    dnsBlockViewModel = dnsBlockViewModel,
                    settingsViewModel = settingsViewModel,
                    panicViewModel = panicViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        panicViewModel.refreshStatus()
        if (PanicManager.isDeviceOwner(this)) {
            PanicManager.enforceSpinBrowserOnly(this)
        }
        val config = panicViewModel.panicConfig.value
        if (config != null && config.isActive && System.currentTimeMillis() < config.expiresAt) {
            val allowed = PanicManager.parseAllowedPackages(config.allowedPackagesJson)
            PanicManager.enterLockTaskIfPossible(this, allowed)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
