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
interface LocationSampleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sample: LocationSample): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(samples: List<LocationSample>)

    @Query("SELECT * FROM location_samples WHERE tripId = :tripId ORDER BY epochMillis")
    suspend fun getForTrip(tripId: Long): List<LocationSample>

    @Query("SELECT * FROM location_samples WHERE tripId IS NULL ORDER BY epochMillis")
    suspend fun getUnassigned(): List<LocationSample>

    @Query("UPDATE location_samples SET tripId = :tripId WHERE tripId IS NULL AND epochMillis BETWEEN :from AND :to")
    suspend fun assignToTrip(tripId: Long, from: Long, to: Long)

    @Query("DELETE FROM location_samples WHERE epochMillis < :before")
    suspend fun pruneOlderThan(before: Long)

    @Query("DELETE FROM location_samples WHERE tripId IS NULL AND epochMillis < :before")
    suspend fun pruneUnassignedBefore(before: Long)

    @Query("DELETE FROM location_samples WHERE tripId = :tripId")
    suspend fun deleteForTrip(tripId: Long)
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
