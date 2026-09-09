package com.ferhat.commutetracker.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TripSegmenterTest {

    private val detector = StayPointDetector()
    private val segmenter = TripSegmenter(minDistanceMeters = 150.0, minDurationMillis = 120_000L)

    @Test
    fun `home to work is one trip between the two stays`() {
        val points = TrajectoryFixtures.homeToWork()
        val stays = detector.detect(points)
        val trips = segmenter.segment(points, stays)

        assertThat(trips).hasSize(1)
        val trip = trips.single()
        assertThat(trip.originStay).isEqualTo(stays[0])
        assertThat(trip.destinationStay).isEqualTo(stays[1])
        assertThat(trip.distanceMeters).isGreaterThan(1_500.0)
        assertThat(trip.durationMillis).isGreaterThan(0L)
    }

    @Test
    fun `a few metres of wandering is not a trip`() {
        val points =
            TrajectoryFixtures.dwell(TrajectoryFixtures.HOME_LAT, TrajectoryFixtures.HOME_LNG, 0L, 20) +
                TrajectoryFixtures.move(
                    TrajectoryFixtures.HOME_LAT, TrajectoryFixtures.HOME_LNG,
                    TrajectoryFixtures.SHOP_LAT, TrajectoryFixtures.SHOP_LNG, 21 * 60_000L, 4,
                ) +
                TrajectoryFixtures.dwell(TrajectoryFixtures.SHOP_LAT, TrajectoryFixtures.SHOP_LNG, 26 * 60_000L, 20)
        val stays = detector.detect(points)
        val trips = segmenter.segment(points, stays)
        assertThat(trips).isEmpty()
    }

    @Test
    fun `no stay points still yields a best-effort trip`() {
        val points = TrajectoryFixtures.move(
            TrajectoryFixtures.HOME_LAT, TrajectoryFixtures.HOME_LNG,
            TrajectoryFixtures.WORK_LAT, TrajectoryFixtures.WORK_LNG, 0L, 15,
        )
        val trips = segmenter.segment(points, emptyList())
        assertThat(trips).hasSize(1)
        assertThat(trips.single().originStay).isNull()
        assertThat(trips.single().destinationStay).isNull()
    }
}
