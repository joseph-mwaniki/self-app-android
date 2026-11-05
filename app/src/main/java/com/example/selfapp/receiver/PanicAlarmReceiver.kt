package com.example.selfapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.selfapp.data.dao.AppLockConfigDao
import com.example.selfapp.data.dao.PanicConfigDao
import com.example.selfapp.data.entity.PanicConfig
import com.example.selfapp.util.PanicManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class PanicAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var panicConfigDao: PanicConfigDao

    @Inject
    lateinit var appLockConfigDao: AppLockConfigDao

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure active app locks and Spin Browser enforcement are synced
                val lockConfigs = appLockConfigDao.getAllLockConfigsList()
                PanicManager.syncAppLockWithDeviceOwner(context, lockConfigs)
                if (PanicManager.isDeviceOwner(context)) {
                    PanicManager.enforceSpinBrowserOnly(context)
                }

                val currentConfig = panicConfigDao.getPanicConfig() ?: PanicConfig()

                when (action) {
                    ACTION_PANIC_START -> {
                        val currentTime = System.currentTimeMillis()
                        val allowedPackages = PanicManager.parseAllowedPackages(currentConfig.allowedPackagesJson)

                        // Calculate end time
                        val expiresAt = if (currentConfig.scheduledEndHour in 0..23 && currentConfig.scheduledEndMinute in 0..59) {
                            val endCal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, currentConfig.scheduledEndHour)
                                set(Calendar.MINUTE, currentConfig.scheduledEndMinute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            if (endCal.timeInMillis <= currentTime) {
                                endCal.add(Calendar.DAY_OF_YEAR, 1)
                            }
                            endCal.timeInMillis
                        } else if (currentConfig.durationMinutes > 0) {
                            currentTime + (currentConfig.durationMinutes * 60L * 1000L)
                        } else {
                            currentTime + (60 * 60L * 1000L) // Default 1 hour
                        }

                        val updated = currentConfig.copy(
                            isActive = true,
                            startTimeMillis = currentTime,
                            expiresAt = expiresAt
                        )
                        panicConfigDao.insertPanicConfig(updated)

                        // Apply Lock Task packages and launch Panic interface
                        if (PanicManager.isDeviceOwner(context)) {
                            PanicManager.configureLockTaskPackages(context, allowedPackages)
                        }
                        PanicManager.launchPanicActivity(context)
                        PanicManager.scheduleAlarms(context, updated)
                    }

                    ACTION_PANIC_EXPIRE -> {
                        val updated = currentConfig.copy(
                            isActive = false,
                            expiresAt = 0L
                        )
                        panicConfigDao.insertPanicConfig(updated)
                        
                        // Re-schedule start alarm for next day if schedule enabled
                        if (updated.isScheduleEnabled) {
                            PanicManager.scheduleAlarms(context, updated)
                        }
                    }

                    Intent.ACTION_BOOT_COMPLETED -> {
                        val currentTime = System.currentTimeMillis()
                        if (currentConfig.isActive && currentTime < currentConfig.expiresAt) {
                            // Restore active panic
                            val allowedPackages = PanicManager.parseAllowedPackages(currentConfig.allowedPackagesJson)
                            if (PanicManager.isDeviceOwner(context)) {
                                PanicManager.configureLockTaskPackages(context, allowedPackages)
                            }
                            PanicManager.launchPanicActivity(context)
                            PanicManager.scheduleAlarms(context, currentConfig)
                        } else if (currentConfig.isActive && currentTime >= currentConfig.expiresAt) {
                            // Expired while phone was off
                            val updated = currentConfig.copy(
                                isActive = false,
                                expiresAt = 0L
                            )
                            panicConfigDao.insertPanicConfig(updated)
                            if (updated.isScheduleEnabled) {
                                PanicManager.scheduleAlarms(context, updated)
                            }
                        } else if (currentConfig.isScheduleEnabled) {
                            // Re-arm schedule alarm
                            PanicManager.scheduleAlarms(context, currentConfig)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PANIC_START = "com.example.selfapp.ACTION_PANIC_START"
        const val ACTION_PANIC_EXPIRE = "com.example.selfapp.ACTION_PANIC_EXPIRE"
    }
}
