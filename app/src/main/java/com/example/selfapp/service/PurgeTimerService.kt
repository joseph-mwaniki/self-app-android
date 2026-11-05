package com.example.selfapp.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.selfapp.MainActivity
import com.example.selfapp.SelfApp
import com.example.selfapp.data.dao.PurgeTestDao
import com.example.selfapp.data.dao.StreakDao
import com.example.selfapp.data.dao.TestResultDao
import com.example.selfapp.data.entity.Streak
import com.example.selfapp.data.entity.TestResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
import android.app.PendingIntent
import android.os.Build
import android.content.pm.ServiceInfo

import com.example.selfapp.util.PanicManager

@AndroidEntryPoint
class PurgeTimerService : Service() {

    @Inject
    lateinit var streakDao: StreakDao

    @Inject
    lateinit var purgeTestDao: PurgeTestDao

    @Inject
    lateinit var testResultDao: TestResultDao

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var notificationManager: NotificationManager? = null

    private val dynamicPackageReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (context != null && PanicManager.isDeviceOwner(context)) {
                PanicManager.applyStrictSpinKioskPolicy(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Apply strict Spin kiosk policy on service initialization
        if (PanicManager.isDeviceOwner(this)) {
            PanicManager.applyStrictSpinKioskPolicy(this)
        }

        // Dynamically register package install listener so new downloads trigger policy re-application
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            registerReceiver(dynamicPackageReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        startTimer()
        return START_STICKY
    }

    private fun startForegroundService() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, SelfApp.CHANNEL_PURGE)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠️ PURGE ACTIVE")
            .setContentText("Time remaining: 24:00:00")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1001, notification)
        }
    }

    private fun startTimer() {
        serviceScope.launch {
            val currentTime = System.currentTimeMillis()
            val activePurge = purgeTestDao.getActivePurgeTest(currentTime)
            
            var remainingSeconds = if (activePurge != null) {
                ((activePurge.expiresDate - currentTime) / 1000).toInt()
            } else {
                24 * 60 * 60
            }

            while (remainingSeconds > 0) {
                val checkPurge = purgeTestDao.getActivePurgeTest(System.currentTimeMillis())
                if (checkPurge == null) {
                    break
                }

                val hours = remainingSeconds / 3600
                val minutes = (remainingSeconds % 3600) / 60
                val seconds = remainingSeconds % 60

                val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                updateNotification(timeString)

                delay(1000)
                remainingSeconds--
            }

            if (remainingSeconds <= 0) {
                val expiredPurge = purgeTestDao.getActivePurgeTest(System.currentTimeMillis())
                if (expiredPurge != null) {
                    val finishedTest = expiredPurge.copy(
                        completed = true,
                        score = 0,
                        started = false
                    )
                    purgeTestDao.updatePurgeTest(finishedTest)

                    testResultDao.insertTestResult(
                        TestResult(
                            type = "PURGE",
                            score = 0,
                            timestamp = System.currentTimeMillis()
                        )
                    )

                    val streak = streakDao.getStreak() ?: Streak(id = 1)
                    streakDao.insertStreak(
                        streak.copy(
                            currentDaily = 0,
                            currentWeekly = 0,
                            currentPurge = 0,
                            missedPurges = streak.missedPurges + 1
                        )
                    )
                }
                sendExpiredNotification()
            }
            stopSelf()
        }
    }

    private fun updateNotification(timeString: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, SelfApp.CHANNEL_PURGE)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠️ PURGE ACTIVE")
            .setContentText("Time remaining: $timeString")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)

        notificationManager?.notify(1001, builder.build())
    }

    private fun sendExpiredNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, SelfApp.CHANNEL_PURGE)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("❌ PURGE EXPIRED")
            .setContentText("You missed the purge! Streaks have been reset.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager?.notify(1002, builder.build())
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(dynamicPackageReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
