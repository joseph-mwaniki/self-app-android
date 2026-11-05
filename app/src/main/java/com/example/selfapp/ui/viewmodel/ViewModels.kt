package com.example.selfapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selfapp.data.dao.*
import com.example.selfapp.data.entity.*
import com.example.selfapp.data.llm.LlmClient
import com.example.selfapp.data.storage.ScopedStorageManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.selfapp.service.AppNotification
import com.example.selfapp.service.AppNotificationListenerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

// --- DASHBOARD VIEWMODEL ---
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val courseDao: CourseDao,
    private val topicDao: TopicDao,
    private val questionDao: QuestionDao,
    private val submissionDao: SubmissionDao,
    private val streakDao: StreakDao,
    private val dailyTaskDao: DailyTaskDao,
    private val testResultDao: TestResultDao
) : ViewModel() {

    data class CourseProgress(
        val course: Course,
        val solvedCount: Int,
        val totalCount: Int,
        val percentage: Float
    )

    val streakState: StateFlow<Streak?> = streakDao.getStreakFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val courseProgressList: StateFlow<List<CourseProgress>> = flow {
        combine(
            courseDao.getAllCourses(),
            questionDao.getAllQuestionsFlow(),
            submissionDao.getAllSubmissionsFlow()
        ) { courses, questions, submissions ->
            val approvedQuestionIds = submissions
                .filter { it.status == "APPROVED" }
                .map { it.questionId }
                .toSet()

            val topics = topicDao.getAllTopicsList()

            courses.map { course ->
                val courseTopicIds = topics.filter { it.courseId == course.id }.map { it.id }.toSet()
                val courseQuestions = questions.filter { courseTopicIds.contains(it.topicId) }
                val total = courseQuestions.size
                val solved = courseQuestions.count { approvedQuestionIds.contains(it.id) }
                val pct = if (total > 0) (solved.toFloat() / total.toFloat()) else 0f
                CourseProgress(course, solved, total, pct)
            }
        }.collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalPoints: StateFlow<Int> = flow {
        combine(
            questionDao.getAllQuestionsFlow(),
            submissionDao.getAllSubmissionsFlow()
        ) { questions, submissions ->
            val approvedIds = submissions.filter { it.status == "APPROVED" }.map { it.questionId }.toSet()
            questions.filter { approvedIds.contains(it.id) }.sumOf { it.points }
        }.collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    
    val dailyTasks: StateFlow<List<DailyTask>> = dailyTaskDao.getDailyTasksFlow(todayStr)
        .onStart { 
            // Populate defaults if empty
            val tasks = dailyTaskDao.getDailyTasks(todayStr)
            if (tasks.isEmpty()) {
                dailyTaskDao.insertDailyTasks(listOf(
                    DailyTask(title = "Resolve 3 LeetCode-style questions", completed = false, dateStr = todayStr),
                    DailyTask(title = "Review Weekly Test variations", completed = false, dateStr = todayStr),
                    DailyTask(title = "Maintain 24h Digital Fast", completed = false, dateStr = todayStr)
                ))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleTask(task: DailyTask) {
        viewModelScope.launch {
            dailyTaskDao.updateDailyTask(task.copy(completed = !task.completed))
        }
    }

    val testHistory: StateFlow<List<TestResult>> = testResultDao.getAllTestResultsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getActivityCalendar(): StateFlow<List<Int>> = submissionDao.getAllSubmissionsFlow().map { submissions ->
        val calendar = Calendar.getInstance()
        val levels = mutableListOf<Int>()
        for (i in 0 until 30) {
            val dateStart = calendar.apply { 
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val dateEnd = dateStart + 24 * 60 * 60 * 1000
            val count = submissions.count { it.submittedAt in dateStart until dateEnd }
            levels.add(when {
                count == 0 -> 0
                count < 2 -> 1
                count < 4 -> 2
                count < 6 -> 3
                else -> 4
            })
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        levels.reversed()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(30) { 0 })
}

// --- COURSE & QUESTION VIEWMODEL ---
@HiltViewModel
class CourseViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseDao: CourseDao,
    private val topicDao: TopicDao,
    private val questionDao: QuestionDao,
    private val transformationDao: TransformationDao,
    private val submissionDao: SubmissionDao,
    private val streakDao: StreakDao,
    private val llmClient: LlmClient,
    private val storageManager: ScopedStorageManager
) : ViewModel() {

    val courses: StateFlow<List<Course>> = courseDao.getAllCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var showAddCourseDialog by mutableStateOf(false)
    var showAddTopicDialog by mutableStateOf(false)
    var showAddQuestionDialog by mutableStateOf(false)

    fun addCourse(title: String, description: String) {
        viewModelScope.launch {
            courseDao.insertCourse(Course(title = title, description = description))
        }
    }

    fun addTopic(title: String) {
        val courseId = _selectedCourseId.value ?: return
        viewModelScope.launch {
            topicDao.insertTopic(Topic(courseId = courseId, title = title))
        }
    }

    fun addQuestion(questionText: String, difficulty: String, points: Int) {
        val topicId = _selectedTopicId.value ?: return
        viewModelScope.launch {
            questionDao.insertQuestion(
                Question(
                    topicId = topicId,
                    questionText = questionText,
                    difficulty = difficulty,
                    points = points
                )
            )
        }
    }

    fun deleteQuestion(question: Question) {
        viewModelScope.launch {
            questionDao.deleteQuestion(question)
            if (_selectedQuestion.value?.id == question.id) {
                _selectedQuestion.value = null
            }
        }
    }

    fun deleteTransformation(transformation: Transformation) {
        viewModelScope.launch {
            transformationDao.deleteTransformation(transformation)
        }
    }

    private val _selectedCourseId = MutableStateFlow<Long?>(null)
    val selectedCourseId = _selectedCourseId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val topics: StateFlow<List<Topic>> = _selectedCourseId.flatMapLatest { courseId ->
        if (courseId == null) flowOf(emptyList())
        else topicDao.getTopicsForCourse(courseId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTopicId = MutableStateFlow<Long?>(null)
    val selectedTopicId = _selectedTopicId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val questions: StateFlow<List<Question>> = _selectedTopicId.flatMapLatest { topicId ->
        if (topicId == null) flowOf(emptyList())
        else questionDao.getQuestionsForTopic(topicId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedQuestion = MutableStateFlow<Question?>(null)
    val selectedQuestion = _selectedQuestion.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val transformations: StateFlow<List<Transformation>> = _selectedQuestion.flatMapLatest { question ->
        if (question == null) flowOf(emptyList())
        else transformationDao.getTransformationsForQuestion(question.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val submissions: StateFlow<List<Submission>> = _selectedQuestion.flatMapLatest { question ->
        if (question == null) flowOf(emptyList())
        else submissionDao.getSubmissionsForQuestion(question.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _isGrading = MutableStateFlow(false)
    val isGrading = _isGrading.asStateFlow()

    fun selectCourse(courseId: Long?) {
        _selectedCourseId.value = courseId
        _selectedTopicId.value = null
        _selectedQuestion.value = null
    }

    fun selectTopic(topicId: Long?) {
        _selectedTopicId.value = topicId
        _selectedQuestion.value = null
    }

    fun selectQuestion(question: Question?) {
        _selectedQuestion.value = question
    }

    fun generateTransformationsForSelected() {
        val q = _selectedQuestion.value ?: return
        viewModelScope.launch {
            _isGenerating.value = true
            val results = llmClient.generateTransformations(q.questionText)
            val entities = results.map { Transformation(questionId = q.id, transformedQuestion = it) }
            transformationDao.insertTransformations(entities)
            _isGenerating.value = false
        }
    }

    fun submitQuestionSolution(reasoningUri: Uri?, solutionUris: List<Uri>, isDirectory: Boolean) {
        val q = _selectedQuestion.value ?: return
        viewModelScope.launch {
            _isGrading.value = true
            val timestamp = System.currentTimeMillis()

            // 1. Scoped storage copy
            var reasoningPath = ""
            if (reasoningUri != null) {
                reasoningPath = storageManager.copyFileToLocal(reasoningUri, q.id, timestamp, "reasoning.txt")
            }

            val solutionPaths = mutableListOf<String>()
            if (isDirectory && solutionUris.isNotEmpty()) {
                val paths = storageManager.copyDirectoryToLocal(solutionUris.first(), q.id, timestamp)
                solutionPaths.addAll(paths)
            } else {
                for ((idx, uri) in solutionUris.withIndex()) {
                    val path = storageManager.copyFileToLocal(uri, q.id, timestamp, "solution_$idx.txt")
                    solutionPaths.add(path)
                }
            }

            // 2. Read contents to grade
            val reasoningText = if (reasoningPath.isNotEmpty()) storageManager.readFileContent(reasoningPath) else "No reasoning document uploaded."
            val codeBuilder = StringBuilder()
            for (path in solutionPaths) {
                val file = File(path)
                codeBuilder.append("--- File: ${file.name} ---\n")
                codeBuilder.append(storageManager.readFileContent(path))
                codeBuilder.append("\n\n")
            }

            // 3. Grade using LLM
            val gradeResult = llmClient.gradeSubmission(q.questionText, reasoningText, codeBuilder.toString())

            // 4. Save Submission
            val submission = Submission(
                questionId = q.id,
                reasoningFilePath = reasoningPath,
                solutionFilePathsJson = Gson().toJson(solutionPaths),
                score = gradeResult.score,
                status = if (gradeResult.score >= 70) "APPROVED" else "FAILED",
                feedback = gradeResult.feedback,
                submittedAt = timestamp
            )
            submissionDao.insertSubmission(submission)

            // 5. Update Daily Streak on approval
            if (gradeResult.score >= 70) {
                updateStreakAfterSolve()
            }

            _isGrading.value = false
        }
    }

    private suspend fun updateStreakAfterSolve() {
        val streak = streakDao.getStreak() ?: Streak(id = 1)
        val today = getMidnightTimestamp(System.currentTimeMillis())

        if (streak.lastActiveDate == today) {
            // Already solved something today
            return
        }

        val yesterday = getMidnightTimestamp(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        val newDaily = if (streak.lastActiveDate == yesterday) {
            streak.currentDaily + 1
        } else {
            1
        }
        val newLongest = maxOf(newDaily, streak.longestDaily)

        streakDao.insertStreak(
            streak.copy(
                currentDaily = newDaily,
                longestDaily = newLongest,
                lastActiveDate = today
            )
        )
    }

    private fun getMidnightTimestamp(time: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

// --- PURGE VIEWMODEL ---
@HiltViewModel
class PurgeViewModel @Inject constructor(
    private val purgeTestDao: PurgeTestDao,
    private val questionDao: QuestionDao,
    private val streakDao: StreakDao,
    private val llmClient: LlmClient,
    private val testResultDao: TestResultDao
) : ViewModel() {

    val purgeTests: StateFlow<List<PurgeTest>> = purgeTestDao.getPurgeTestsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePurgeTest: StateFlow<PurgeTest?> = flow {
        while (true) {
            emit(purgeTestDao.getActivePurgeTest(System.currentTimeMillis()))
            kotlinx.coroutines.delay(10000) // Poll active purge state
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isGrading = MutableStateFlow(false)
    val isGrading = _isGrading.asStateFlow()

    fun startPurge(purgeId: Long) {
        viewModelScope.launch {
            val tests = purgeTests.value
            val match = tests.find { it.id == purgeId } ?: return@launch
            if (!match.started) {
                purgeTestDao.updatePurgeTest(
                    match.copy(
                        started = true,
                        startedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun autoSubmitPurge(purgeId: Long) {
        viewModelScope.launch {
            val tests = purgeTests.value
            val match = tests.find { it.id == purgeId } ?: return@launch
            
            if (!match.completed) {
                val finishedTest = match.copy(
                    completed = true,
                    score = 0,
                    started = false
                )
                purgeTestDao.updatePurgeTest(finishedTest)
                
                // Log missed purge in TestResult table
                testResultDao.insertTestResult(
                    TestResult(
                        type = "PURGE",
                        score = 0,
                        timestamp = System.currentTimeMillis()
                    )
                )

                // Reset streaks
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
        }
    }

    fun submitPurgeTest(purgeId: Long, answers: List<String>) {
        viewModelScope.launch {
            _isGrading.value = true
            val tests = purgeTests.value
            val match = tests.find { it.id == purgeId } ?: return@launch

            val itemRefs: List<String> = Gson().fromJson(match.questionsJson, object : TypeToken<List<String>>() {}.type)
            val questionDetailsList = mutableListOf<String>()

            for ((idx, ref) in itemRefs.withIndex()) {
                val parts = ref.split("_")
                val text = if (parts[0] == "q") {
                    questionDao.getQuestionById(parts[1].toLong())?.questionText ?: "Question Details"
                } else {
                    "Transformation Variation of Question ID: ${parts[1]}"
                }
                questionDetailsList.add("Question ${idx + 1}: $text\nAnswer: ${answers.getOrNull(idx) ?: "No Answer"}")
            }

            val submissionsStr = questionDetailsList.joinToString("\n\n")
            val gradeResult = llmClient.gradeSubmission(
                questionText = "Consolidated Weekly 10-Question Purge Test.",
                reasoningText = "Answers submitted in-app.",
                solutionFilesText = submissionsStr
            )

            val finishedTest = match.copy(
                completed = true,
                score = gradeResult.score,
                answersJson = Gson().toJson(answers)
            )
            purgeTestDao.updatePurgeTest(finishedTest)

            // Log in test results table
            testResultDao.insertTestResult(
                TestResult(
                    type = "PURGE",
                    score = gradeResult.score,
                    timestamp = System.currentTimeMillis()
                )
            )

            if (gradeResult.score >= 70) {
                val streak = streakDao.getStreak() ?: Streak(id = 1)
                streakDao.insertStreak(
                    streak.copy(
                        currentPurge = streak.currentPurge + 1,
                        currentDaily = streak.currentDaily + 1,
                        longestDaily = maxOf(streak.currentDaily + 1, streak.longestDaily)
                    )
                )
            }

            _isGrading.value = false
        }
    }
}

// --- WEEKLY & MONTHLY TESTS VIEWMODEL ---
@HiltViewModel
class TestViewModel @Inject constructor(
    private val questionDao: QuestionDao,
    private val transformationDao: TransformationDao,
    private val submissionDao: SubmissionDao,
    private val streakDao: StreakDao,
    private val llmClient: LlmClient
) : ViewModel() {

    private val _activeQuiz = MutableStateFlow<QuizSession?>(null)
    val activeQuiz = _activeQuiz.asStateFlow()

    private val _isGrading = MutableStateFlow(false)
    val isGrading = _isGrading.asStateFlow()

    data class QuizSession(
        val isMonthly: Boolean,
        val questions: List<QuizQuestion>,
        val answers: MutableList<String>
    )

    data class QuizQuestion(
        val refId: String,
        val text: String,
        val points: Int
    )

    fun startTest(isMonthly: Boolean) {
        viewModelScope.launch {
            val questions = questionDao.getAllQuestions()
            if (questions.isEmpty()) return@launch

            val failedIds = submissionDao.getFailedSubmissions().map { it.questionId }.toSet()
            val transformations = transformationDao.getAllTransformations()

            val pool = mutableListOf<QuizQuestion>()
            
            // Generate pool entries
            for (q in questions) {
                val pointsMultiplier = if (isMonthly) 2 else 1
                if (failedIds.contains(q.id)) {
                    pool.add(QuizQuestion("q_${q.id}", q.questionText, q.points * pointsMultiplier))
                }
                val qTransformations = transformations.filter { it.questionId == q.id }
                for (t in qTransformations) {
                    pool.add(QuizQuestion("t_${t.id}", t.transformedQuestion, q.points * pointsMultiplier))
                }
            }

            // Fallback: Add all questions to pool if not enough variations
            if (pool.size < 10) {
                for (q in questions) {
                    pool.add(QuizQuestion("q_${q.id}", q.questionText, q.points * (if (isMonthly) 2 else 1)))
                }
            }

            val selection = pool.distinctBy { it.text }.shuffled().take(10)
            _activeQuiz.value = QuizSession(
                isMonthly = isMonthly,
                questions = selection,
                answers = MutableList(selection.size) { "" }
            )
        }
    }

    fun submitAnswer(index: Int, text: String) {
        _activeQuiz.value?.answers?.set(index, text)
    }

    fun completeQuiz(onResult: (Int, String) -> Unit) {
        val session = _activeQuiz.value ?: return
        viewModelScope.launch {
            _isGrading.value = true

            val promptBuilder = StringBuilder()
            for ((idx, q) in session.questions.withIndex()) {
                promptBuilder.append("Question ${idx + 1}: ${q.text}\nAnswer: ${session.answers[idx]}\n\n")
            }

            val gradeResult = llmClient.gradeSubmission(
                questionText = if (session.isMonthly) "Monthly Test (Harder Mode)" else "Weekly Test (Standard)",
                reasoningText = "Answers submitted in-app.",
                solutionFilesText = promptBuilder.toString()
            )

            // Update streaks
            val streak = streakDao.getStreak() ?: Streak(id = 1)
            if (gradeResult.score >= 70) {
                val updatedStreak = if (session.isMonthly) {
                    streak.copy(
                        currentMonthly = streak.currentMonthly + 1,
                        currentDaily = streak.currentDaily + 1,
                        longestDaily = maxOf(streak.currentDaily + 1, streak.longestDaily)
                    )
                } else {
                    streak.copy(
                        currentWeekly = streak.currentWeekly + 1,
                        currentDaily = streak.currentDaily + 1,
                        longestDaily = maxOf(streak.currentDaily + 1, streak.longestDaily)
                    )
                }
                streakDao.insertStreak(updatedStreak)
            }

            _activeQuiz.value = null
            _isGrading.value = false
            onResult(gradeResult.score, gradeResult.feedback)
        }
    }
}

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable? = null
)

// --- APP LOCK VIEWMODEL ---
@HiltViewModel
class AppLockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLockConfigDao: AppLockConfigDao
) : ViewModel() {

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            val appList = apps.filter { 
                (pm.getLaunchIntentForPackage(it.packageName) != null) && (it.packageName != context.packageName)
            }.map { 
                AppInfo(
                    name = it.loadLabel(pm).toString(),
                    packageName = it.packageName,
                    icon = it.loadIcon(pm)
                )
            }.sortedBy { it.name }
            _installedApps.value = appList
        }
    }

    val lockedApps: StateFlow<List<AppLockConfig>> = appLockConfigDao.getAllLockConfigsFlow()
        .onEach { configs ->
            if (com.example.selfapp.util.PanicManager.isDeviceOwner(context)) {
                com.example.selfapp.util.PanicManager.syncAppLockWithDeviceOwner(context, configs)
                com.example.selfapp.util.PanicManager.enforceSpinBrowserOnly(context)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun lockApp(packageName: String, appName: String, expiryTime: Long) {
        val trimmedPkg = packageName.trim()
        viewModelScope.launch {
            appLockConfigDao.insertLockConfig(
                AppLockConfig(
                    packageName = trimmedPkg,
                    appName = appName.trim(),
                    lockedUntil = expiryTime
                )
            )
            if (com.example.selfapp.util.PanicManager.isDeviceOwner(context)) {
                com.example.selfapp.util.PanicManager.setPackageSuspended(context, trimmedPkg, true)
            }
        }
    }

    // No delete permitted if not expired (strict rule enforcement!)
    fun unlockApp(config: AppLockConfig) {
        if (System.currentTimeMillis() >= config.lockedUntil) {
            viewModelScope.launch {
                appLockConfigDao.deleteLockConfig(config)
                if (com.example.selfapp.util.PanicManager.isDeviceOwner(context)) {
                    com.example.selfapp.util.PanicManager.setPackageSuspended(context, config.packageName, false)
                }
            }
        }
    }
}

// --- PANIC VIEWMODEL ---
@HiltViewModel
class PanicViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val panicLogDao: PanicLogDao,
    private val panicConfigDao: PanicConfigDao
) : ViewModel() {

    val panicLogs: StateFlow<List<PanicLog>> = panicLogDao.getAllPanicLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val panicConfig: StateFlow<PanicConfig?> = panicConfigDao.getPanicConfigFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeNotifications: StateFlow<List<AppNotification>> = AppNotificationListenerService.activeNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

    private val _isDeviceOwner = MutableStateFlow(false)
    val isDeviceOwner = _isDeviceOwner.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled = _isAccessibilityEnabled.asStateFlow()

    init {
        loadInstalledApps()
        refreshStatus()
    }

    fun refreshStatus() {
        _isDeviceOwner.value = com.example.selfapp.util.PanicManager.isDeviceOwner(context)
        _isAccessibilityEnabled.value = com.example.selfapp.util.PanicManager.isAccessibilityEnabled(context)
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            val appList = apps.filter { 
                (pm.getLaunchIntentForPackage(it.packageName) != null) && (it.packageName != context.packageName)
            }.map { 
                AppInfo(
                    name = it.loadLabel(pm).toString(),
                    packageName = it.packageName,
                    icon = it.loadIcon(pm)
                )
            }.sortedBy { it.name }
            _installedApps.value = appList
        }
    }

    fun triggerPanic(minutes: Int, allowedPackages: List<String>) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val expiry = startTime + (minutes * 60L * 1000L)
            panicLogDao.insertPanicLog(
                PanicLog(
                    timestamp = startTime,
                    durationMinutes = minutes
                )
            )
            val current = panicConfig.value ?: PanicConfig()
            val newConfig = current.copy(
                isActive = true,
                startTimeMillis = startTime,
                durationMinutes = minutes,
                expiresAt = expiry,
                allowedPackagesJson = Gson().toJson(allowedPackages)
            )
            panicConfigDao.insertPanicConfig(newConfig)

            if (com.example.selfapp.util.PanicManager.isDeviceOwner(context)) {
                com.example.selfapp.util.PanicManager.configureLockTaskPackages(context, allowedPackages)
            }
            com.example.selfapp.util.PanicManager.scheduleAlarms(context, newConfig)
        }
    }

    fun savePanicSchedule(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        enabled: Boolean,
        allowedPackages: List<String>
    ) {
        viewModelScope.launch {
            val current = panicConfig.value ?: PanicConfig()
            
            val cal = java.util.Calendar.getInstance()
            val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val currentMinute = cal.get(java.util.Calendar.MINUTE)
            val currentMins = currentHour * 60 + currentMinute
            val startMins = startHour * 60 + startMinute
            val endMins = endHour * 60 + endMinute

            var isActiveNow = false
            var expiresAt = 0L

            if (enabled) {
                if (startMins < endMins) {
                    if (currentMins in startMins until endMins) {
                        isActiveNow = true
                        val endCal = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, endHour)
                            set(java.util.Calendar.MINUTE, endMinute)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        expiresAt = endCal.timeInMillis
                    }
                } else if (startMins > endMins) {
                    if (currentMins >= startMins || currentMins < endMins) {
                        isActiveNow = true
                        val endCal = java.util.Calendar.getInstance().apply {
                            if (currentMins >= startMins) add(java.util.Calendar.DAY_OF_YEAR, 1)
                            set(java.util.Calendar.HOUR_OF_DAY, endHour)
                            set(java.util.Calendar.MINUTE, endMinute)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        expiresAt = endCal.timeInMillis
                    }
                }
            }

            val updatedConfig = current.copy(
                isScheduleEnabled = enabled,
                isScheduled = enabled,
                scheduledStartHour = startHour,
                scheduledStartMinute = startMinute,
                scheduledEndHour = endHour,
                scheduledEndMinute = endMinute,
                isActive = if (enabled && isActiveNow) true else if (!enabled && current.isActive && current.isScheduled) false else current.isActive,
                expiresAt = if (enabled && isActiveNow) expiresAt else current.expiresAt,
                allowedPackagesJson = Gson().toJson(allowedPackages)
            )

            panicConfigDao.insertPanicConfig(updatedConfig)
            com.example.selfapp.util.PanicManager.scheduleAlarms(context, updatedConfig)
            if (updatedConfig.isActive && com.example.selfapp.util.PanicManager.isDeviceOwner(context)) {
                com.example.selfapp.util.PanicManager.configureLockTaskPackages(context, allowedPackages)
            }
        }
    }

    fun deactivatePanic() {
        viewModelScope.launch {
            val current = panicConfig.value ?: PanicConfig()
            val updated = current.copy(isActive = false, expiresAt = 0L)
            panicConfigDao.insertPanicConfig(updated)
            com.example.selfapp.util.PanicManager.scheduleAlarms(context, updated)
        }
    }
}

// --- DNS BLOCK VIEWMODEL ---
@HiltViewModel
class DnsBlockViewModel @Inject constructor(
    private val dnsBlockConfigDao: DnsBlockConfigDao
) : ViewModel() {

    val blockedDomains: StateFlow<List<DnsBlockConfig>> = dnsBlockConfigDao.getAllBlockedDomainsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addDomain(domain: String) {
        val cleanDomain = domain.lowercase().trim()
        if (cleanDomain.isNotEmpty()) {
            viewModelScope.launch {
                dnsBlockConfigDao.insertDnsConfig(DnsBlockConfig(domainName = cleanDomain))
            }
        }
    }

    fun removeDomain(config: DnsBlockConfig) {
        viewModelScope.launch {
            dnsBlockConfigDao.deleteDnsConfig(config)
        }
    }
}

// --- SETTINGS VIEWMODEL ---
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val llmSettingsDao: LlmSettingsDao
) : ViewModel() {

    val settings: StateFlow<LlmSettings?> = llmSettingsDao.getSettingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveSettings(newSettings: LlmSettings) {
        viewModelScope.launch {
            llmSettingsDao.insertSettings(newSettings)
        }
    }
}
