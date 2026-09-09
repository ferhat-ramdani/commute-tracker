package com.ferhat.commutetracker.analysis

import android.content.Context
import com.ferhat.commutetracker.data.AppDatabase
import com.ferhat.commutetracker.data.PlaceRepository
import com.ferhat.commutetracker.data.Trip
import java.util.concurrent.TimeUnit

/**
 * Turns the raw location samples collected during a movement session into [Trip] rows,
 * discovering new [com.ferhat.commutetracker.data.Place]s as needed. Idempotent-ish:
 * only ever consumes *unassigned* samples, so re-running is safe.
 */
class TripAnalyzer(context: Context) {

    private val db = AppDatabase.get(context)
    private val sampleDao = db.locationSampleDao()
    private val tripDao = db.tripDao()
    private val placeDao = db.placeDao()
    private val placeRepository = PlaceRepository(context)

    private val detector = StayPointDetector()
    private val segmenter = TripSegmenter()
    private val resolver = PlaceResolver()

    /** @return ids of any newly discovered (unconfirmed) places. */
    suspend fun analyzePendingSamples(fallbackOriginPlaceId: Long?): List<Long> {
        val samples = sampleDao.getUnassigned().sortedBy { it.epochMillis }
        val newPlaceIds = mutableListOf<Long>()
        if (samples.size < 2) {
            sampleDao.pruneUnassignedBefore(cutoffNow())
            return newPlaceIds
        }

        val points = samples.map {
            GpsPoint(it.latitude, it.longitude, it.epochMillis, it.accuracyMeters)
        }
        val stayPoints = detector.detect(points)
        val rawTrips = segmenter.segment(points, stayPoints)
        if (rawTrips.isEmpty()) {
            sampleDao.pruneUnassignedBefore(cutoffNow())
            return newPlaceIds
        }

        var previousDestinationPlaceId: Long? = fallbackOriginPlaceId

        rawTrips.forEachIndexed { index, raw ->
            val originPlaceId = raw.originStay
                ?.let { stay -> resolvePlace(stay.latitude, stay.longitude, stay.arrivalMillis, newPlaceIds) }
                ?: previousDestinationPlaceId

            val destinationPlaceId = when {
                raw.destinationStay != null -> resolvePlace(
                    raw.destinationStay.latitude,
                    raw.destinationStay.longitude,
                    raw.destinationStay.arrivalMillis,
                    newPlaceIds,
                )
                index == rawTrips.lastIndex -> {
                    // Movement ended without a long enough dwell; use the last fix as a
                    // best-effort destination so the trip still groups.
                    val last = points.last()
                    resolvePlace(last.latitude, last.longitude, last.epochMillis, newPlaceIds)
                }
                else -> null
            }

            val tripId = tripDao.insert(
                Trip(
                    originPlaceId = originPlaceId,
                    destinationPlaceId = destinationPlaceId,
                    startEpochMillis = raw.startMillis,
                    endEpochMillis = raw.endMillis,
                    distanceMeters = raw.distanceMeters,
                    sampleCount = raw.pointCount,
                    isAuto = true,
                    isConfirmed = false,
                ),
            )
            sampleDao.assignToTrip(tripId, raw.startMillis, raw.endMillis)
            originPlaceId?.let { placeRepository.recordVisit(it, raw.startMillis) }
            destinationPlaceId?.let { placeRepository.recordVisit(it, raw.endMillis) }
            previousDestinationPlaceId = destinationPlaceId
        }

        // Samples that fell inside a stay (not a trip) were just "sitting still".
        sampleDao.pruneUnassignedBefore(cutoffNow())
        return newPlaceIds.distinct()
    }

    suspend fun pruneOldSamples(retentionDays: Int = 7) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        sampleDao.pruneOlderThan(cutoff)
    }

    private suspend fun resolvePlace(
        latitude: Double,
        longitude: Double,
        seenAt: Long,
        newPlaceIds: MutableList<Long>,
    ): Long {
        val known = placeDao.getLocated().map {
            KnownPlace(it.id, it.latitude!!, it.longitude!!, it.radiusMeters)
        }
        return when (val match = resolver.resolve(latitude, longitude, known)) {
            is PlaceMatch.Existing -> match.placeId
            is PlaceMatch.New -> placeRepository
                .addAutoPlace(latitude, longitude, seenAt = seenAt)
                .also { newPlaceIds.add(it) }
        }
    }

    private fun cutoffNow() = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2)
}
