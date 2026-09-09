package com.ferhat.commutetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<LocationEntity>>

    @Insert
    suspend fun insert(location: LocationEntity): Long

    @Update
    suspend fun update(location: LocationEntity)

    @Delete
    suspend fun delete(location: LocationEntity)

    @Query("SELECT COUNT(*) FROM locations")
    suspend fun count(): Int
}

@Dao
interface CommuteDao {
    @Query("SELECT * FROM commutes ORDER BY startEpochMillis DESC")
    fun observeAll(): Flow<List<CommuteEntity>>

    @Query("SELECT * FROM commutes WHERE endEpochMillis IS NULL ORDER BY startEpochMillis DESC LIMIT 1")
    fun observeActive(): Flow<CommuteEntity?>

    @Query("SELECT * FROM commutes WHERE endEpochMillis IS NULL ORDER BY startEpochMillis DESC LIMIT 1")
    suspend fun getActive(): CommuteEntity?

    @Insert
    suspend fun insert(commute: CommuteEntity): Long

    @Update
    suspend fun update(commute: CommuteEntity)

    @Delete
    suspend fun delete(commute: CommuteEntity)
}
