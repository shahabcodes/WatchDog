package com.shahaabapps.shield.feature.kid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.protection.PendingAppsRepository
import com.shahaabapps.core.firebase.request.RequestRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.firebase.usage.UsageRepository
import com.shahaabapps.core.model.RequestKind
import com.shahaabapps.core.model.RequestStatus
import com.shahaabapps.core.model.TimeRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One app the parent has a rule for, as the child sees it in "My apps". */
data class MyApp(
    val packageName: String,
    val label: String,
    val iconBase64: String?,
    /** A short line about the rule: "Always blocked", "1h a day · 8:00 AM – 11:00 AM", … */
    val detail: String,
    /** Blocked apps: a "use it supervised" ask is already waiting for the parent. */
    val asked: Boolean = false,
)

data class MyAppsState(
    val blocked: List<MyApp> = emptyList(),
    val restricted: List<MyApp> = emptyList(),
    val supervised: List<MyApp> = emptyList(),
)

@HiltViewModel
class MyAppsViewModel @Inject constructor(
    authRepository: AuthRepository,
    appRulesRepository: AppRulesRepository,
    pendingAppsRepository: PendingAppsRepository,
    usageRepository: UsageRepository,
    private val requestRepository: RequestRepository,
) : ViewModel() {

    private val childId: String? = authRepository.currentUserIdOrNull()

    val state: StateFlow<MyAppsState> = (
        if (childId == null) {
            flowOf(MyAppsState())
        } else {
            combine(
                appRulesRepository.observeInstalledApps(childId),
                appRulesRepository.observeAppRules(childId),
                pendingAppsRepository.observe(childId),
                usageRepository.observeUsage(childId, LocalDate.now().toString()),
                requestRepository.observe(childId, 30),
            ) { apps, rules, pending, usage, requests ->
                val byPkg = apps.associateBy { it.packageName }
                val asked = requests
                    .filter { it.status == RequestStatus.PENDING && it.kind == RequestKind.SUPERVISED_ACCESS }
                    .map { it.appPackage }.toSet()
                fun app(pkg: String, fallback: String, detail: String) = byPkg[pkg]?.let {
                    MyApp(pkg, it.label.ifBlank { fallback.ifBlank { pkg } }, it.icon, detail, pkg in asked)
                }
                val blocked = rules.filter { it.blocked }
                    .mapNotNull { app(it.packageName, it.appLabel, "Always blocked") }
                val restricted = rules.filter { !it.blocked && (it.dailyLimitMinutes != null || it.schedule.isNotEmpty()) }
                    .mapNotNull { r ->
                        val parts = buildList {
                            r.dailyLimitMinutes?.let { add("${usage[r.packageName] ?: 0}m of ${formatLimit(it)} today") }
                            if (r.schedule.isNotEmpty()) add("Open " + r.schedule.joinToString(", ") { "${clock(it.startMinuteOfDay)} – ${clock(it.endMinuteOfDay)}" })
                        }
                        app(r.packageName, r.appLabel, parts.joinToString(" · "))
                    }
                val ruled = rules.map { it.packageName }.toSet()
                val supervised = rules.filter { it.watched }
                    .mapNotNull { app(it.packageName, it.appLabel, "Your parent sees screenshots of it, ${often(it.watchEverySeconds)}") } +
                    pending.filter { it.packageName !in ruled }
                        .mapNotNull { app(it.packageName, it.label, "New app: your parent sees screenshots of it until they decide") }
                MyAppsState(
                    blocked = blocked.sortedBy { it.label.lowercase() },
                    restricted = restricted.sortedBy { it.label.lowercase() },
                    supervised = supervised.sortedBy { it.label.lowercase() },
                )
            }
        }
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyAppsState())

    /** Ask the parent to let this blocked app be used with supervision. */
    fun askSupervised(app: MyApp) {
        val id = childId ?: return
        viewModelScope.launch {
            runCatching {
                requestRepository.create(
                    id,
                    TimeRequest(kind = RequestKind.SUPERVISED_ACCESS, appPackage = app.packageName, appLabel = app.label),
                )
            }
        }
    }

    private companion object {
        fun formatLimit(min: Int): String = when {
            min % 60 == 0 -> "${min / 60}h"
            min > 60 -> "${min / 60}h ${min % 60}m"
            else -> "${min}m"
        }

        fun clock(minuteOfDay: Int): String {
            val h = (minuteOfDay / 60) % 24
            val m = minuteOfDay % 60
            val h12 = if (h % 12 == 0) 12 else h % 12
            return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
        }

        fun often(seconds: Int): String = when {
            seconds >= 300 -> "about every ${seconds / 60} minutes"
            seconds >= 60 -> "about once a minute"
            else -> "about ${60 / seconds} times a minute"
        }
    }
}
