package com.shahaabapps.watchdog.feature.kids

import com.shahaabapps.core.model.AppRule
import com.shahaabapps.core.model.Geofence

/** The writes that make one phone's rules and places match another's. */
data class CopyPlan(
    val saveRules: List<AppRule> = emptyList(),
    val deleteRules: List<String> = emptyList(),
    val savePlaces: List<Geofence> = emptyList(),
    val deletePlaces: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = saveRules.isEmpty() && deleteRules.isEmpty() && savePlaces.isEmpty() && deletePlaces.isEmpty()
}

/** What a merge changes on the phones whose rules are replaced. */
data class MergePreview(val rulesChanged: Int = 0, val placesChanged: Int = 0)

/**
 * The comparisons behind merging phones and keeping them in line (pure, so they
 * are unit-tested). A rule is compared without its timestamp.
 */
object KidRules {

    private fun AppRule.content() = copy(updatedAtMillis = 0L)

    fun sameRule(a: AppRule, b: AppRule): Boolean = a.content() == b.content()

    /** Make [target] identical to [source]: changed and missing ones saved, extras removed. */
    fun copyPlan(
        sourceRules: List<AppRule>,
        targetRules: List<AppRule>,
        sourcePlaces: List<Geofence>,
        targetPlaces: List<Geofence>,
    ): CopyPlan {
        val target = targetRules.associateBy { it.packageName }
        val sourcePkgs = sourceRules.map { it.packageName }.toSet()
        val targetPlace = targetPlaces.associateBy { it.id }
        val sourceIds = sourcePlaces.map { it.id }.toSet()
        return CopyPlan(
            saveRules = sourceRules.filter { r -> target[r.packageName]?.let { !sameRule(it, r) } ?: true },
            deleteRules = targetRules.map { it.packageName }.filter { it !in sourcePkgs },
            savePlaces = sourcePlaces.filter { p -> targetPlace[p.id] != p },
            deletePlaces = targetPlaces.map { it.id }.filter { it !in sourceIds },
        )
    }

    /** How many of [target]'s rules and places a merge replaces or removes. */
    fun preview(
        sourceRules: List<AppRule>,
        targetRules: List<AppRule>,
        sourcePlaces: List<Geofence>,
        targetPlaces: List<Geofence>,
    ): MergePreview {
        val plan = copyPlan(sourceRules, targetRules, sourcePlaces, targetPlaces)
        val targetPkgs = targetRules.map { it.packageName }.toSet()
        val targetIds = targetPlaces.map { it.id }.toSet()
        return MergePreview(
            rulesChanged = plan.deleteRules.size + plan.saveRules.count { it.packageName in targetPkgs },
            placesChanged = plan.deletePlaces.size + plan.savePlaces.count { it.id in targetIds },
        )
    }

    /**
     * Repair for phones that drifted apart (a save that reached only one phone):
     * every rule goes to every phone, the most recently edited version winning; a
     * place missing on a phone is added. Nothing is deleted (a missing rule can't
     * be told apart from one not copied yet). Returns phone -> writes.
     */
    fun repairPlan(
        rules: Map<String, List<AppRule>>,
        places: Map<String, List<Geofence>>,
    ): Map<String, CopyPlan> {
        val newest = rules.values.flatten()
            .groupBy { it.packageName }
            .mapValues { (_, versions) -> versions.maxBy { it.updatedAtMillis } }
        val allPlaces = places.values.flatten().associateBy { it.id }
        return rules.keys.union(places.keys).associateWith { phone ->
            val mine = rules[phone].orEmpty().associateBy { it.packageName }
            val myPlaces = places[phone].orEmpty().map { it.id }.toSet()
            CopyPlan(
                saveRules = newest.values.filter { r -> mine[r.packageName]?.let { !sameRule(it, r) } ?: true },
                savePlaces = allPlaces.values.filter { it.id !in myPlaces },
            )
        }.filterValues { !it.isEmpty }
    }
}
