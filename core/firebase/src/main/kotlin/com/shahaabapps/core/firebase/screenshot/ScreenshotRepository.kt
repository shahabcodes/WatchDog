package com.shahaabapps.core.firebase.screenshot

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.model.ScreenshotCommand
import kotlinx.coroutines.flow.Flow

/**
 * Remote screenshot handshake via Firestore (children/{childId}/commands).
 * The image is stored as a compressed base64 JPEG on the command document.
 */
interface ScreenshotRepository {

    /** Parent: create a screenshot request; returns the command id. */
    suspend fun requestScreenshot(childId: String): AppResult<String>

    /** Parent: watch a specific command for its result. */
    fun observeCommand(childId: String, commandId: String): Flow<ScreenshotCommand?>

    /** Parent: the most recent screenshot command (for the viewer). */
    fun observeLatest(childId: String): Flow<ScreenshotCommand?>

    /** Child: pending (REQUESTED) screenshot commands to fulfill. */
    fun observePendingRequests(childId: String): Flow<List<ScreenshotCommand>>

    /** Child: upload the captured image (base64 JPEG). */
    suspend fun submitResult(childId: String, commandId: String, imageBase64: String): AppResult<Unit>

    /** Child: report a capture failure. */
    suspend fun submitFailure(childId: String, commandId: String, error: String): AppResult<Unit>
}
