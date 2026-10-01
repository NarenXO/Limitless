package com.teamdexters.limitless.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SafetyModule {
    // Dependencies are provided by other modules:
    // - FusedLocationProviderClient by LocationModule
    // - MappedRoomDao by DatabaseModule
}
