package com.example.selfapp.di

import android.content.Context
import androidx.room.Room
import com.example.selfapp.data.AppDatabase
import com.example.selfapp.data.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "self_app_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideCourseDao(db: AppDatabase): CourseDao = db.courseDao()

    @Provides
    fun provideTopicDao(db: AppDatabase): TopicDao = db.topicDao()

    @Provides
    fun provideQuestionDao(db: AppDatabase): QuestionDao = db.questionDao()

    @Provides
    fun provideTransformationDao(db: AppDatabase): TransformationDao = db.transformationDao()

    @Provides
    fun provideSubmissionDao(db: AppDatabase): SubmissionDao = db.submissionDao()

    @Provides
    fun providePurgeTestDao(db: AppDatabase): PurgeTestDao = db.purgeTestDao()

    @Provides
    fun provideStreakDao(db: AppDatabase): StreakDao = db.streakDao()

    @Provides
    fun provideAppLockConfigDao(db: AppDatabase): AppLockConfigDao = db.appLockConfigDao()

    @Provides
    fun provideDnsBlockConfigDao(db: AppDatabase): DnsBlockConfigDao = db.dnsBlockConfigDao()

    @Provides
    fun provideLlmSettingsDao(db: AppDatabase): LlmSettingsDao = db.llmSettingsDao()

    @Provides
    @Singleton
    fun provideDailyTaskDao(db: AppDatabase): DailyTaskDao = db.dailyTaskDao()

    @Provides
    @Singleton
    fun provideTestResultDao(db: AppDatabase): TestResultDao = db.testResultDao()

    @Provides
    @Singleton
    fun providePanicLogDao(db: AppDatabase): PanicLogDao = db.panicLogDao()

    @Provides
    @Singleton
    fun providePanicConfigDao(db: AppDatabase): PanicConfigDao = db.panicConfigDao()
}
