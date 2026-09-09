package com.ferhat.commutetracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ferhat.commutetracker.data.CommuteEntity
import com.ferhat.commutetracker.data.CommuteRepository
import com.ferhat.commutetracker.data.LocationEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CommuteViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CommuteRepository(app)

    val locations: StateFlow<List<LocationEntity>> =
        repo.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val commutes: StateFlow<List<CommuteEntity>> =
        repo.commutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeCommute: StateFlow<CommuteEntity?> =
        repo.activeCommute.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startCommute(originName: String, destinationName: String) = viewModelScope.launch {
        repo.startCommute(originName, destinationName)
    }

    fun endActiveCommute() = viewModelScope.launch {
        activeCommute.value?.let { repo.endCommute(it) }
    }

    fun cancelActiveCommute() = viewModelScope.launch {
        activeCommute.value?.let { repo.cancelCommute(it) }
    }

    fun deleteCommute(commute: CommuteEntity) = viewModelScope.launch {
        repo.deleteCommute(commute)
    }

    fun addLocation(name: String) = viewModelScope.launch { repo.addLocation(name) }

    fun renameLocation(location: LocationEntity, newName: String) = viewModelScope.launch {
        repo.renameLocation(location, newName)
    }

    fun deleteLocation(location: LocationEntity) = viewModelScope.launch {
        repo.deleteLocation(location)
    }
}
