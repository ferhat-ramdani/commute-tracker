package com.ferhat.commutetracker.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TripDetectorTest {

    private val detector = TripDetector(DetectionConfig.DEFAULT)
    private val preprocessor = TrackPreprocessor(DetectionConfig.DEFAULT)

    private fun run(points: List<GpsPoint>, known: List<KnownPlace> = emptyList()) =
        detector.detect(preprocessor.process(points), known)

    private val F = TrajectoryFixtures

    @Test
    fun `home to work is one trip`() {
        val trips = run(F.homeToWork())
        assertThat(trips).hasSize(1)
        val trip = trips.single()
        assertThat(trip.netMeters).isGreaterThan(600.0)
        assertThat(trip.stops).isEmpty()
        assertThat(trip.straightness).isGreaterThan(0.7)
    }

    @Test
    fun `walking to the mailbox and back is not a trip`() {
        val out = F.offset(F.HOME_LAT, F.HOME_LNG, northMeters = 30.0, eastMeters = 0.0)
        val points =
            F.dwell(F.HOME_LAT, F.HOME_LNG, 0L, 20) +
                F.path(listOf(F.HOME_LAT to F.HOME_LNG, out, F.HOME_LAT to F.HOME_LNG), 20 * 60_000L, 1) +
                F.dwell(F.HOME_LAT, F.HOME_LNG, 24 * 60_000L, 20)
        assertThat(run(points)).isEmpty()
    }

    @Test
    fun `a loop around the block that returns home is not a trip`() {
        val a = F.offset(F.HOME_LAT, F.HOME_LNG, 250.0, 0.0)
        val b = F.offset(F.HOME_LAT, F.HOME_LNG, 250.0, 250.0)
        val c = F.offset(F.HOME_LAT, F.HOME_LNG, 0.0, 250.0)
        val points =
            F.dwell(F.HOME_LAT, F.HOME_LNG, 0L, 15) +
                F.path(
                    listOf(F.HOME_LAT to F.HOME_LNG, a, b, c, F.HOME_LAT to F.HOME_LNG),
                    15 * 60_000L, 2,
                ) +
                F.dwell(F.HOME_LAT, F.HOME_LNG, 45 * 60_000L, 15)
        assertThat(run(points)).isEmpty()
    }

    @Test
    fun `a bus wait mid-commute stays one trip with a stop`() {
        val busStop = F.offset(F.HOME_LAT, F.HOME_LNG, 800.0, 0.0)
        var t = 0L
        val home = F.dwell(F.HOME_LAT, F.HOME_LNG, t, 15); t = home.last().epochMillis + 30_000L
        val leg1 = F.move(F.HOME_LAT, F.HOME_LNG, busStop.first, busStop.second, t, 8); t = leg1.last().epochMillis + 30_000L
        val wait = F.dwell(busStop.first, busStop.second, t, 12); t = wait.last().epochMillis + 30_000L
        val leg2 = F.move(busStop.first, busStop.second, F.WORK_LAT, F.WORK_LNG, t, 10); t = leg2.last().epochMillis + 30_000L
        val work = F.dwell(F.WORK_LAT, F.WORK_LNG, t, 15)

        val trips = run(home + leg1 + wait + leg2 + work)
        assertThat(trips).hasSize(1)
        assertThat(trips.single().stops).hasSize(1)
        assertThat(trips.single().stops.single().durationMillis).isAtLeast(10 * 60_000L)
    }

    @Test
    fun `a long stop splits into two trips`() {
        val midpoint = F.MOSQUE_LAT to F.MOSQUE_LNG
        var t = 0L
        val home = F.dwell(F.HOME_LAT, F.HOME_LNG, t, 15); t = home.last().epochMillis + 30_000L
        val leg1 = F.move(F.HOME_LAT, F.HOME_LNG, midpoint.first, midpoint.second, t, 10); t = leg1.last().epochMillis + 30_000L
        val longStop = F.dwell(midpoint.first, midpoint.second, t, 30); t = longStop.last().epochMillis + 30_000L
        val leg2 = F.move(midpoint.first, midpoint.second, F.WORK_LAT, F.WORK_LNG, t, 12); t = leg2.last().epochMillis + 30_000L
        val work = F.dwell(F.WORK_LAT, F.WORK_LNG, t, 15)

        val trips = run(home + leg1 + longStop + leg2 + work)
        assertThat(trips).hasSize(2)
    }

    @Test
    fun `endpoints resolve to known places`() {
        val known = listOf(
            KnownPlace(10L, F.HOME_LAT, F.HOME_LNG, 35.0),
            KnownPlace(20L, F.WORK_LAT, F.WORK_LNG, 35.0),
        )
        val trip = run(F.homeToWork(), known).single()
        assertThat(trip.originPlaceId).isEqualTo(10L)
        assertThat(trip.destinationPlaceId).isEqualTo(20L)
    }

    @Test
    fun `re-running on the same trace is idempotent`() {
        val trace = preprocessor.process(F.homeToWork())
        val first = detector.detect(trace, emptyList())
        val second = detector.detect(trace, emptyList())
        assertThat(second.map { it.startMillis to it.endMillis })
            .isEqualTo(first.map { it.startMillis to it.endMillis })
    }
}
