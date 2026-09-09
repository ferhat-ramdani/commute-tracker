package com.ferhat.commutetracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ferhat.commutetracker.analysis.RouteGroup
import com.ferhat.commutetracker.analysis.TripGrouper
import com.ferhat.commutetracker.data.Place
import com.ferhat.commutetracker.data.PlaceRepository
import com.ferhat.commutetracker.data.Trip
import com.ferhat.commutetracker.data.TripRepository
import com.ferhat.commutetracker.data.TripStop
import com.ferhat.commutetracker.tracking.OneShotLocation
import com.ferhat.commutetracker.tracking.TrackingController
import com.ferhat.commutetracker.tracking.TrackingPermissions
import com.ferhat.commutetracker.tracking.TrackingPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrackerUiState(
    val places: List<Place> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val tripStops: List<TripStop> = emptyList(),
    val routeGroups: List<RouteGroup> = emptyList(),
    val activeTrip: Trip? = null,
    val trackingEnabled: Boolean = false,
    val positionLogCount: Int = 0,
    val positionLogOldestMillis: Long? = null,
) {
    val placesById: Map<Long, Place> get() = places.associateBy { it.id }
    val discoveredPlaces: List<Place> get() = places.filter { !it.isConfirmed }
    fun stopsForTrip(tripId: Long): List<TripStop> = tripStops.filter { it.tripId == tripId }
}

class TrackerViewModel(app: Application) : AndroidViewModel(app) {

    private val placeRepository = PlaceRepository(app)
    private val tripRepository = TripRepository(app)
    private val trackingPreferences = TrackingPreferences(app)

    private val core = combine(
        placeRepository.places,
        tripRepository.finishedTrips,
        tripRepository.activeTrip,
        tripRepository.routeLabels,
        trackingPreferences.enabled,
    ) { places, trips, active, routeLabels, trackingEnabled ->
        val labelMap = routeLabels.associateBy { it.originPlaceId to it.destinationPlaceId }
        TrackerUiState(
            places = places,
            trips = trips,
            routeGroups = TripGrouper.group(trips, places.associateBy { it.id }, labelMap),
            activeTrip = active,
            trackingEnabled = trackingEnabled,
        )
    }

    val uiState: StateFlow<TrackerUiState> = combine(
        core,
        tripRepository.tripStops,
        tripRepository.positionLogCount,
        tripRepository.positionLogOldest,
    ) { state, stops, logCount, oldest ->
        state.copy(
            tripStops = stops,
            positionLogCount = logCount,
            positionLogOldestMillis = oldest,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackerUiState())

    // ---- tracking -----------------------------------------------------

    fun setTrackingEnabled(enabled: Boolean) = viewModelScope.launch {
        val controller = TrackingController.get(getApplication())
        if (enabled) controller.enableTracking() else controller.disableTracking()
    }

    fun permissionsSatisfied(): Boolean = TrackingPermissions.hasAllRequired(getApplication())

    fun missingPermissions(): List<String> = TrackingPermissions.missing(getApplication())

    // ---- manual trips ----------------------------------------------

    fun startManualTrip(originPlaceId: Long, destinationPlaceId: Long) = viewModelScope.launch {
        tripRepository.startManualTrip(originPlaceId, destinationPlaceId)
    }

    fun endActiveTrip() = viewModelScope.launch { tripRepository.endActiveTrip() }

    fun cancelActiveTrip() = viewModelScope.launch { tripRepository.cancelActiveTrip() }

    fun deleteTrip(trip: Trip) = viewModelScope.launch { tripRepository.delete(trip) }

    fun confirmTrip(tripId: Long) = viewModelScope.launch { tripRepository.confirm(tripId) }

    fun setTripEndpoints(tripId: Long, originPlaceId: Long?, destinationPlaceId: Long?) =
        viewModelScope.launch { tripRepository.setEndpoints(tripId, originPlaceId, destinationPlaceId) }

    // ---- places -----------------------------------------------------

    fun addPlace(label: String) = viewModelScope.launch { placeRepository.addUserPlace(label) }

    fun renamePlace(place: Place, label: String) = viewModelScope.launch {
        placeRepository.rename(place, label)
        TrackingController.get(getApplication()).refreshGeofences()
    }

    fun confirmPlace(place: Place) = viewModelScope.launch { placeRepository.confirm(place) }

    fun setPlaceRadius(place: Place, radiusMeters: Double) = viewModelScope.launch {
        placeRepository.setRadius(place, radiusMeters)
        TrackingController.get(getApplication()).refreshGeofences()
    }

    fun setPlaceTransit(place: Place, transit: Boolean) = viewModelScope.launch {
        placeRepository.setKind(place, if (transit) Place.KIND_TRANSIT else Place.KIND_NORMAL)
    }

    fun locatePlaceHere(place: Place) = viewModelScope.launch {
        val fix = OneShotLocation.current(getApplication()) ?: return@launch
        placeRepository.setCoordinates(place, fix.first, fix.second)
        TrackingController.get(getApplication()).refreshGeofences()
    }

    fun deletePlace(place: Place) = viewModelScope.launch {
        placeRepository.delete(place)
        TrackingController.get(getApplication()).refreshGeofences()
    }

    fun mergePlaces(keep: Place, from: Place) = viewModelScope.launch {
        placeRepository.merge(keep, from)
        TrackingController.get(getApplication()).refreshGeofences()
    }

    // ---- routes ---------------------------------------------------

    fun setRouteLabel(originPlaceId: Long, destinationPlaceId: Long, label: String) =
        viewModelScope.launch {
            if (label.isBlank()) tripRepository.clearRouteLabel(originPlaceId, destinationPlaceId)
            else tripRepository.setRouteLabel(originPlaceId, destinationPlaceId, label)
        }
}
