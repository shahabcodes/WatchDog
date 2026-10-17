package com.shahaabapps.core.firebase.child

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.model.ChildAccount
import com.shahaabapps.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Child account documents and the QR pairing handshake.
 *
 * Child side: creates/observes its own `children/{childId}` document (profile +
 * pairing code + linked parents). Parent side: links a scanned child and
 * observes all children linked to it.
 */
interface ChildRepository {

    /** Live child account for the given id, or null until it exists. */
    fun observeChild(childId: String): Flow<ChildAccount?>

    /**
     * Creates or updates the child profile, ensuring a pairing code exists.
     * Called by the child app after profile setup.
     */
    suspend fun saveChildProfile(childId: String, profile: UserProfile): AppResult<Unit>

    /** All children linked to the given parent (multi-child dashboard). */
    fun observeLinkedChildren(parentId: String): Flow<List<ChildAccount>>

    /** Children this uid can VIEW (read-only observer), newest query wins. */
    fun observeViewableChildren(viewerId: String): Flow<List<ChildAccount>>

    /**
     * Links the scanning parent to a child, validating the pairing code.
     * Runs as a transaction against `children/{childId}`.
     */
    suspend fun linkChild(childId: String, code: String, parentId: String): AppResult<Unit>

    /**
     * Child presence heartbeat: stamps `device.lastSeenMillis`, model and battery
     * level. The parent derives online/offline from how fresh this is.
     * [batteryPercent] of 0 means "unknown" and is not written.
     */
    suspend fun heartbeat(
        childId: String,
        model: String,
        batteryPercent: Int = 0,
        charging: Boolean = false,
        drainPerHour: Double = 0.0,
        temperatureTenthsC: Int = 0,
    ): AppResult<Unit>

    /** Child reports which special-access permissions it currently has granted. */
    suspend fun updatePermissions(childId: String, permissions: com.shahaabapps.core.model.DevicePermissions): AppResult<Unit>

    /** Child reports detailed battery history (hourly levels + last charge). */
    suspend fun updateBatteryStats(childId: String, stats: com.shahaabapps.core.model.BatteryStats): AppResult<Unit>

    /** A linked parent removes (unlinks) themselves from a child. */
    suspend fun unlinkChild(childId: String, parentId: String): AppResult<Unit>
}
