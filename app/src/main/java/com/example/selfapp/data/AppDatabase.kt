package com.example.selfapp.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.selfapp.data.dao.*
import com.example.selfapp.data.entity.*

@Database(
    entities = [
        Course::class,
        Topic::class,
        Question::class,
        Transformation::class,
        Submission::class,
        PurgeTest::class,
        Streak::class,
        AppLockConfig::class,
        DnsBlockConfig::class,
        LlmSettings::class,
        DailyTask::class,
        TestResult::class,
        PanicLog::class,
        PanicConfig::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun topicDao(): TopicDao
    abstract fun questionDao(): QuestionDao
    abstract fun transformationDao(): TransformationDao
    abstract fun submissionDao(): SubmissionDao
    abstract fun purgeTestDao(): PurgeTestDao
    abstract fun streakDao(): StreakDao
    abstract fun appLockConfigDao(): AppLockConfigDao
    abstract fun dnsBlockConfigDao(): DnsBlockConfigDao
    abstract fun llmSettingsDao(): LlmSettingsDao
    abstract fun dailyTaskDao(): DailyTaskDao
    abstract fun testResultDao(): TestResultDao
    abstract fun panicLogDao(): PanicLogDao
    abstract fun panicConfigDao(): PanicConfigDao
}
