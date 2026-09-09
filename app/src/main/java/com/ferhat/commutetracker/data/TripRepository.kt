package com.ferhat.commutetracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TripRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val tripDao = db.tripDao()
    private val sampleDao = db.locationSampleDao()
    private val routeLabelDao = db.routeLabelDao()

    val finishedTrips: Flow<List<Trip>> = tripDao.observeFinished()
    val activeTrip: Flow<Trip?> = tripDao.observeActive()
    val allTrips: Flow<List<Trip>> = tripDao.observeAll()
    val routeLabels: Flow<List<RouteLabel>> = routeLabelDao.observeAll()

    // ---- Manual flow -------------------------------------------------------

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
        sampleDao.deleteForTrip(active.id)
        tripDao.delete(active)
    }

    // ---- Auto flow (used by the tracker) ---------------------------------

    suspend fun getActiveTrip(): Trip? = tripDao.getActive()

    suspend fun startAutoTrip(originPlaceId: Long?, startedAt: Long): Long {
        return tripDao.insert(
            Trip(
                originPlaceId = originPlaceId,
                startEpochMillis = startedAt,
                isAuto = true,
                isConfirmed = false,
            ),
        )
    }

    suspend fun finishAutoTrip(
        tripId: Long,
        destinationPlaceId: Long?,
        endedAt: Long,
        distanceMeters: Double,
        sampleCount: Int,
    ) {
        val trip = tripDao.getById(tripId) ?: return
        tripDao.update(
            trip.copy(
                destinationPlaceId = destinationPlaceId,
                endEpochMillis = endedAt,
                distanceMeters = distanceMeters,
                sampleCount = sampleCount,
            ),
        )
    }

    suspend fun discardTrip(tripId: Long) {
        sampleDao.deleteForTrip(tripId)
        tripDao.getById(tripId)?.let { tripDao.delete(it) }
    }

    // ---- Editing --------------------------------------------------------

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
        sampleDao.deleteForTrip(trip.id)
        tripDao.delete(trip)
    }

    // ---- Samples -------------------------------------------------------

    suspend fun addSample(sample: LocationSample) = sampleDao.insert(sample)

    suspend fun samplesForTrip(tripId: Long): List<LocationSample> = sampleDao.getForTrip(tripId)

    suspend fun pruneSamplesOlderThan(cutoffMillis: Long) = sampleDao.pruneOlderThan(cutoffMillis)

    // ---- Route labels -------------------------------------------------

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
