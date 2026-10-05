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

    /**
     * Links the scanning parent to a child, validating the pairing code.
     * Runs as a transaction against `children/{childId}`.
     */
    suspend fun linkChild(childId: String, code: String, parentId: String): AppResult<Unit>

    /**
     * Child presence heartbeat: stamps `device.lastSeenMillis` (and model). The
     * parent derives online/offline from how fresh this is.
     */
    suspend fun heartbeat(childId: String, model: String): AppResult<Unit>

    /** A linked parent removes (unlinks) themselves from a child. */
    suspend fun unlinkChild(childId: String, parentId: String): AppResult<Unit>
}
