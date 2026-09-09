package com.ferhat.commutetracker.analysis

import android.content.Context
import com.ferhat.commutetracker.data.AppDatabase
import com.ferhat.commutetracker.data.PlaceRepository
import com.ferhat.commutetracker.data.Trip
import com.ferhat.commutetracker.data.TripStop
import java.util.concurrent.TimeUnit

/**
 * Runs [TripDetector] over a rolling window of the raw position log and writes any
 * newly-found journeys. Only ever *adds* trips that don't overlap an existing one, so
 * it can be re-run freely (after every stop and nightly).
 */
class TripAnalyzer(
    context: Context,
    private val config: DetectionConfig = DetectionConfig.DEFAULT,
) {
    private val db = AppDatabase.get(context)
    private val positionLogDao = db.positionLogDao()
    private val tripDao = db.tripDao()
    private val tripStopDao = db.tripStopDao()
    private val placeDao = db.placeDao()
    private val placeRepository = PlaceRepository(context)

    private val preprocessor = TrackPreprocessor(config)
    private val detector = TripDetector(config)

    private val lookbackMillis = TimeUnit.HOURS.toMillis(26)

    /** @return ids of any newly discovered (unconfirmed) places. */
    suspend fun analyzeRecent(fallbackOriginPlaceId: Long?): List<Long> {
        val now = System.currentTimeMillis()
        val lastEnd = tripDao.lastAutoTripEnd() ?: 0L
        val windowStart = maxOf(lastEnd - TimeUnit.MINUTES.toMillis(30), now - lookbackMillis)

        val logs = positionLogDao.since(windowStart)
        if (logs.size < 3) return emptyList()

        val points = logs.map {
            GpsPoint(it.latitude, it.longitude, it.epochMillis, it.accuracyMeters)
        }
        val clean = preprocessor.process(points)
        val detected = detector.detect(clean, knownPlaces(), fallbackOriginPlaceId)

        val existing = tripDao.getAutoTripsSince(windowStart - TimeUnit.HOURS.toMillis(2))
            .filter { it.endEpochMillis != null }
        val newPlaceIds = mutableListOf<Long>()

        for (trip in detected) {
            val overlaps = existing.any {
                it.startEpochMillis < trip.endMillis && (it.endEpochMillis ?: 0L) > trip.startMillis
            }
            if (overlaps) continue

            val originId = trip.originPlaceId
                ?: ensurePlace(trip.originLatitude, trip.originLongitude, trip.startMillis, newPlaceIds)
            val destinationId = trip.destinationPlaceId
                ?: ensurePlace(trip.destinationLatitude, trip.destinationLongitude, trip.endMillis, newPlaceIds)
            if (originId == destinationId) continue

            val tripId = tripDao.insert(
                Trip(
                    originPlaceId = originId,
                    destinationPlaceId = destinationId,
                    startEpochMillis = trip.startMillis,
                    endEpochMillis = trip.endMillis,
                    distanceMeters = trip.pathMeters,
                    sampleCount = trip.pointCount,
                    isAuto = true,
                    isConfirmed = false,
                    straightness = trip.straightness,
                    maxAccuracyMeters = trip.maxAccuracyMeters,
                ),
            )
            trip.stops.forEach { stop ->
                tripStopDao.insert(
                    TripStop(
                        tripId = tripId,
                        latitude = stop.latitude,
                        longitude = stop.longitude,
                        arrivalMillis = stop.arrivalMillis,
                        departureMillis = stop.departureMillis,
                        kind = stop.kind,
                    ),
                )
            }
            placeRepository.recordVisit(originId, trip.startMillis)
            placeRepository.recordVisit(destinationId, trip.endMillis)
        }
        return newPlaceIds.distinct()
    }

    /** Trim the raw position log: thin the old part, hard-delete the very old part. */
    suspend fun applyRetention() {
        val now = System.currentTimeMillis()
        val fullCutoff = now - TimeUnit.DAYS.toMillis(config.positionLogFullRetentionDays.toLong())
        positionLogDao.downsampleOlderThan(fullCutoff, config.positionLogDownsampleGapMillis)
        val hardCutoff = now - TimeUnit.DAYS.toMillis(2L * config.positionLogFullRetentionDays)
        positionLogDao.deleteOlderThan(hardCutoff)
    }

    private suspend fun knownPlaces(): List<KnownPlace> =
        placeDao.getLocated().map {
            KnownPlace(it.id, it.latitude!!, it.longitude!!, it.radiusMeters, it.isTransit)
        }

    private suspend fun ensurePlace(
        latitude: Double,
        longitude: Double,
        seenAt: Long,
        newPlaceIds: MutableList<Long>,
    ): Long {
        val resolver = PlaceResolver(minMatchRadiusMeters = config.newPlaceRadiusMeters + 10.0)
        return when (val match = resolver.resolve(latitude, longitude, knownPlaces())) {
            is PlaceMatch.Existing -> match.placeId
            is PlaceMatch.New -> placeRepository
                .addAutoPlace(latitude, longitude, config.newPlaceRadiusMeters, seenAt)
                .also { newPlaceIds.add(it) }
        }
    }
}
