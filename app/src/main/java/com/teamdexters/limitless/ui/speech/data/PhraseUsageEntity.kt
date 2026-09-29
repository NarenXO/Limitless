package com.teamdexters.limitless.ui.speech.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity tracking local phrase usage for on-device phrase prediction.
 *
 * The primary key is a composite string "languageCode:phraseText" so that
 * Tamil and Hindi usage records are completely independent from English ones.
 *
 * @param id                 Composite key: "$languageCode:$phraseText".
 * @param phraseText         The phrase text string.
 * @param languageCode       ISO language code ("en", "ta", "hi").
 * @param usageCount         Total number of times this phrase has been selected.
 * @param lastUsedTimestamp  Epoch timestamp (millis) of the last usage.
 * @param timeOfDaySlot      Coarse time slot when last used ("morning", "afternoon", "evening", "night").
 * @param lastLocationTag    Coarse location tag when last used ("home", "hospital", "transit", "unknown").
 */
@Entity(tableName = "phrase_usage")
data class PhraseUsageEntity(
    @PrimaryKey val id: String,           // "$languageCode:$phraseText"
    val phraseText: String,
    val languageCode: String = "en",
    val usageCount: Int = 0,
    val lastUsedTimestamp: Long = 0L,
    val timeOfDaySlot: String = "morning",
    val lastLocationTag: String = "unknown"
)
