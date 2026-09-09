package com.ferhat.commutetracker.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StayPointDetectorTest {

    private val detector = StayPointDetector(distanceThresholdMeters = 60.0, timeThresholdMillis = 5 * 60_000L)

    @Test
    fun `sitting in one place all morning is a single stay point`() {
        val points = TrajectoryFixtures.dwell(
            TrajectoryFixtures.HOME_LAT, TrajectoryFixtures.HOME_LNG, startMillis = 0L, minutes = 40,
        )
        val stays = detector.detect(points)
        assertThat(stays).hasSize(1)
        assertThat(stays[0].latitude).isWithin(0.001).of(TrajectoryFixtures.HOME_LAT)
        assertThat(stays[0].dwellMillis).isAtLeast(30 * 60_000L)
    }

    @Test
    fun `home then work yields two stay points`() {
        val stays = detector.detect(TrajectoryFixtures.homeToWork())
        assertThat(stays).hasSize(2)
        assertThat(stays[0].latitude).isWithin(0.002).of(TrajectoryFixtures.HOME_LAT)
        assertThat(stays[1].latitude).isWithin(0.002).of(TrajectoryFixtures.WORK_LAT)
        assertThat(stays[0].departureMillis).isLessThan(stays[1].arrivalMillis)
    }

    @Test
    fun `just passing through produces no stay point`() {
        val points = TrajectoryFixtures.move(
            TrajectoryFixtures.HOME_LAT, TrajectoryFixtures.HOME_LNG,
            TrajectoryFixtures.WORK_LAT, TrajectoryFixtures.WORK_LNG,
            startMillis = 0L, minutes = 15,
        )
        assertThat(detector.detect(points)).isEmpty()
    }

    @Test
    fun `empty and tiny traces are handled`() {
        assertThat(detector.detect(emptyList())).isEmpty()
        assertThat(detector.detect(listOf(GpsPoint(1.0, 1.0, 0L)))).isEmpty()
    }
}
