package com.teamdexters.limitless.di

import android.content.Context
import com.teamdexters.limitless.routing.AStarAccessibleRouter
import com.teamdexters.limitless.routing.VoiceNavigator
import com.teamdexters.limitless.routing.CurrentLocationTracker
import com.teamdexters.limitless.data.local.dao.MappedRoomDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoutingModule {

    @Provides
    @Singleton
    fun provideCurrentLocationTracker(
        @ApplicationContext context: Context,
        mappedRoomDao: MappedRoomDao
    ): CurrentLocationTracker {
        return CurrentLocationTracker(context, mappedRoomDao)
    }

    @Provides
    @Singleton
    fun provideVoiceNavigator(@ApplicationContext context: Context): VoiceNavigator {
        return VoiceNavigator(context)
    }

}
