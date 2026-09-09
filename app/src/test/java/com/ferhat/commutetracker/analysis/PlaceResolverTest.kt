package com.ferhat.commutetracker.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaceResolverTest {

    private val resolver = PlaceResolver(minMatchRadiusMeters = 45.0)

    private val home = KnownPlace(1L, TrajectoryFixtures.HOME_LAT, TrajectoryFixtures.HOME_LNG, radiusMeters = 35.0)
    private val work = KnownPlace(2L, TrajectoryFixtures.WORK_LAT, TrajectoryFixtures.WORK_LNG, radiusMeters = 35.0)

    @Test
    fun `a point at a known place matches it`() {
        val match = resolver.resolve(TrajectoryFixtures.HOME_LAT, TrajectoryFixtures.HOME_LNG, listOf(home, work))
        assertThat(match).isEqualTo(PlaceMatch.Existing(1L))
    }

    @Test
    fun `a point far from everything is new`() {
        val match = resolver.resolve(36.9000, 3.2000, listOf(home, work))
        assertThat(match).isInstanceOf(PlaceMatch.New::class.java)
    }

    @Test
    fun `the nearest known place wins`() {
        // ~20 m north of home, well within the 45 m floor.
        val match = resolver.resolve(TrajectoryFixtures.HOME_LAT + 0.00018, TrajectoryFixtures.HOME_LNG, listOf(home, work))
        assertThat(match).isEqualTo(PlaceMatch.Existing(1L))
    }

    @Test
    fun `no known places means everything is new`() {
        assertThat(resolver.resolve(1.0, 1.0, emptyList())).isInstanceOf(PlaceMatch.New::class.java)
    }
}
