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
     * Creates or updates the child profile. Called by the child app after profile
     * setup. Pairing codes are issued separately by [startPairing].
     */
    suspend fun saveChildProfile(childId: String, profile: UserProfile): AppResult<Unit>

    /**
     * Gets a fresh single-use pairing code (valid ~10 minutes) from the server.
     * Called by the child device for itself, or by a linked parent with [childId]
     * to invite a co-parent.
     */
    suspend fun startPairing(childId: String? = null): AppResult<PairingCode>

    /** Parents waiting for approval on this child's phone (child side). */
    fun observeParentRequests(childId: String): Flow<List<ParentRequest>>

    /** The child device approves or denies a waiting parent. */
    suspend fun respondToParentRequest(parentId: String, approve: Boolean): AppResult<Unit>

    /** All children linked to the given parent (multi-child dashboard). */
    fun observeLinkedChildren(parentId: String): Flow<List<ChildAccount>>

    /**
     * Redeems a pairing code (server-side). Normally returns
     * [LinkOutcome.AwaitingApproval]: the child's phone must approve the parent.
     */
    suspend fun linkChild(code: String): AppResult<LinkOutcome>

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

    /** A linked parent removes (unlinks) themselves from a child (server-side). */
    suspend fun unlinkChild(childId: String): AppResult<Unit>
}

/** A server-issued pairing code and when it stops working. */
data class PairingCode(val code: String, val expiresAtMillis: Long)

/** A parent who redeemed this child's code and awaits approval on the child's phone. */
data class ParentRequest(val parentId: String, val email: String, val expiresAtMillis: Long)

/** Result of redeeming a pairing code. */
sealed interface LinkOutcome {
    val childId: String
    data class AwaitingApproval(override val childId: String) : LinkOutcome
    data class Linked(override val childId: String) : LinkOutcome
}
