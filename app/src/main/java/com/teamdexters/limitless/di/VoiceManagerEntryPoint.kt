package com.teamdexters.limitless.di

import com.teamdexters.limitless.core.audio.VoiceManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt EntryPoint to inject [VoiceManager] into non-Hilt-injected contexts
 * such as Composable functions that cannot use @HiltViewModel.
 *
 * Usage:
 * ```kotlin
 * val voiceManager = EntryPointAccessors.fromApplication(
 *     context.applicationContext,
 *     VoiceManagerEntryPoint::class.java
 * ).voiceManager()
 * ```
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface VoiceManagerEntryPoint {
    fun voiceManager(): VoiceManager
}
