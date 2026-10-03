package com.shahaabapps.core.firebase.screenshot

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.ScreenshotCommand
import com.shahaabapps.core.model.ScreenshotStatus
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseScreenshotRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ScreenshotRepository {

    override suspend fun requestScreenshot(childId: String): AppResult<String> =
        runCatchingResult {
            val doc = commands(childId).document()
            val data = mapOf(
                "type" to TYPE_SCREENSHOT,
                "status" to ScreenshotStatus.REQUESTED.name,
                "requestedAtMillis" to System.currentTimeMillis(),
            )
            doc.set(data).await()
            doc.id
        }

    override fun observeCommand(childId: String, commandId: String): Flow<ScreenshotCommand?> =
        callbackFlow {
            val registration = commands(childId).document(commandId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error); return@addSnapshotListener
                    }
                    trySend(snapshot?.takeIf { it.exists() }?.toCommand())
                }
            awaitClose { registration.remove() }
        }

    override fun observeLatest(childId: String): Flow<ScreenshotCommand?> = callbackFlow {
        val registration = commands(childId)
            .whereEqualTo("type", TYPE_SCREENSHOT)
            .orderBy("requestedAtMillis", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error); return@addSnapshotListener
                }
                trySend(snapshot?.documents?.firstOrNull()?.toCommand())
            }
        awaitClose { registration.remove() }
    }

    override fun observePendingRequests(childId: String): Flow<List<ScreenshotCommand>> =
        callbackFlow {
            val registration = commands(childId)
                .whereEqualTo("type", TYPE_SCREENSHOT)
                .whereEqualTo("status", ScreenshotStatus.REQUESTED.name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error); return@addSnapshotListener
                    }
                    trySend(snapshot?.documents?.map { it.toCommand() } ?: emptyList())
                }
            awaitClose { registration.remove() }
        }

    override suspend fun submitResult(
        childId: String,
        commandId: String,
        imageBase64: String,
    ): AppResult<Unit> = runCatchingResult {
        commands(childId).document(commandId).update(
            mapOf(
                "status" to ScreenshotStatus.CAPTURED.name,
                "imageBase64" to imageBase64,
                "completedAtMillis" to System.currentTimeMillis(),
            ),
        ).await()
    }

    override suspend fun submitFailure(
        childId: String,
        commandId: String,
        error: String,
    ): AppResult<Unit> = runCatchingResult {
        commands(childId).document(commandId).update(
            mapOf(
                "status" to ScreenshotStatus.FAILED.name,
                "error" to error,
                "completedAtMillis" to System.currentTimeMillis(),
            ),
        ).await()
    }

    private fun commands(childId: String) =
        firestore.collection(CHILDREN).document(childId).collection(COMMANDS)

    private fun DocumentSnapshot.toCommand() = ScreenshotCommand(
        id = id,
        status = getString("status")?.let { runCatching { ScreenshotStatus.valueOf(it) }.getOrNull() }
            ?: ScreenshotStatus.REQUESTED,
        requestedAtMillis = getLong("requestedAtMillis") ?: 0L,
        completedAtMillis = getLong("completedAtMillis") ?: 0L,
        imageBase64 = getString("imageBase64"),
        error = getString("error"),
    )

    private companion object {
        const val CHILDREN = "children"
        const val COMMANDS = "commands"
        const val TYPE_SCREENSHOT = "screenshot"
    }
}
