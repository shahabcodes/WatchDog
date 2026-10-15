package com.shahaabapps.core.firebase.geofence

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.GeofenceEvent
import kotlinx.coroutines.flow.Flow

/**
 * Parent-authored geofences and the enter/exit events the child reports.
 *
 * Firestore layout:
 *  - children/{childId}/geofences/{geoId}
 *  - children/{childId}/geofenceEvents/{eventId}
 */
interface GeofenceRepository {

    fun observeGeofences(childId: String): Flow<List<Geofence>>

    suspend fun saveGeofence(childId: String, geofence: Geofence): AppResult<Unit>

    suspend fun deleteGeofence(childId: String, geofenceId: String): AppResult<Unit>

    /** Child reports an enter/exit transition. */
    suspend fun reportEvent(childId: String, event: GeofenceEvent): AppResult<Unit>

    /** Most recent events, newest first (for the parent's activity feed). */
    fun observeRecentEvents(childId: String, limit: Long = 50): Flow<List<GeofenceEvent>>

    /** Fetch and decrypt a single event by id (for enriching a push notification). */
    suspend fun getEvent(childId: String, eventId: String): GeofenceEvent?

    /** Deletes every stored event for the child (parent "clear all" on the feed). */
    suspend fun clearEvents(childId: String): AppResult<Unit>
}
