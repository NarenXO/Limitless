package com.teamdexters.limitless.di

import android.content.Context
import com.teamdexters.limitless.data.local.LimitlessDatabase
import com.teamdexters.limitless.data.local.dao.AccessibilityScoreDao
import com.teamdexters.limitless.data.local.dao.UserReportDao
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
    fun provideDatabase(@ApplicationContext context: Context): LimitlessDatabase {
        return LimitlessDatabase.getDatabase(context)
    }

    @Provides
    fun provideUserReportDao(database: LimitlessDatabase): UserReportDao {
        return database.userReportDao()
    }

    @Provides
    fun provideAccessibilityScoreDao(database: LimitlessDatabase): AccessibilityScoreDao {
        return database.accessibilityScoreDao()
    }
}
