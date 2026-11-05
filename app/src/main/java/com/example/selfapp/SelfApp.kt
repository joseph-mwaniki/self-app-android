package com.example.selfapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.selfapp.data.DatabaseInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.content.Intent

@HiltAndroidApp
class SelfApp : Application() {

    @Inject
    lateinit var databaseInitializer: DatabaseInitializer

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Start background kiosk service permanently
        try {
            val serviceIntent = Intent(this, com.example.selfapp.service.PurgeTimerService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Asynchronously populate database if empty
        CoroutineScope(Dispatchers.IO).launch {
            databaseInitializer.initialize()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Purge Alerts Channel (High Priority)
            val purgeChannel = NotificationChannel(
                CHANNEL_PURGE,
                "Purge System Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts about active and missed learning Purges."
                enableVibration(true)
            }

            // Streak & Reminders Channel (Default Priority)
            val streakChannel = NotificationChannel(
                CHANNEL_STREAK,
                "Streaks & Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminders and streak update notifications."
            }

            notificationManager?.createNotificationChannel(purgeChannel)
            notificationManager?.createNotificationChannel(streakChannel)
        }
    }

    companion object {
        const val CHANNEL_PURGE = "purge_alerts_channel"
        const val CHANNEL_STREAK = "streak_reminders_channel"
    }
}
