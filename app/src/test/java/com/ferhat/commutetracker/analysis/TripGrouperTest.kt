package com.ferhat.commutetracker.analysis

import com.ferhat.commutetracker.data.Place
import com.ferhat.commutetracker.data.RouteLabel
import com.ferhat.commutetracker.data.Trip
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TripGrouperTest {

    private val places = mapOf(
        1L to Place(id = 1L, label = "Home"),
        2L to Place(id = 2L, label = "Work"),
        3L to Place(id = 3L, label = "Gym"),
    )

    private fun trip(origin: Long?, dest: Long?, start: Long, durationMin: Long) = Trip(
        originPlaceId = origin,
        destinationPlaceId = dest,
        startEpochMillis = start,
        endEpochMillis = start + durationMin * 60_000L,
    )

    @Test
    fun `groups by origin-destination pair and counts`() {
        val trips = listOf(
            trip(1L, 2L, 0L, 20),
            trip(1L, 2L, 86_400_000L, 24),
            trip(1L, 2L, 172_800_000L, 28),
            trip(2L, 1L, 200_000_000L, 22),
        )
        val groups = TripGrouper.group(trips, places, emptyMap())

        assertThat(groups).hasSize(2)
        val homeToWork = groups.first { it.originPlaceId == 1L && it.destinationPlaceId == 2L }
        assertThat(homeToWork.tripCount).isEqualTo(3)
        assertThat(homeToWork.label).isEqualTo("Home → Work")
        assertThat(homeToWork.medianDurationMillis).isEqualTo(24 * 60_000L)
        assertThat(homeToWork.shortestDurationMillis).isEqualTo(20 * 60_000L)
        assertThat(homeToWork.longestDurationMillis).isEqualTo(28 * 60_000L)
    }

    @Test
    fun `trips with an unknown endpoint are ignored`() {
        val trips = listOf(trip(1L, null, 0L, 15), trip(null, 2L, 1_000L, 15))
        assertThat(TripGrouper.group(trips, places, emptyMap())).isEmpty()
    }

    @Test
    fun `in-progress trips are ignored`() {
        val open = Trip(originPlaceId = 1L, destinationPlaceId = 2L, startEpochMillis = 0L, endEpochMillis = null)
        assertThat(TripGrouper.group(listOf(open), places, emptyMap())).isEmpty()
    }

    @Test
    fun `a route label overrides the default name`() {
        val trips = listOf(trip(1L, 3L, 0L, 12), trip(1L, 3L, 100_000L, 14))
        val labels = mapOf((1L to 3L) to RouteLabel(1L, 3L, "Evening workout", category = "leisure"))
        val group = TripGrouper.group(trips, places, labels).single()
        assertThat(group.label).isEqualTo("Evening workout")
        assertThat(group.category).isEqualTo("leisure")
    }

    @Test
    fun `groups are sorted by trip count`() {
        val trips = listOf(
            trip(1L, 3L, 0L, 12),
            trip(1L, 2L, 1L, 20),
            trip(1L, 2L, 2L, 20),
        )
        val groups = TripGrouper.group(trips, places, emptyMap())
        assertThat(groups.first().destinationPlaceId).isEqualTo(2L)
    }
}
