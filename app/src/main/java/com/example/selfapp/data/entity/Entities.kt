package com.example.selfapp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Topic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val title: String
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = Topic::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: Long,
    val questionText: String,
    val difficulty: String, // EASY, MEDIUM, HARD
    val points: Int
)

@Entity(
    tableName = "transformations",
    foreignKeys = [
        ForeignKey(
            entity = Question::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Transformation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val transformedQuestion: String
)

@Entity(tableName = "submissions")
data class Submission(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val reasoningFilePath: String, // Path in Scoped Local storage
    val solutionFilePathsJson: String, // JSON array of file paths
    val score: Int,
    val status: String, // PENDING, APPROVED, FAILED
    val feedback: String = "",
    val submittedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "purge_tests")
data class PurgeTest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val generatedDate: Long,
    val expiresDate: Long,
    val started: Boolean = false,
    val completed: Boolean = false,
    val score: Int = 0,
    val startedAt: Long? = null,
    val questionsJson: String, // JSON array of Question IDs or Transformation IDs included in the quiz
    val answersJson: String = "[]" // JSON array of text responses
)

@Entity(tableName = "streaks")
data class Streak(
    @PrimaryKey val id: Int = 1,
    val currentDaily: Int = 0,
    val longestDaily: Int = 0,
    val currentWeekly: Int = 0,
    val currentMonthly: Int = 0,
    val currentPurge: Int = 0,
    val missedPurges: Int = 0,
    val lastActiveDate: Long = 0L // Timestamp of midnight of last solved question
)

@Entity(tableName = "app_lock_configs")
data class AppLockConfig(
    @PrimaryKey val packageName: String,
    val appName: String,
    val lockedUntil: Long // Timestamp in milliseconds
)

@Entity(tableName = "dns_block_configs")
data class DnsBlockConfig(
    @PrimaryKey val domainName: String,
    val isBlocked: Boolean = true
)

@Entity(tableName = "llm_settings")
data class LlmSettings(
    @PrimaryKey val id: Int = 1,
    val provider: String = "GEMINI", // GEMINI, OPENAI, ANTHROPIC
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelName: String = "gemini-1.5-flash",
    val customSystemPrompt: String = "You are an expert computer science tutor.\nGenerate exactly 10 transformations of the user's question.\nEach transformation must test the same underlying concept and require the application of the same algorithmic or theoretical principle, but be set in a different scenario or context.\nReturn your response in JSON format. The JSON must contain a single key \"transformations\" which is an array of 10 strings, representing the 10 transformations. Do not include markdown formatting or extra text.",
    val customUserPrompt: String = "Transform the following question:\n{question}"
)

@Entity(tableName = "daily_tasks")
data class DailyTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val completed: Boolean,
    val dateStr: String // Format: "yyyy-MM-dd"
)

@Entity(tableName = "test_results")
data class TestResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "PURGE", "WEEKLY", "MONTHLY"
    val score: Int,
    val timestamp: Long
)

@Entity(tableName = "panic_logs")
data class PanicLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val durationMinutes: Int
)

@Entity(tableName = "panic_config")
data class PanicConfig(
    @PrimaryKey val id: Int = 1,
    val isActive: Boolean = false,
    val startTimeMillis: Long = 0L,
    val expiresAt: Long = 0L,
    val durationMinutes: Int = 0,
    val isScheduled: Boolean = false,
    val scheduledStartHour: Int = -1,
    val scheduledStartMinute: Int = -1,
    val scheduledEndHour: Int = -1,
    val scheduledEndMinute: Int = -1,
    val isScheduleEnabled: Boolean = false,
    val allowedPackagesJson: String = "[]", // JSON array of package names
    val originalAppBlockPackage: String? = null
)

