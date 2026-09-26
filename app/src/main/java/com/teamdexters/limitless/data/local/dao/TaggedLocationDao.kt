package com.teamdexters.limitless.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.teamdexters.limitless.data.local.entity.TaggedLocationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for TaggedLocationEntity operations.
 * Provides CRUD operations and queries for QR/NFC/OSM tagged locations.
 */
@Dao
interface TaggedLocationDao {

    /**
     * Inserts a new tagged location into the database.
     * If a location with the same tagId exists, it will be replaced.
     *
     * @param location The TaggedLocationEntity to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: TaggedLocationEntity)

    /**
     * Retrieves a specific tagged location by its tag ID.
     * Used when scanning QR codes, NFC tags, or looking up OSM nodes.
     *
     * @param tagId The unique tag identifier (QR payload, NFC ID, or OSM node ID)
     * @return The TaggedLocationEntity if found, null otherwise
     */
    @Query("SELECT * FROM tagged_locations WHERE tagId = :tagId")
    suspend fun getLocationByTag(tagId: String): TaggedLocationEntity?

    /**
     * Retrieves all tagged locations from the database.
     * Returns a Flow that emits updated lists whenever the database changes.
     *
     * @return Flow emitting list of all TaggedLocationEntity objects
     */
    @Query("SELECT * FROM tagged_locations")
    fun getAllLocations(): Flow<List<TaggedLocationEntity>>

    /**
     * Deletes a specific tagged location from the database.
     *
     * @param location The TaggedLocationEntity to delete
     */
    @Delete
    suspend fun deleteLocation(location: TaggedLocationEntity)
}