package com.ferhat.commutetracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TripRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val tripDao = db.tripDao()
    private val tripStopDao = db.tripStopDao()
    private val positionLogDao = db.positionLogDao()
    private val routeLabelDao = db.routeLabelDao()

    val finishedTrips: Flow<List<Trip>> = tripDao.observeFinished()
    val activeTrip: Flow<Trip?> = tripDao.observeActive()
    val allTrips: Flow<List<Trip>> = tripDao.observeAll()
    val routeLabels: Flow<List<RouteLabel>> = routeLabelDao.observeAll()
    val tripStops: Flow<List<TripStop>> = tripStopDao.observeAll()

    val positionLogCount: Flow<Int> = positionLogDao.observeCount()
    val positionLogOldest: Flow<Long?> = positionLogDao.observeOldest()

    // ---- raw position log ---------------------------------------------

    suspend fun logPosition(entry: PositionLog) = positionLogDao.insert(entry)

    // ---- manual flow -------------------------------------------------

    suspend fun startManualTrip(originPlaceId: Long, destinationPlaceId: Long): Long? {
        if (tripDao.getActive() != null) return null
        return tripDao.insert(
            Trip(
                originPlaceId = originPlaceId,
                destinationPlaceId = destinationPlaceId,
                startEpochMillis = System.currentTimeMillis(),
                isAuto = false,
                isConfirmed = true,
            ),
        )
    }

    suspend fun endActiveTrip() {
        val active = tripDao.getActive() ?: return
        tripDao.update(active.copy(endEpochMillis = System.currentTimeMillis()))
    }

    suspend fun cancelActiveTrip() {
        val active = tripDao.getActive() ?: return
        tripDao.delete(active)
    }

    suspend fun getActiveTrip(): Trip? = tripDao.getActive()

    // ---- editing ---------------------------------------------------

    suspend fun setEndpoints(tripId: Long, originPlaceId: Long?, destinationPlaceId: Long?) {
        val trip = tripDao.getById(tripId) ?: return
        tripDao.update(
            trip.copy(
                originPlaceId = originPlaceId,
                destinationPlaceId = destinationPlaceId,
                isConfirmed = true,
            ),
        )
    }

    suspend fun confirm(tripId: Long) {
        tripDao.getById(tripId)?.let { tripDao.update(it.copy(isConfirmed = true)) }
    }

    suspend fun delete(trip: Trip) {
        tripStopDao.deleteForTrip(trip.id)
        tripDao.delete(trip)
    }

    suspend fun stopsForTrip(tripId: Long): List<TripStop> = tripStopDao.getForTrip(tripId)

    // ---- route labels -------------------------------------------

    suspend fun setRouteLabel(originPlaceId: Long, destinationPlaceId: Long, label: String) {
        routeLabelDao.upsert(
            RouteLabel(
                originPlaceId = originPlaceId,
                destinationPlaceId = destinationPlaceId,
                label = label.trim(),
                isUserSet = true,
            ),
        )
    }

    suspend fun clearRouteLabel(originPlaceId: Long, destinationPlaceId: Long) =
        routeLabelDao.delete(originPlaceId, destinationPlaceId)
}
