package com.shahaabapps.watchdog.feature.kids

import com.shahaabapps.core.model.AppRule
import com.shahaabapps.core.model.ChildAccount
import com.shahaabapps.core.model.DeviceInfo
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.KidLink
import com.shahaabapps.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KidRulesTest {

    private fun rule(pkg: String, limit: Int? = null, at: Long = 0L, blocked: Boolean = false) =
        AppRule(packageName = pkg, appLabel = pkg, dailyLimitMinutes = limit, blocked = blocked, updatedAtMillis = at)

    private fun place(id: String, name: String = id) = Geofence(id = id, name = name)

    @Test
    fun `copy makes the target match the source`() {
        val plan = KidRules.copyPlan(
            sourceRules = listOf(rule("yt", 60), rule("tiktok", blocked = true)),
            targetRules = listOf(rule("yt", 30), rule("game", 10)),
            sourcePlaces = listOf(place("home"), place("school")),
            targetPlaces = listOf(place("home"), place("gym")),
        )
        assertEquals(listOf("yt", "tiktok"), plan.saveRules.map { it.packageName })
        assertEquals(listOf("game"), plan.deleteRules)
        assertEquals(listOf("school"), plan.savePlaces.map { it.id })
        assertEquals(listOf("gym"), plan.deletePlaces)
    }

    @Test
    fun `identical rules with different timestamps need no write`() {
        val plan = KidRules.copyPlan(listOf(rule("yt", 60, at = 5)), listOf(rule("yt", 60, at = 9)), emptyList(), emptyList())
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `preview counts only what the target loses or has replaced`() {
        val p = KidRules.preview(
            sourceRules = listOf(rule("yt", 60), rule("new")),
            targetRules = listOf(rule("yt", 30), rule("game")),
            sourcePlaces = listOf(place("home")),
            targetPlaces = listOf(place("home", "Home!"), place("gym")),
        )
        // yt replaced + game removed; "new" is only added.
        assertEquals(2, p.rulesChanged)
        assertEquals(2, p.placesChanged)
    }

    @Test
    fun `repair spreads the newest rule and missing places, deleting nothing`() {
        val plans = KidRules.repairPlan(
            rules = mapOf(
                "a" to listOf(rule("yt", 60, at = 10), rule("game", 5, at = 1)),
                "b" to listOf(rule("yt", 30, at = 20)),
            ),
            places = mapOf("a" to listOf(place("home")), "b" to emptyList()),
        )
        assertEquals(listOf("yt"), plans.getValue("a").saveRules.map { it.packageName })
        assertEquals(30, plans.getValue("a").saveRules.single().dailyLimitMinutes)
        assertEquals(setOf("game"), plans.getValue("b").saveRules.map { it.packageName }.toSet())
        assertEquals(listOf("home"), plans.getValue("b").savePlaces.map { it.id })
        assertTrue(plans.values.all { it.deleteRules.isEmpty() && it.deletePlaces.isEmpty() })
    }

    @Test
    fun `phones in sync need no repair`() {
        val plans = KidRules.repairPlan(
            rules = mapOf("a" to listOf(rule("yt", 60, at = 1)), "b" to listOf(rule("yt", 60, at = 2))),
            places = mapOf("a" to listOf(place("home")), "b" to listOf(place("home"))),
        )
        assertTrue(plans.isEmpty())
    }

    private fun phone(id: String, kid: KidLink? = null, model: String = "", name: String = "Usaid") =
        ChildAccount(childId = id, profile = UserProfile(name = name), device = DeviceInfo(model = model), kid = kid)

    @Test
    fun `phones of one kid group under the kid's name`() {
        val kid = KidLink(id = "k", name = "Usaid", phones = listOf("p1", "p2"))
        val kids = groupKids(listOf(phone("p1", kid, "Pixel 7"), phone("x", name = "Sara"), phone("p2", kid, "Tab A8")))
        assertEquals(listOf("k", "x"), kids.map { it.key })
        assertEquals(listOf("p1", "p2"), kids.first().phoneIds)
        assertEquals("Usaid", kids.first().name)
        assertTrue(kids.first().merged)
    }

    @Test
    fun `phone labels number two of the same model`() {
        val a = phone("a", model = "Pixel 7")
        val b = phone("b", model = "Pixel 7")
        val c = phone("c")
        val all = listOf(a, b, c)
        assertEquals("Pixel 7 1", phoneLabel(a, all))
        assertEquals("Pixel 7 2", phoneLabel(b, all))
        assertEquals("Phone 3", phoneLabel(c, all))
    }
}
