package com.shahaabapps.watchdog.feature.kids

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.Diag
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.focus.FocusRepository
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.firebase.installguard.InstallGuardRepository
import com.shahaabapps.core.firebase.protection.ProtectionRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.model.AppRule
import com.shahaabapps.core.model.Geofence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merging phones into one child, and keeping a child's phones in line. Every
 * write here is to one phone only (shared = false): the copy decides each phone's
 * content itself.
 */
@Singleton
class KidSync @Inject constructor(
    private val childRepository: ChildRepository,
    private val appRules: AppRulesRepository,
    private val geofences: GeofenceRepository,
    private val focus: FocusRepository,
    private val installGuard: InstallGuardRepository,
    private val protection: ProtectionRepository,
) {
    private val lastRepair = mutableMapOf<String, Long>()

    private suspend fun <T> Flow<T>.read(): T = withTimeout(15_000) { first() }
    private suspend fun rulesOf(id: String): List<AppRule> = appRules.observeAppRules(id).read()
    private suspend fun placesOf(id: String): List<Geofence> = geofences.observeGeofences(id).read()

    /** What merging changes on [others] when [keepFrom]'s rules are kept. */
    suspend fun preview(keepFrom: String, others: List<String>): MergePreview {
        val rules = rulesOf(keepFrom)
        val places = placesOf(keepFrom)
        return others.map { KidRules.preview(rules, rulesOf(it), places, placesOf(it)) }
            .fold(MergePreview()) { a, b -> MergePreview(a.rulesChanged + b.rulesChanged, a.placesChanged + b.placesChanged) }
    }

    /**
     * Joins [keepFrom] and [others] into one child named [name]: [others] first
     * get [keepFrom]'s rules, places, bedtime/school, install policy and new-app
     * policy, then the server groups them. Returns the kid id.
     */
    suspend fun merge(keepFrom: String, others: List<String>, name: String): AppResult<String> {
        val copied = runCatchingResult { others.forEach { copyAll(keepFrom, it) } }
        if (copied is AppResult.Failure) return copied
        return childRepository.mergeChildren(listOf(keepFrom) + others, name)
    }

    private suspend fun copyAll(from: String, to: String) {
        val plan = KidRules.copyPlan(rulesOf(from), rulesOf(to), placesOf(from), placesOf(to))
        apply(to, plan, deleting = true)
        focus.save(to, focus.observe(from).read(), shared = false).orThrow()
        installGuard.setGuard(to, installGuard.observe(from).read(), shared = false).orThrow()
        val keep = protection.observe(from).read()
        val mine = protection.observe(to).read()
        protection.save(
            to, mine.uninstallBlocked, keep.blockNewApps, mine.lockSettings, mine.multiWindowBlock,
            newPin = null, newAppsBlocked = keep.newAppsBlocked,
        ).orThrow()
    }

    private suspend fun apply(phone: String, plan: CopyPlan, deleting: Boolean) {
        plan.saveRules.forEach { appRules.saveAppRule(phone, it, shared = false).orThrow() }
        plan.savePlaces.forEach { geofences.saveGeofence(phone, it, shared = false).orThrow() }
        if (deleting) {
            plan.deleteRules.forEach { appRules.deleteAppRule(phone, it, shared = false).orThrow() }
            plan.deletePlaces.forEach { geofences.deleteGeofence(phone, it, shared = false).orThrow() }
        }
    }

    /**
     * Brings [phones] (one child's) back in line when a save reached only some of
     * them. At most every 10 minutes per child; quiet on failure.
     */
    suspend fun repair(kidId: String, phones: List<String>) {
        if (phones.size < 2) return
        val now = System.currentTimeMillis()
        if (now - (lastRepair[kidId] ?: 0L) < 10 * 60_000L) return
        lastRepair[kidId] = now
        runCatching {
            val plans = KidRules.repairPlan(
                rules = phones.associateWith { rulesOf(it) },
                places = phones.associateWith { placesOf(it) },
            )
            plans.forEach { (phone, plan) ->
                Diag.info("kid", "repair $phone rules=${plan.saveRules.size} places=${plan.savePlaces.size}")
                apply(phone, plan, deleting = false)
            }
        }.onFailure { Diag.warn("kid", "repair failed", it) }
    }

    private fun AppResult<*>.orThrow() {
        if (this is AppResult.Failure) throw error
    }
}
