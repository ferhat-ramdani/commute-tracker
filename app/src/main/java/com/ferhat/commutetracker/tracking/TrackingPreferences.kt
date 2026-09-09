package com.ferhat.commutetracker.tracking

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tracking")

/**
 * Small persisted state for the background tracker. Survives process death so a
 * broadcast receiver waking cold can tell whether a trip is in progress.
 */
class TrackingPreferences(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("tracking_enabled")
        val MOVEMENT_STARTED_AT = longPreferencesKey("movement_started_at")
        val MOVEMENT_ORIGIN_PLACE_ID = longPreferencesKey("movement_origin_place_id")
        val CURRENT_PLACE_ID = longPreferencesKey("current_place_id")
        val LAST_TRACKING_EVENT_AT = longPreferencesKey("last_tracking_event_at")
    }

    val enabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.ENABLED] ?: false }

    val movementInProgress: Flow<Boolean> =
        context.dataStore.data.map { (it[Keys.MOVEMENT_STARTED_AT] ?: 0L) > 0L }

    suspend fun isEnabled(): Boolean = context.dataStore.data.first()[Keys.ENABLED] ?: false

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.ENABLED] = value }
    }

    suspend fun movementStartedAt(): Long? =
        context.dataStore.data.first()[Keys.MOVEMENT_STARTED_AT]?.takeIf { it > 0L }

    suspend fun movementOriginPlaceId(): Long? =
        context.dataStore.data.first()[Keys.MOVEMENT_ORIGIN_PLACE_ID]?.takeIf { it > 0L }

    suspend fun currentPlaceId(): Long? =
        context.dataStore.data.first()[Keys.CURRENT_PLACE_ID]?.takeIf { it > 0L }

    suspend fun setCurrentPlaceId(placeId: Long?) {
        context.dataStore.edit {
            if (placeId == null) it.remove(Keys.CURRENT_PLACE_ID) else it[Keys.CURRENT_PLACE_ID] = placeId
        }
    }

    suspend fun beginMovement(startedAt: Long, originPlaceId: Long?) {
        context.dataStore.edit {
            it[Keys.MOVEMENT_STARTED_AT] = startedAt
            if (originPlaceId != null) {
                it[Keys.MOVEMENT_ORIGIN_PLACE_ID] = originPlaceId
            } else {
                it.remove(Keys.MOVEMENT_ORIGIN_PLACE_ID)
            }
        }
    }

    suspend fun endMovement() {
        context.dataStore.edit {
            it.remove(Keys.MOVEMENT_STARTED_AT)
            it.remove(Keys.MOVEMENT_ORIGIN_PLACE_ID)
        }
    }

    suspend fun markEvent(at: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[Keys.LAST_TRACKING_EVENT_AT] = at }
    }

    val lastEventAt: Flow<Long> =
        context.dataStore.data.map { it[Keys.LAST_TRACKING_EVENT_AT] ?: 0L }
}
