package com.shahaabapps.core.firebase.device

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.model.ChildLocation
import com.shahaabapps.core.model.RingCommand
import com.shahaabapps.core.model.TrailPoint
import com.shahaabapps.core.model.TravelStop
import kotlinx.coroutines.flow.Flow

/**
 * Live device state shared between the child (Shield) and the parent (Watch Dog).
 *
 * Firestore layout:
 *  - children/{childId}/live/location  — child writes, parent observes
 *  - children/{childId}/live/ring      — parent writes, child observes
 */
interface DeviceRepository {

    /** Child publishes its current location. */
    suspend fun updateLocation(childId: String, location: ChildLocation): AppResult<Unit>

    /** Parent observes the child's last-known location. */
    fun observeLocation(childId: String): Flow<ChildLocation?>

    /** Parent rings the child's device, optionally with a message. */
    suspend fun sendRing(childId: String, message: String): AppResult<Unit>

    /** Stop an active ring (parent cancels, or child snoozes/dismisses). */
    suspend fun clearRing(childId: String): AppResult<Unit>

    /** Child observes ring requests. */
    fun observeRing(childId: String): Flow<RingCommand?>

    /** Child records a completed stop in its travel history. */
    suspend fun addTravelStop(childId: String, stop: TravelStop): AppResult<Unit>

    /** Parent observes the child's recent stops, newest first. */
    fun observeTravelHistory(childId: String, limit: Long = 50): Flow<List<TravelStop>>

    /** Child appends a sampled point to the day's location trail. [date] is yyyy-MM-dd. */
    suspend fun appendTrailPoint(childId: String, date: String, point: TrailPoint): AppResult<Unit>

    /** Parent observes the continuous location trail for a given day (chronological). */
    fun observeTrail(childId: String, date: String): Flow<List<TrailPoint>>
}
