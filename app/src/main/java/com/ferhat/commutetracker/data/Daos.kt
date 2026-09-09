package com.ferhat.commutetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<Place>>

    @Query("SELECT * FROM places ORDER BY label COLLATE NOCASE")
    suspend fun getAll(): List<Place>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getById(id: Long): Place?

    @Query("SELECT * FROM places WHERE latitude IS NOT NULL AND longitude IS NOT NULL")
    suspend fun getLocated(): List<Place>

    @Insert
    suspend fun insert(place: Place): Long

    @Update
    suspend fun update(place: Place)

    @Delete
    suspend fun delete(place: Place)

    @Query("UPDATE places SET visitCount = visitCount + 1, lastSeenAt = :at WHERE id = :id")
    suspend fun recordVisit(id: Long, at: Long)

    @Query("SELECT COUNT(*) FROM places")
    suspend fun count(): Int
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startEpochMillis DESC")
    fun observeAll(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE endEpochMillis IS NOT NULL ORDER BY startEpochMillis DESC")
    fun observeFinished(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE endEpochMillis IS NULL ORDER BY startEpochMillis DESC LIMIT 1")
    fun observeActive(): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE endEpochMillis IS NULL ORDER BY startEpochMillis DESC LIMIT 1")
    suspend fun getActive(): Trip?

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): Trip?

    @Query("SELECT * FROM trips WHERE isAuto = 1 AND startEpochMillis >= :since ORDER BY startEpochMillis")
    suspend fun getAutoTripsSince(since: Long): List<Trip>

    @Query("SELECT MAX(endEpochMillis) FROM trips WHERE isAuto = 1 AND endEpochMillis IS NOT NULL")
    suspend fun lastAutoTripEnd(): Long?

    @Query("SELECT * FROM trips WHERE endEpochMillis IS NOT NULL ORDER BY startEpochMillis")
    suspend fun getFinished(): List<Trip>

    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    @Delete
    suspend fun delete(trip: Trip)
}

@Dao
interface TripStopDao {
    @Query("SELECT * FROM trip_stops WHERE tripId = :tripId ORDER BY arrivalMillis")
    suspend fun getForTrip(tripId: Long): List<TripStop>

    @Query("SELECT * FROM trip_stops")
    fun observeAll(): Flow<List<TripStop>>

    @Insert
    suspend fun insert(stop: TripStop): Long

    @Query("DELETE FROM trip_stops WHERE tripId = :tripId")
    suspend fun deleteForTrip(tripId: Long)
}

@Dao
interface PositionLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: PositionLog): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<PositionLog>)

    @Query("SELECT * FROM position_log WHERE epochMillis >= :from ORDER BY epochMillis")
    suspend fun since(from: Long): List<PositionLog>

    @Query("SELECT * FROM position_log WHERE epochMillis BETWEEN :from AND :to ORDER BY epochMillis")
    suspend fun between(from: Long, to: Long): List<PositionLog>

    @Query("SELECT * FROM position_log ORDER BY epochMillis DESC LIMIT 1")
    suspend fun latest(): PositionLog?

    @Query("SELECT COUNT(*) FROM position_log")
    fun observeCount(): Flow<Int>

    @Query("SELECT MIN(epochMillis) FROM position_log")
    fun observeOldest(): Flow<Long?>

    @Query("DELETE FROM position_log WHERE epochMillis < :before")
    suspend fun deleteOlderThan(before: Long)

    /**
     * Thin the log older than [before] to at most one row per [bucketMillis] window,
     * keeping the earliest row in each bucket.
     */
    @Query(
        "DELETE FROM position_log WHERE epochMillis < :before AND id NOT IN (" +
            "SELECT MIN(id) FROM position_log WHERE epochMillis < :before GROUP BY epochMillis / :bucketMillis)",
    )
    suspend fun downsampleOlderThan(before: Long, bucketMillis: Long)
}

@Dao
interface RouteLabelDao {
    @Query("SELECT * FROM route_labels")
    fun observeAll(): Flow<List<RouteLabel>>

    @Upsert
    suspend fun upsert(label: RouteLabel)

    @Query("DELETE FROM route_labels WHERE originPlaceId = :origin AND destinationPlaceId = :destination")
    suspend fun delete(origin: Long, destination: Long)
}

@Dao
interface PlaceWifiSignatureDao {
    @Query("SELECT * FROM place_wifi_signatures")
    suspend fun getAll(): List<PlaceWifiSignature>

    @Query("SELECT * FROM place_wifi_signatures WHERE placeId = :placeId")
    suspend fun getForPlace(placeId: Long): List<PlaceWifiSignature>

    @Upsert
    suspend fun upsertAll(signatures: List<PlaceWifiSignature>)

    @Query("DELETE FROM place_wifi_signatures WHERE placeId = :placeId")
    suspend fun deleteForPlace(placeId: Long)
}
