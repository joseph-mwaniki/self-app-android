package com.example.selfapp.util

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.example.selfapp.MainActivity
import com.example.selfapp.data.dao.PanicConfigDao
import com.example.selfapp.data.entity.AppLockConfig
import com.example.selfapp.data.entity.PanicConfig
import com.example.selfapp.receiver.PanicAlarmReceiver
import com.example.selfapp.receiver.SelfDeviceAdminReceiver
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.Locale

object PanicManager {

    const val SPIN_PACKAGE_NAME = "com.nationaleducationalmusic.spin"

    enum class StrictKioskState {
        CONFIGURED_SUCCESS,
        PARTIALLY_CONFIGURED,
        FAILED_NOT_DEVICE_OWNER,
        FAILED_SPIN_NOT_INSTALLED,
        FAILED_POLICY_ERROR
    }

    data class StrictKioskDiagnostics(
        val state: StrictKioskState,
        val isDeviceOwner: Boolean,
        val spinInstalled: Boolean,
        val spinResolvedActivity: String?,
        val lockTaskAllowlistConfigured: Boolean,
        val persistentPreferredConfigured: Boolean,
        val userRestrictionsConfigured: Boolean,
        val failedSuspendedPackages: List<String>,
        val errorMessage: String? = null
    )

    fun getAdminComponent(context: Context): ComponentName {
        return ComponentName(context, SelfDeviceAdminReceiver::class.java)
    }

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expectedService = "${context.packageName}/com.example.selfapp.service.AppLockAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabledServices.contains(expectedService)
    }

    // --- STRICT LOCK TASK CONFIGURATION ---

    fun configureStrictLockTaskAllowlist(context: Context): Boolean {
        if (!isDeviceOwner(context)) {
            Log.e("PanicManager", "Strict Lock Task failed: Not Device Owner")
            return false
        }
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val adminComponent = getAdminComponent(context)
        val strictPackages = arrayOf(context.packageName, SPIN_PACKAGE_NAME)

        return try {
            dpm.setLockTaskPackages(adminComponent, strictPackages)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                )
            }
            Log.d("PanicManager", "Strict Lock Task allowlist configured: ${strictPackages.joinToString()}")
            true
        } catch (e: Exception) {
            Log.e("PanicManager", "Failed to configure strict Lock Task allowlist", e)
            false
        }
    }

    fun configureLockTaskPackages(context: Context, allowedPackages: List<String>) {
        if (!isDeviceOwner(context)) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = getAdminComponent(context)
        val allAllowed = (allowedPackages + context.packageName + SPIN_PACKAGE_NAME).distinct().toTypedArray()
        try {
            dpm.setLockTaskPackages(adminComponent, allAllowed)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                )
            }
        } catch (e: Exception) {
            Log.e("PanicManager", "Failed to configure lock task packages", e)
        }
    }

    fun enterLockTaskIfPossible(activity: Activity, allowedPackages: List<String>) {
        configureLockTaskPackages(activity, allowedPackages)
        try {
            activity.startLockTask()
            Log.d("PanicManager", "Successfully requested startLockTask()")
        } catch (e: Exception) {
            Log.e("PanicManager", "startLockTask() failed", e)
        }
    }

    fun exitLockTaskIfPossible(activity: Activity) {
        try {
            activity.stopLockTask()
        } catch (e: Exception) {
            Log.e("PanicManager", "stopLockTask() failed", e)
        }
        if (isDeviceOwner(activity)) {
            try {
                configureStrictLockTaskAllowlist(activity)
            } catch (e: Exception) {
                Log.e("PanicManager", "Failed to restore strict allowlist after exiting lock task", e)
            }
        }
    }

    // --- PERSISTENT PREFERRED BROWSER CONFIGURATION ---

    fun resolveSpinActivityComponent(context: Context, spinPackageName: String): ComponentName? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val spinActivity = resolveInfos.find { it.activityInfo.packageName == spinPackageName }?.activityInfo
        if (spinActivity != null) {
            return ComponentName(spinActivity.packageName, spinActivity.name)
        }
        val launchIntent = pm.getLaunchIntentForPackage(spinPackageName)
        val launchComponent = launchIntent?.component
        if (launchComponent != null) {
            return launchComponent
        }
        return null
    }

    fun setSpinAsDefaultBrowser(context: Context, spinPackageName: String): Boolean {
        if (!isDeviceOwner(context)) {
            Log.e("PanicManager", "setSpinAsDefaultBrowser failed: Not Device Owner")
            return false
        }
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val adminComponent = getAdminComponent(context)

        val targetComponent = resolveSpinActivityComponent(context, spinPackageName)
        if (targetComponent == null) {
            Log.e("PanicManager", "setSpinAsDefaultBrowser failed: Could not resolve Spin activity for $spinPackageName")
            return false
        }

        val filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataScheme("http")
            addDataScheme("https")
        }

        return try {
            dpm.addPersistentPreferredActivity(adminComponent, filter, targetComponent)
            Log.d("PanicManager", "Persistent Preferred Activity added successfully for Spin: ${targetComponent.flattenToString()}")
            true
        } catch (e: Exception) {
            Log.e("PanicManager", "Failed to add persistent preferred activity for Spin", e)
            false
        }
    }

    // --- DEVICE OWNER USER RESTRICTIONS ---

    fun applyDeviceOwnerUserRestrictions(context: Context): Boolean {
        if (!isDeviceOwner(context)) return false
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val adminComponent = getAdminComponent(context)

        return try {
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
            }
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            Log.d("PanicManager", "Device Owner user restrictions applied successfully")
            true
        } catch (e: Exception) {
            Log.e("PanicManager", "Failed to apply Device Owner user restrictions", e)
            false
        }
    }

    // --- DEFENSE IN DEPTH PACKAGE SUSPENSION ---

    fun suspendAlternativeBrowsersWithDiagnostics(context: Context): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()
        if (!isDeviceOwner(context)) return result

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return result
        val adminComponent = getAdminComponent(context)
        val spinPkg = detectSpinBrowserPackage(context)

        val browsers = getAllBrowserPackages(context, spinPkg).filter {
            it != context.packageName && it != spinPkg && !it.contains("spin")
        }

        if (browsers.isEmpty()) return result

        try {
            val failedToSuspend = dpm.setPackagesSuspended(adminComponent, browsers.toTypedArray(), true)
            val failedSet = failedToSuspend?.toSet() ?: emptySet()

            for (pkg in browsers) {
                val success = !failedSet.contains(pkg)
                result[pkg] = success
                if (!success) {
                    Log.w("PanicManager", "Package suspension failed for: $pkg")
                }
            }
        } catch (e: Exception) {
            Log.e("PanicManager", "Error suspending alternative browsers", e)
        }
        return result
    }

    // --- UNIFIED STRICT KIOSK POLICY EXECUTION ---

    fun applyStrictSpinKioskPolicy(context: Context): StrictKioskDiagnostics {
        val isDO = isDeviceOwner(context)
        if (!isDO) {
            Log.e("PanicManager", "Strict Kiosk setup failed: Device is not Device Owner")
            return StrictKioskDiagnostics(
                state = StrictKioskState.FAILED_NOT_DEVICE_OWNER,
                isDeviceOwner = false,
                spinInstalled = false,
                spinResolvedActivity = null,
                lockTaskAllowlistConfigured = false,
                persistentPreferredConfigured = false,
                userRestrictionsConfigured = false,
                failedSuspendedPackages = emptyList(),
                errorMessage = "Device is not provisioned as Device Owner"
            )
        }

        val spinPkg = detectSpinBrowserPackage(context)
        val spinActivity = resolveSpinActivityComponent(context, spinPkg)
        val spinInstalled = spinActivity != null

        val lockTaskOk = configureStrictLockTaskAllowlist(context)
        val prefOk = if (spinInstalled) setSpinAsDefaultBrowser(context, spinPkg) else false
        val restrictionsOk = applyDeviceOwnerUserRestrictions(context)

        val suspensionResults = suspendAlternativeBrowsersWithDiagnostics(context)
        val failedSuspensions = suspensionResults.filter { !it.value }.keys.toList()

        val state = when {
            !spinInstalled -> StrictKioskState.FAILED_SPIN_NOT_INSTALLED
            lockTaskOk && prefOk && restrictionsOk && failedSuspensions.isEmpty() -> StrictKioskState.CONFIGURED_SUCCESS
            lockTaskOk || prefOk -> StrictKioskState.PARTIALLY_CONFIGURED
            else -> StrictKioskState.FAILED_POLICY_ERROR
        }

        Log.i("PanicManager", "Strict Spin Kiosk Policy Applied. State: $state, Spin Resolved Component: ${spinActivity?.flattenToString()}")

        return StrictKioskDiagnostics(
            state = state,
            isDeviceOwner = true,
            spinInstalled = spinInstalled,
            spinResolvedActivity = spinActivity?.flattenToString(),
            lockTaskAllowlistConfigured = lockTaskOk,
            persistentPreferredConfigured = prefOk,
            userRestrictionsConfigured = restrictionsOk,
            failedSuspendedPackages = failedSuspensions,
            errorMessage = if (!spinInstalled) "Spin Browser activity could not be resolved" else null
        )
    }

    fun setPackageSuspended(context: Context, packageName: String, suspended: Boolean): Boolean {
        if (!isDeviceOwner(context)) return false
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val adminComponent = getAdminComponent(context)
        var success = false
        try {
            val failed = dpm.setPackagesSuspended(adminComponent, arrayOf(packageName), suspended)
            val failedSet = failed?.toSet() ?: emptySet()
            if (!failedSet.contains(packageName)) {
                success = true
            }
        } catch (e: Exception) {
            Log.w("PanicManager", "setPackagesSuspended failed for $packageName, falling back to setApplicationHidden: ${e.message}")
        }

        if (suspended) {
            if (!success) {
                try {
                    success = dpm.setApplicationHidden(adminComponent, packageName, true)
                } catch (e: Exception) {
                    Log.e("PanicManager", "setApplicationHidden failed for $packageName: ${e.message}")
                }
            }
        } else {
            try {
                dpm.setApplicationHidden(adminComponent, packageName, false)
            } catch (e: Exception) {
                Log.e("PanicManager", "setApplicationHidden unhide failed for $packageName: ${e.message}")
            }
        }
        return success
    }

    fun setPackagesSuspended(context: Context, packageNames: List<String>, suspended: Boolean): Boolean {
        if (!isDeviceOwner(context) || packageNames.isEmpty()) return false
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val adminComponent = getAdminComponent(context)

        var failedSet = emptySet<String>()
        try {
            val failed = dpm.setPackagesSuspended(adminComponent, packageNames.toTypedArray(), suspended)
            failedSet = failed?.toSet() ?: emptySet()
        } catch (e: Exception) {
            Log.w("PanicManager", "setPackagesSuspended batch failed: ${e.message}")
            failedSet = packageNames.toSet()
        }

        for (pkg in packageNames) {
            if (suspended) {
                if (failedSet.contains(pkg)) {
                    try {
                        dpm.setApplicationHidden(adminComponent, pkg, true)
                    } catch (e: Exception) {
                        Log.e("PanicManager", "setApplicationHidden fallback failed for $pkg: ${e.message}")
                    }
                }
            } else {
                try {
                    dpm.setApplicationHidden(adminComponent, pkg, false)
                } catch (e: Exception) {
                    Log.e("PanicManager", "setApplicationHidden unhide failed for $pkg: ${e.message}")
                }
            }
        }
        return true
    }

    fun syncAppLockWithDeviceOwner(context: Context, configs: List<AppLockConfig>) {
        if (!isDeviceOwner(context)) return

        val currentTime = System.currentTimeMillis()
        val activePackages = configs.filter { it.lockedUntil > currentTime }.map { it.packageName }.distinct()
        val expiredPackages = configs.filter { it.lockedUntil <= currentTime }.map { it.packageName }.distinct()

        if (activePackages.isNotEmpty()) {
            setPackagesSuspended(context, activePackages, true)
        }
        if (expiredPackages.isNotEmpty()) {
            setPackagesSuspended(context, expiredPackages, false)
        }
    }

    fun parseAllowedPackages(json: String): List<String> {
        return try {
            Gson().fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun scheduleAlarms(context: Context, config: PanicConfig) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (config.isActive && config.expiresAt > System.currentTimeMillis()) {
            val expireIntent = Intent(context, PanicAlarmReceiver::class.java).apply {
                action = PanicAlarmReceiver.ACTION_PANIC_EXPIRE
            }
            val pendingExpire = PendingIntent.getBroadcast(
                context,
                101,
                expireIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, config.expiresAt, pendingExpire)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, config.expiresAt, pendingExpire)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (config.isScheduleEnabled && config.scheduledStartHour in 0..23 && config.scheduledStartMinute in 0..59) {
            val nextStart = calculateNextTime(config.scheduledStartHour, config.scheduledStartMinute)
            val startIntent = Intent(context, PanicAlarmReceiver::class.java).apply {
                action = PanicAlarmReceiver.ACTION_PANIC_START
            }
            val pendingStart = PendingIntent.getBroadcast(
                context,
                102,
                startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextStart, pendingStart)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextStart, pendingStart)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun calculateNextTime(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    fun launchPanicActivity(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_PANIC", true)
        }
        context.startActivity(intent)
    }

    fun routeUrlToSpin(context: Context, urlString: String): Boolean {
        return try {
            val spinPkg = detectSpinBrowserPackage(context)
            val uri = Uri.parse(if (urlString.startsWith("http://") || urlString.startsWith("https://")) urlString else "https://$urlString")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(spinPkg)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_REROUTED_BY_SELFAPP", true)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun detectSpinBrowserPackage(context: Context): String {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            val label = info.loadLabel(pm).toString()
            if (pkg.contains("spin", ignoreCase = true) || label.contains("spin", ignoreCase = true)) {
                return pkg
            }
        }
        return SPIN_PACKAGE_NAME
    }

    fun getAllBrowserPackages(context: Context, spinPkg: String): List<String> {
        val pm = context.packageManager
        val browserPackages = mutableSetOf<String>()

        val httpIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
        val httpResolves = pm.queryIntentActivities(httpIntent, PackageManager.MATCH_ALL)
        for (info in httpResolves) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && pkg != spinPkg && !pkg.contains("spin")) {
                browserPackages.add(pkg)
            }
        }

        val httpsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
        val httpsResolves = pm.queryIntentActivities(httpsIntent, PackageManager.MATCH_ALL)
        for (info in httpsResolves) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && pkg != spinPkg && !pkg.contains("spin")) {
                browserPackages.add(pkg)
            }
        }

        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val browserKeywords = listOf(
                "browser", "chrome", "opera", "firefox", "brave", "edge", "duckduckgo",
                "sbrowser", "ucbrowser", "kiwi", "yandex", "vivaldi", "puffin", "torproject",
                "dolphin", "via", "internet", "webbrowser", "fennec", "emmx", "mozilla",
                "pure", "soul", "stargon", "monument", "fulguris", "midori", "hermit", "apex",
                "phoenix", "aloha", "xbrowser", "mini", "web", "search", "explore", "navegador", "gx",
                "googlequicksearchbox", "quicksearchbox"
            )
            for (app in installedApps) {
                val pkg = app.packageName.lowercase(Locale.ROOT)
                val label = app.loadLabel(pm).toString().lowercase(Locale.ROOT)

                if (pkg == context.packageName || pkg == spinPkg || pkg.contains("spin")) continue

                val isBrowserPkg = browserKeywords.any { pkg.contains(it) || label.contains(it) }
                if (isBrowserPkg) {
                    browserPackages.add(app.packageName)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return browserPackages.toList()
    }

    fun enforceSpinBrowserOnly(context: Context) {
        applyStrictSpinKioskPolicy(context)
    }
}
