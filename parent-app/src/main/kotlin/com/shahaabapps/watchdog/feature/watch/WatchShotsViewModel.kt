package com.shahaabapps.watchdog.feature.watch

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.model.InstalledApp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One visit to a watched app: its captures, newest first. */
data class WatchSession(
    val packageName: String,
    val entries: List<WatchEntry>,
) {
    val startMillis: Long get() = entries.last().capturedAtMillis
    val endMillis: Long get() = entries.first().capturedAtMillis
}

data class WatchShotsUiState(
    val sessions: List<WatchSession> = emptyList(),
    /** Apps with screenshots on this phone, for the filter chips. */
    val apps: List<String> = emptyList(),
    val selectedApp: String? = null,
    val appsByPackage: Map<String, InstalledApp> = emptyMap(),
)

@HiltViewModel
class WatchShotsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    appRulesRepository: AppRulesRepository,
    watchShotSync: WatchShotSync,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])
    private val parentUid: String get() = authRepository.currentUserIdOrNull() ?: "unknown"

    private val entries = MutableStateFlow<List<WatchEntry>>(emptyList())
    private val selected = MutableStateFlow(savedStateHandle.get<String>("app")?.ifBlank { null })

    val uiState: StateFlow<WatchShotsUiState> = combine(
        entries,
        selected,
        appRulesRepository.observeInstalledApps(childId),
    ) { all, app, installed ->
        val shown = if (app == null) all else all.filter { it.packageName == app }
        WatchShotsUiState(
            sessions = sessionsOf(shown),
            apps = all.map { it.packageName }.distinct(),
            selectedApp = app,
            appsByPackage = installed.associateBy { it.packageName },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchShotsUiState(selectedApp = selected.value))

    init {
        refresh()
        viewModelScope.launch { watchShotSync.run(childId) { refresh() } }
    }

    fun select(app: String?) { selected.value = app }

    fun delete(entry: WatchEntry) = delete(listOf(entry))

    /** Deletes these captures from this phone (they won't download again). */
    fun delete(toDelete: Collection<WatchEntry>) {
        if (toDelete.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            LocalWatchStore.delete(toDelete, context, parentUid, childId)
            entries.value = LocalWatchStore.list(context, parentUid, childId)
        }
    }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            entries.value = LocalWatchStore.list(context, parentUid, childId)
        }
    }

    private companion object {
        /** Captures of one app this close together belong to the same visit. */
        const val SESSION_GAP_MS = 10 * 60_000L

        fun sessionsOf(entries: List<WatchEntry>): List<WatchSession> {
            val sessions = mutableListOf<WatchSession>()
            var current = mutableListOf<WatchEntry>()
            for (e in entries) { // newest first
                val last = current.lastOrNull()
                if (last != null && (last.packageName != e.packageName || last.capturedAtMillis - e.capturedAtMillis > SESSION_GAP_MS)) {
                    sessions += WatchSession(last.packageName, current)
                    current = mutableListOf()
                }
                current += e
            }
            current.lastOrNull()?.let { sessions += WatchSession(it.packageName, current) }
            return sessions
        }
    }
}
