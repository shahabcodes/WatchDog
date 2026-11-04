package com.shahaabapps.watchdog.feature.watch

import android.content.Context
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.watch.WatchShotRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Pulls a child's new watched-app screenshots down to this phone while [run] is
 * collected (the Activity tab or the screenshots screen is open). Shots are not
 * deleted from the server on download, so every parent gets them; they expire
 * there after WatchShotRepository.KEEP_DAYS.
 */
class WatchShotSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WatchShotRepository,
    private val authRepository: AuthRepository,
) {
    private val parentUid: String get() = authRepository.currentUserIdOrNull() ?: "unknown"

    /** Runs until cancelled; [onSaved] after each batch that stored something. */
    suspend fun run(childId: String, onSaved: () -> Unit) {
        val since = withContext(Dispatchers.IO) {
            LocalWatchStore.prune(context, parentUid, childId)
            LocalWatchStore.newestMillis(context, parentUid, childId)
        }
        repository.observeAfter(childId, since).collect { records ->
            var saved = false
            for (record in records) {
                val shot = repository.open(childId, record) ?: continue
                val stored = withContext(Dispatchers.IO) {
                    if (LocalWatchStore.has(context, parentUid, childId, shot.packageName, shot.capturedAtMillis)) {
                        false
                    } else {
                        LocalWatchStore.save(context, parentUid, childId, shot.packageName, shot.capturedAtMillis, shot.image)
                        true
                    }
                }
                saved = saved || stored
            }
            if (saved) onSaved()
        }
    }
}
