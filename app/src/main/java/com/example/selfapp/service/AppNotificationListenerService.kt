package com.example.selfapp.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.selfapp.data.dao.AppLockConfigDao
import com.example.selfapp.data.dao.PanicConfigDao
import com.example.selfapp.data.entity.PanicConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppNotification(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

@AndroidEntryPoint
class AppNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var appLockConfigDao: AppLockConfigDao

    @Inject
    lateinit var panicConfigDao: PanicConfigDao

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var blockedApps = mapOf<String, Long>()
    private var panicConfig: PanicConfig? = null

    companion object {
        private val _activeNotifications = MutableStateFlow<List<AppNotification>>(emptyList())
        val activeNotifications = _activeNotifications.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            appLockConfigDao.getAllLockConfigsFlow().collectLatest { configs ->
                blockedApps = configs.associate { it.packageName to it.lockedUntil }
            }
        }
        serviceScope.launch {
            panicConfigDao.getPanicConfigFlow().collectLatest { config ->
                panicConfig = config
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val packageName = sbn?.packageName ?: return
        if (packageName == this.packageName) return

        val title = sbn.notification.extras.getString("android.title") ?: ""
        val text = sbn.notification.extras.getCharSequence("android.text")?.toString() ?: ""
        
        val notification = AppNotification(
            id = sbn.key,
            packageName = packageName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        // 1. Panic Mode filter
        val pc = panicConfig
        if (pc != null && pc.isActive && System.currentTimeMillis() < pc.expiresAt) {
            val allowedPackages: List<String> = Gson().fromJson(pc.allowedPackagesJson, object : TypeToken<List<String>>() {}.type)
            if (!allowedPackages.contains(packageName)) {
                cancelNotification(sbn.key)
                return
            } else {
                // Add to our active list if allowed
                _activeNotifications.value = (_activeNotifications.value + notification).takeLast(20)
            }
        }

        // 2. Standard Blocked Apps filter
        val lockedUntil = blockedApps[packageName] ?: return
        if (System.currentTimeMillis() < lockedUntil) {
            cancelNotification(sbn.key)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val key = sbn?.key ?: return
        _activeNotifications.value = _activeNotifications.value.filter { it.id != key }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Optionally fetch current notifications
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
