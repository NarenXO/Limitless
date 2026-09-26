package com.teamdexters.limitless.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.teamdexters.limitless.data.local.entity.PersonaPreferenceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for PersonaPreferenceEntity operations.
 * Provides CRUD operations for the singleton user persona preference.
 */
@Dao
interface PersonaPreferenceDao {

    /**
     * Sets or updates the user's persona preference.
     * Since this is a singleton table (fixed ID = 1), this will always replace the existing preference.
     *
     * @param pref The PersonaPreferenceEntity to set
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(pref: PersonaPreferenceEntity)

    /**
     * Retrieves the current user persona preference.
     * Since this is a singleton table, it always queries for ID = 1.
     *
     * @return The PersonaPreferenceEntity if found, null otherwise
     */
    @Query("SELECT * FROM persona_preferences WHERE id = 1")
    suspend fun getPreference(): PersonaPreferenceEntity?

    /**
     * Retrieves the current user persona preference as a Flow.
     * Returns a Flow that emits updated preferences whenever the database changes.
     * Since this is a singleton table, it always queries for ID = 1.
     *
     * @return Flow emitting the PersonaPreferenceEntity (can be null if not set)
     */
    @Query("SELECT * FROM persona_preferences WHERE id = 1")
    fun getPreferenceFlow(): Flow<PersonaPreferenceEntity?>

    /**
     * Clears the user's persona preference by deleting the singleton record.
     * After this, getPreference() will return null until a new preference is set.
     */
    @Query("DELETE FROM persona_preferences")
    suspend fun clearPreference()
}