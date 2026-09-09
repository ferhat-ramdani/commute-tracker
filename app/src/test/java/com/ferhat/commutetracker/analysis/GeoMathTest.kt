package com.ferhat.commutetracker.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoMathTest {

    @Test
    fun `one degree of latitude is about 111 km`() {
        val d = GeoMath.distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertThat(d).isWithin(1_000.0).of(111_195.0)
    }

    @Test
    fun `distance is zero for identical points`() {
        assertThat(GeoMath.distanceMeters(36.75, 3.06, 36.75, 3.06)).isEqualTo(0.0)
    }

    @Test
    fun `centroid averages the points`() {
        val (lat, lng) = GeoMath.centroid(listOf(0.0 to 0.0, 2.0 to 4.0))
        assertThat(lat).isEqualTo(1.0)
        assertThat(lng).isEqualTo(2.0)
    }

    @Test
    fun `path length sums the segments`() {
        val points = listOf(0.0 to 0.0, 0.0 to 0.001, 0.0 to 0.002)
        val expectedSegment = GeoMath.distanceMeters(0.0, 0.0, 0.0, 0.001)
        assertThat(GeoMath.pathLengthMeters(points)).isWithin(0.01).of(expectedSegment * 2)
    }
}
