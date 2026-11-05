package com.example.selfapp.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.selfapp.R
import com.example.selfapp.SelfApp
import com.example.selfapp.data.dao.*
import com.example.selfapp.data.entity.*
import com.google.gson.Gson
import java.util.*
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*

@HiltWorker
class PurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val courseDao: CourseDao,
    private val topicDao: TopicDao,
    private val questionDao: QuestionDao,
    private val transformationDao: TransformationDao,
    private val submissionDao: SubmissionDao,
    private val purgeTestDao: PurgeTestDao,
    private val streakDao: StreakDao,
    private val testResultDao: TestResultDao
) : CoroutineWorker(appContext, workerParams) {

    private val sharedPrefs: SharedPreferences =
        appContext.getSharedPreferences("self_app_purge_prefs", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        try {
            val currentTime = System.currentTimeMillis()

            // 1. Check for expired, uncompleted Purges (24-hour window)
            checkExpiredPurges(currentTime)

            // 2. Schedule next Purge if none is planned
            val scheduledTime = sharedPrefs.getLong(KEY_SCHEDULED_PURGE_TIME, 0L)
            if (scheduledTime == 0L || scheduledTime < currentTime - (7 * 24 * 60 * 60 * 1000)) {
                scheduleNextPurge(currentTime)
            } else if (currentTime >= scheduledTime && !sharedPrefs.getBoolean(KEY_PURGE_TRIGGERED, false)) {
                // 3. Trigger Purge
                triggerPurge(currentTime)
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("PurgeWorker", "Error executing Purge check", e)
            return Result.retry()
        }
    }

    private suspend fun checkExpiredPurges(currentTime: Long) {
        val expiredPurges = purgeTestDao.getExpiredUncompletedPurges(currentTime)
        for (purge in expiredPurges) {
            val closedPurge = purge.copy(completed = true, score = 0)
            purgeTestDao.updatePurgeTest(closedPurge)

            // Log missed purge in TestResult table
            testResultDao.insertTestResult(
                TestResult(
                    type = "PURGE",
                    score = 0,
                    timestamp = currentTime
                )
            )

            // Reset Streaks
            val streak = streakDao.getStreak() ?: Streak(id = 1)
            val updatedStreak = streak.copy(
                currentDaily = 0,
                currentWeekly = 0,
                currentPurge = 0,
                missedPurges = streak.missedPurges + 1
            )
            streakDao.insertStreak(updatedStreak)

            // Send notification
            sendNotification(
                title = "PURGE MISSED",
                text = "You did not complete the Purge in time. Your learning streaks have been reset.",
                notificationId = NOTIF_ID_MISSED
            )
        }
    }

    private fun scheduleNextPurge(currentTime: Long) {
        val random = Random()
        // Generate random time in the next 7 days (at least 1 day in the future)
        val oneDayMillis = 24 * 60 * 60 * 1000L
        val minDelay = oneDayMillis
        val maxDelay = 7 * oneDayMillis
        val randomDelay = minDelay + ((maxDelay - minDelay) * random.nextDouble()).toLong()

        val scheduledTime = currentTime + randomDelay
        sharedPrefs.edit()
            .putLong(KEY_SCHEDULED_PURGE_TIME, scheduledTime)
            .putBoolean(KEY_PURGE_TRIGGERED, false)
            .apply()
        
        Log.d("PurgeWorker", "Scheduled next Purge for: ${Date(scheduledTime)}")
    }

    private var ringtonePlayer: MediaPlayer? = null

    private suspend fun triggerPurge(currentTime: Long) {
        sharedPrefs.edit().putBoolean(KEY_PURGE_TRIGGERED, true).apply()

        val allQuestions = questionDao.getAllQuestions()
        if (allQuestions.isEmpty()) return

        val allCourses = courseDao.getAllCoursesList()
        val allTopics = topicDao.getAllTopicsList()

        val questionsByTopic = allQuestions.groupBy { it.topicId }
        val topicsByCourse = allTopics.groupBy { it.courseId }

        val recentSubmissions = submissionDao.getAllSubmissions()
            .filter { it.submittedAt > currentTime - (30 * 24 * 60 * 60 * 1000) }

        val failedQuestions = recentSubmissions
            .filter { it.status == "FAILED" }
            .map { it.questionId }
            .distinct()

        val approvedQuestions = recentSubmissions
            .filter { it.status == "APPROVED" }
            .map { it.questionId }
            .distinct()

        val weightedPool = mutableListOf<String>()

        for (course in allCourses) {
            val courseTopics = topicsByCourse[course.id] ?: emptyList()
            for (topic in courseTopics) {
                val topicQuestions = questionsByTopic[topic.id] ?: emptyList()
                val topicWeight = when {
                    topicQuestions.isEmpty() -> 0
                    else -> 2 + (topicQuestions.size / 10)
                }

                val availableQuestions = topicQuestions.filter {
                    failedQuestions.contains(it.id) || !approvedQuestions.contains(it.id) || true
                }

                val transformations = transformationDao.getTransformationsForQuestionList(
                    topicQuestions.map { it.id }.randomOrNull() ?: continue
                )

                if (availableQuestions.isNotEmpty()) {
                    repeat(topicWeight) {
                        val randomQ = availableQuestions.random()
                        weightedPool.add("q_${randomQ.id}")
                    }
                }

                transformations.take(2).forEach { t ->
                    repeat(2) { weightedPool.add("t_${t.id}") }
                }
            }
        }

        if (weightedPool.size < 10) {
            allQuestions.forEach { q ->
                if (!weightedPool.any { it.contains("q_${q.id}") }) {
                    weightedPool.add("q_${q.id}")
                }
            }
        }

        val quizSelection = weightedPool.distinct().shuffled().take(10)
        if (quizSelection.isEmpty()) return

        val expiresDate = currentTime + (24 * 60 * 60 * 1000L)

        val purgeTest = PurgeTest(
            generatedDate = currentTime,
            expiresDate = expiresDate,
            started = false,
            completed = false,
            score = 0,
            questionsJson = Gson().toJson(quizSelection)
        )
        purgeTestDao.insertPurgeTest(purgeTest)

        playPurgeRingtone()
        sendPurgeNotificationWithTimer()
    }

    private fun playPurgeRingtone() {
        try {
            val prefs = applicationContext.getSharedPreferences("self_app_prefs", Context.MODE_PRIVATE)
            val ringtoneUriString = prefs.getString("purge_ringtone_uri", null)

            val uri = if (!ringtoneUriString.isNullOrEmpty()) {
                Uri.parse(ringtoneUriString)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            ringtonePlayer = MediaPlayer().apply {
                setDataSource(applicationContext, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            CoroutineScope(Dispatchers.IO).launch {
                delay(30000)
                ringtonePlayer?.stop()
                ringtonePlayer?.release()
                ringtonePlayer = null
            }
        } catch (e: Exception) {
            Log.e("PurgeWorker", "Failed to play ringtone", e)
        }
    }

    private fun sendPurgeNotificationWithTimer() {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, com.example.selfapp.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, SelfApp.CHANNEL_PURGE)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠️ PURGE ACTIVE")
            .setContentText("Complete the purge test! Timer: 24:00:00")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setSound(null)

        notificationManager.notify(NOTIF_ID_AVAILABLE, builder.build())
        startTimerUpdateService()
    }

    private fun startTimerUpdateService() {
        val intent = Intent(applicationContext, com.example.selfapp.service.PurgeTimerService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(applicationContext, intent)
    }

    private fun sendNotification(title: String, text: String, notificationId: Int) {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, com.example.selfapp.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, SelfApp.CHANNEL_PURGE)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
    }

    companion object {
        const val KEY_SCHEDULED_PURGE_TIME = "scheduled_purge_time"
        const val KEY_PURGE_TRIGGERED = "purge_triggered"

        private const val NOTIF_ID_AVAILABLE = 1001
        private const val NOTIF_ID_MISSED = 1002
    }
}
