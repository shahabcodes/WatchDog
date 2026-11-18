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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /** Which of the child's phones it came from ("" when the child has one phone). */
    val phone: String = "",
)

/** Feed of place arrival/departure alerts for the current child. */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val geofenceRepository: GeofenceRepository,
    private val requestRepository: RequestRepository,
    private val appRulesRepository: com.shahaabapps.core.firebase.rules.AppRulesRepository,
    private val pendingAppsRepository: com.shahaabapps.core.firebase.protection.PendingAppsRepository,
    private val kids: com.shahaabapps.core.firebase.child.KidDirectory,
    private val installGuardRepository: com.shahaabapps.core.firebase.installguard.InstallGuardRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    /** Every phone of this child (this one first): the feed shows all of them. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val phones: kotlinx.coroutines.flow.Flow<List<String>> = kotlinx.coroutines.flow.flow {
        emit(kids.phonesOf(childId))
        kids.kids.collect { map -> emit(listOf(childId) + map[childId]?.phones.orEmpty().filter { it != childId }) }
    }.distinctUntilChanged()

    private fun label(phone: String, all: List<String>): String =
        if (all.size < 2) "" else kids.labelOf(phone).ifBlank { "Phone ${all.indexOf(phone) + 1}" }

    /** The phone a request came from. */
    private fun phoneOf(request: TimeRequest): String = request.childId.ifBlank { childId }

    /** Deep-link target from a notification: which request, and a pre-chosen action. */
    val initialRequestId: String? = savedStateHandle.get<String>("requestId")?.ifBlank { null }
    val initialAction: String = savedStateHandle.get<String>("action") ?: "VIEW"

    /** The child's pending "asks" awaiting a parent decision. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pendingRequests: StateFlow<List<TimeRequest>> = phones
        .flatMapLatest { all ->
            combine(all.map { phone ->
                requestRepository.observe(phone, 30).map { list ->
                    list.filter { it.status == RequestStatus.PENDING }
                        .map { it.copy(childId = phone, phone = label(phone, all)) }
                }
            }) { lists -> lists.toList().flatten().sortedByDescending { it.createdAtMillis } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun respond(requestId: String, approved: Boolean, grantUntilMillis: Long = 0L) {
        val phone = pendingRequests.value.firstOrNull { it.id == requestId }?.childId?.ifBlank { null } ?: childId
        viewModelScope.launch { requestRepository.respond(phone, requestId, approved, grantUntilMillis) }
    }

    /**
     * Approve supervised access: the app becomes Supervised (unblocked, a screenshot
     * every [watchEverySeconds]); its limit and hours are kept. The rule is saved
     * before the approval, so Shield reopens the app with the new rule in place.
     */
    fun approveSupervised(request: TimeRequest, watchEverySeconds: Int) {
        val childId = phoneOf(request)
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
        val childId = phoneOf(request)
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

    /** Remove the app's allowed/blocked hours (its limit and access stay), then approve. */
    fun liftHours(request: TimeRequest) {
        val childId = phoneOf(request)
        viewModelScope.launch {
            val current = runCatching { appRulesRepository.observeAppRules(childId).first() }.getOrDefault(emptyList())
                .firstOrNull { it.packageName == request.appPackage }
            val saved = if (current != null) {
                appRulesRepository.saveAppRule(childId, current.copy(schedule = emptyList(), scheduleBlocks = false))
            } else {
                com.shahaabapps.core.common.AppResult.Success(Unit)
            }
            if (saved is com.shahaabapps.core.common.AppResult.Success) {
                requestRepository.respond(childId, request.id, true, 0L)
            }
        }
    }

    /**
     * Approves an ask to install a sideloaded app: the install guard stops blocking
     * it (added to the allowed list, or taken off the blocked one) on every phone.
     */
    fun allowInstall(request: TimeRequest) {
        val childId = phoneOf(request)
        val pkg = request.appPackage
        viewModelScope.launch {
            val guard = kotlinx.coroutines.withTimeoutOrNull(15_000) { installGuardRepository.observe(childId).first() }
                ?: return@launch
            val saved = when {
                pkg.isBlank() || !guard.blocks(pkg) -> com.shahaabapps.core.common.AppResult.Success(Unit)
                guard.allowlist -> installGuardRepository.setGuard(childId, guard.copy(packages = guard.packages + pkg))
                else -> installGuardRepository.setGuard(childId, guard.copy(packages = guard.packages - pkg))
            }
            if (saved is com.shahaabapps.core.common.AppResult.Success) {
                requestRepository.respond(childId, request.id, true, 0L)
            }
        }
    }

    // Join events with the current geofences so each alert shows the saved
    // place's name, and so an event whose place no longer exists (or never had
    // one) is marked unknown.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<AlertItem>> = phones.flatMapLatest { all ->
        // Places are shared by the child's phones, so this phone's list names them.
        combine(
            combine(all.map { phone ->
                geofenceRepository.observeRecentEvents(phone, 50L).map { list -> list.map { phone to it } }
            }) { lists -> lists.toList().flatten() },
            geofenceRepository.observeGeofences(childId),
        ) { events, geofences ->
            val byId = geofences.associateBy { it.id }
            events.sortedByDescending { it.second.timestampMillis }.take(80).map { (phone, e) ->
                val saved = byId[e.geofenceId]
                val name = saved?.name?.ifBlank { null } ?: e.geofenceName.ifBlank { null }
                AlertItem(event = e, placeName = name, known = saved != null || e.geofenceName.isNotBlank(), phone = label(phone, all))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Clears the whole notifications feed for this child (every phone). */
    fun clearAll() {
        viewModelScope.launch { kids.phonesOf(childId).forEach { geofenceRepository.clearEvents(it) } }
    }
}
