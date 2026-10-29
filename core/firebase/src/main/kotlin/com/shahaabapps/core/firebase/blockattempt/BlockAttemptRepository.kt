package com.shahaabapps.core.firebase.blockattempt

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.model.BlockAttempt

/**
 * The child's attempts to open blocked apps, for the parent's alert.
 *
 * Firestore layout: children/{childId}/blockAttempts/{eventId}, holding only
 * {enc, e, atMillis}: which app and why is sealed, so the push stays content-free.
 */
interface BlockAttemptRepository {

    /** Child records an attempt. Fails when it can't be sealed (never written in plaintext). */
    suspend fun report(childId: String, attempt: BlockAttempt): AppResult<Unit>

    /** Fetch and decrypt one attempt (for the parent's notification); null if missing. */
    suspend fun getEvent(childId: String, eventId: String): BlockAttempt?

    /** Parent deletes attempts recorded before [cutoffMillis] (keeps the collection small). */
    suspend fun deleteOlderThan(childId: String, cutoffMillis: Long): AppResult<Unit>
}
