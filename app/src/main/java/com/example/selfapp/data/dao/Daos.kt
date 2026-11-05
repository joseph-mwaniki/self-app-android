package com.example.selfapp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.selfapp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY title ASC")
    fun getAllCourses(): Flow<List<Course>>

    @Query("SELECT * FROM courses")
    suspend fun getAllCoursesList(): List<Course>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: Course): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<Course>): List<Long>
}

@Dao
interface TopicDao {
    @Query("SELECT * FROM topics WHERE courseId = :courseId")
    fun getTopicsForCourse(courseId: Long): Flow<List<Topic>>

    @Query("SELECT * FROM topics WHERE courseId = :courseId")
    suspend fun getTopicsForCourseList(courseId: Long): List<Topic>

    @Query("SELECT * FROM topics")
    fun getAllTopicsFlow(): Flow<List<Topic>>

    @Query("SELECT * FROM topics")
    suspend fun getAllTopicsList(): List<Topic>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: Topic): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<Topic>): List<Long>
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE topicId = :topicId")
    fun getQuestionsForTopic(topicId: Long): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestionById(id: Long): Question?

    @Query("SELECT * FROM questions")
    suspend fun getAllQuestions(): List<Question>

    @Query("SELECT * FROM questions")
    fun getAllQuestionsFlow(): Flow<List<Question>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: Question): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<Question>): List<Long>

    @Delete
    suspend fun deleteQuestion(question: Question)
}

@Dao
interface TransformationDao {
    @Query("SELECT * FROM transformations WHERE questionId = :questionId")
    fun getTransformationsForQuestion(questionId: Long): Flow<List<Transformation>>

    @Query("SELECT * FROM transformations WHERE questionId = :questionId")
    suspend fun getTransformationsForQuestionList(questionId: Long): List<Transformation>

    @Query("SELECT * FROM transformations")
    suspend fun getAllTransformations(): List<Transformation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransformation(transformation: Transformation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransformations(transformations: List<Transformation>)

    @Delete
    suspend fun deleteTransformation(transformation: Transformation)
}

@Dao
interface SubmissionDao {
    @Query("SELECT * FROM submissions WHERE questionId = :questionId ORDER BY submittedAt DESC")
    fun getSubmissionsForQuestion(questionId: Long): Flow<List<Submission>>

    @Query("SELECT * FROM submissions ORDER BY submittedAt DESC")
    fun getAllSubmissionsFlow(): Flow<List<Submission>>

    @Query("SELECT * FROM submissions")
    suspend fun getAllSubmissions(): List<Submission>

    @Query("SELECT * FROM submissions WHERE score < 70")
    suspend fun getFailedSubmissions(): List<Submission>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubmission(submission: Submission): Long
}

@Dao
interface PurgeTestDao {
    @Query("SELECT * FROM purge_tests ORDER BY generatedDate DESC")
    fun getPurgeTestsFlow(): Flow<List<PurgeTest>>

    @Query("SELECT * FROM purge_tests WHERE completed = 0 AND expiresDate > :currentTime ORDER BY generatedDate DESC LIMIT 1")
    suspend fun getActivePurgeTest(currentTime: Long): PurgeTest?

    @Query("SELECT * FROM purge_tests WHERE completed = 0 AND expiresDate <= :currentTime")
    suspend fun getExpiredUncompletedPurges(currentTime: Long): List<PurgeTest>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurgeTest(purgeTest: PurgeTest): Long

    @Update
    suspend fun updatePurgeTest(purgeTest: PurgeTest)
}

@Dao
interface StreakDao {
    @Query("SELECT * FROM streaks WHERE id = 1")
    fun getStreakFlow(): Flow<Streak?>

    @Query("SELECT * FROM streaks WHERE id = 1")
    suspend fun getStreak(): Streak?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreak(streak: Streak)
}

@Dao
interface AppLockConfigDao {
    @Query("SELECT * FROM app_lock_configs")
    fun getAllLockConfigsFlow(): Flow<List<AppLockConfig>>

    @Query("SELECT * FROM app_lock_configs")
    suspend fun getAllLockConfigsList(): List<AppLockConfig>

    @Query("SELECT * FROM app_lock_configs WHERE packageName = :packageName LIMIT 1")
    suspend fun getLockConfigForPackage(packageName: String): AppLockConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLockConfig(config: AppLockConfig)

    @Delete
    suspend fun deleteLockConfig(config: AppLockConfig)
}

@Dao
interface DnsBlockConfigDao {
    @Query("SELECT * FROM dns_block_configs")
    fun getAllBlockedDomainsFlow(): Flow<List<DnsBlockConfig>>

    @Query("SELECT * FROM dns_block_configs WHERE isBlocked = 1")
    suspend fun getBlockedDomainsList(): List<DnsBlockConfig>

    @Query("SELECT * FROM dns_block_configs WHERE domainName = :domainName LIMIT 1")
    suspend fun getDnsConfigForDomain(domainName: String): DnsBlockConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDnsConfig(config: DnsBlockConfig)

    @Delete
    suspend fun deleteDnsConfig(config: DnsBlockConfig)
}

@Dao
interface LlmSettingsDao {
    @Query("SELECT * FROM llm_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<LlmSettings?>

    @Query("SELECT * FROM llm_settings WHERE id = 1")
    suspend fun getSettings(): LlmSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: LlmSettings)
}

@Dao
interface DailyTaskDao {
    @Query("SELECT * FROM daily_tasks WHERE dateStr = :dateStr")
    fun getDailyTasksFlow(dateStr: String): Flow<List<DailyTask>>

    @Query("SELECT * FROM daily_tasks WHERE dateStr = :dateStr")
    suspend fun getDailyTasks(dateStr: String): List<DailyTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyTask(task: DailyTask): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyTasks(tasks: List<DailyTask>)

    @Update
    suspend fun updateDailyTask(task: DailyTask)
}

@Dao
interface TestResultDao {
    @Query("SELECT * FROM test_results ORDER BY timestamp DESC")
    fun getAllTestResultsFlow(): Flow<List<TestResult>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTestResult(result: TestResult): Long
}

@Dao
interface PanicLogDao {
    @Query("SELECT * FROM panic_logs ORDER BY timestamp DESC")
    fun getAllPanicLogsFlow(): Flow<List<PanicLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPanicLog(log: PanicLog): Long
}

@Dao
interface PanicConfigDao {
    @Query("SELECT * FROM panic_config WHERE id = 1")
    fun getPanicConfigFlow(): Flow<PanicConfig?>

    @Query("SELECT * FROM panic_config WHERE id = 1")
    suspend fun getPanicConfig(): PanicConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPanicConfig(config: PanicConfig)
}

