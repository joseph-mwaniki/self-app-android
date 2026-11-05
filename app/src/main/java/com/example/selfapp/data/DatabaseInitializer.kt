package com.example.selfapp.data

import com.example.selfapp.data.dao.*
import com.example.selfapp.data.entity.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseInitializer @Inject constructor(
    private val courseDao: CourseDao,
    private val topicDao: TopicDao,
    private val questionDao: QuestionDao,
    private val streakDao: StreakDao,
    private val llmSettingsDao: LlmSettingsDao
) {
    suspend fun initialize() {
        // 1. Initialize Streaks if not present
        if (streakDao.getStreak() == null) {
            streakDao.insertStreak(Streak(id = 1))
        }

        // 2. Initialize LLM Settings if not present
        if (llmSettingsDao.getSettings() == null) {
            llmSettingsDao.insertSettings(LlmSettings(id = 1))
        }
    }
}
