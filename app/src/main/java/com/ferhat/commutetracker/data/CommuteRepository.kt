package com.ferhat.commutetracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class CommuteRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val locationDao = db.locationDao()
    private val commuteDao = db.commuteDao()

    val locations: Flow<List<LocationEntity>> = locationDao.observeAll()
    val commutes: Flow<List<CommuteEntity>> = commuteDao.observeAll()
    val activeCommute: Flow<CommuteEntity?> = commuteDao.observeActive()

    suspend fun addLocation(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) locationDao.insert(LocationEntity(name = trimmed))
    }

    suspend fun renameLocation(location: LocationEntity, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) locationDao.update(location.copy(name = trimmed))
    }

    suspend fun deleteLocation(location: LocationEntity) = locationDao.delete(location)

    /** Starts a commute now. No-op if one is already running. */
    suspend fun startCommute(originName: String, destinationName: String) {
        if (commuteDao.getActive() != null) return
        commuteDao.insert(
            CommuteEntity(
                originName = originName,
                destinationName = destinationName,
                startEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun endCommute(commute: CommuteEntity) {
        if (commute.endEpochMillis != null) return
        commuteDao.update(commute.copy(endEpochMillis = System.currentTimeMillis()))
    }

    /** Discards an in-progress commute without recording it. */
    suspend fun cancelCommute(commute: CommuteEntity) = commuteDao.delete(commute)

    suspend fun deleteCommute(commute: CommuteEntity) = commuteDao.delete(commute)
}
