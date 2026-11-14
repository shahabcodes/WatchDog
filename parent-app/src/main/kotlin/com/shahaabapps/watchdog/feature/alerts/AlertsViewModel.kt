package com.shahaabapps.watchdog.feature.alerts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.firebase.request.RequestRepository
import com.shahaabapps.core.model.GeofenceEvent
import com.shahaabapps.core.model.RequestStatus
import com.shahaabapps.core.model.TimeRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One alert row: the raw event plus the resolved place name and whether that
 * place is a currently-saved location (so unknown places read differently).
 */
data class AlertItem(
    val event: GeofenceEvent,
    val placeName: String?,
    val known: Boolean,
)

/** Feed of place arrival/departure alerts for the current child. */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val geofenceRepository: GeofenceRepository,
    private val requestRepository: RequestRepository,
    private val appRulesRepository: com.shahaabapps.core.firebase.rules.AppRulesRepository,
    private val pendingAppsRepository: com.shahaabapps.core.firebase.protection.PendingAppsRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    /** Deep-link target from a notification: which request, and a pre-chosen action. */
    val initialRequestId: String? = savedStateHandle.get<String>("requestId")?.ifBlank { null }
    val initialAction: String = savedStateHandle.get<String>("action") ?: "VIEW"

    /** The child's pending "asks" awaiting a parent decision. */
    val pendingRequests: StateFlow<List<TimeRequest>> =
        requestRepository.observe(childId, 30)
            .map { list -> list.filter { it.status == RequestStatus.PENDING } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun respond(requestId: String, approved: Boolean, grantUntilMillis: Long = 0L) {
        viewModelScope.launch { requestRepository.respond(childId, requestId, approved, grantUntilMillis) }
    }

    /**
     * Approve supervised access: the app becomes Supervised (unblocked, a screenshot
     * every [watchEverySeconds]); its limit and hours are kept. The rule is saved
     * before the approval, so Shield reopens the app with the new rule in place.
     */
    fun approveSupervised(request: TimeRequest, watchEverySeconds: Int) {
        viewModelScope.launch {
            val current = runCatching { appRulesRepository.observeAppRules(childId).first() }.getOrDefault(emptyList())
                .firstOrNull { it.packageName == request.appPackage }
            val rule = (current ?: com.shahaabapps.core.model.AppRule(packageName = request.appPackage))
                .copy(appLabel = current?.appLabel?.ifBlank { null } ?: request.appLabel, blocked = false, watchEverySeconds = watchEverySeconds)
            val saved = appRulesRepository.saveAppRule(childId, rule)
            if (saved is com.shahaabapps.core.common.AppResult.Success) {
                // A new app waiting for review is decided now, too.
                runCatching { pendingAppsRepository.remove(childId, request.appPackage) }
                requestRepository.respond(childId, request.id, true, 0L)
            }
        }
    }

    /**
     * Allow the app for good: unblocked and no screenshots, keeping any daily limit
     * or hours. A new app waiting for review is cleared from review, which allows it.
     */
    fun approveAlways(request: TimeRequest) {
        viewModelScope.launch {
            val current = runCatching { appRulesRepository.observeAppRules(childId).first() }.getOrDefault(emptyList())
                .firstOrNull { it.packageName == request.appPackage }
            val saved = if (current != null) {
                appRulesRepository.saveAppRule(childId, current.copy(blocked = false, watchEverySeconds = 0))
            } else {
                com.shahaabapps.core.common.AppResult.Success(Unit)
            }
            if (saved is com.shahaabapps.core.common.AppResult.Success) {
                runCatching { pendingAppsRepository.remove(childId, request.appPackage) }
                requestRepository.respond(childId, request.id, true, 0L)
            }
        }
    }

    // Join events with the current geofences so each alert shows the saved
    // place's name, and so an event whose place no longer exists (or never had
    // one) is marked unknown.
    val items: StateFlow<List<AlertItem>> = combine(
        geofenceRepository.observeRecentEvents(childId, 50L),
        geofenceRepository.observeGeofences(childId),
    ) { events, geofences ->
        val byId = geofences.associateBy { it.id }
        events.map { e ->
            val saved = byId[e.geofenceId]
            val name = saved?.name?.ifBlank { null } ?: e.geofenceName.ifBlank { null }
            AlertItem(event = e, placeName = name, known = saved != null || e.geofenceName.isNotBlank())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Clears the whole notifications feed for this child. */
    fun clearAll() {
        viewModelScope.launch { geofenceRepository.clearEvents(childId) }
    }
}
