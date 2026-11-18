package com.shahaabapps.core.firebase.child

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.Diag
import com.google.firebase.firestore.FirebaseFirestore
import com.shahaabapps.core.model.KidLink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which phones belong to the same child. Filled from the child documents the app
 * already reads (FirebaseChildRepository), so the shared-rule writes in the
 * repositories know every phone to save to. A phone not yet seen is looked up
 * once. Phone labels (their model, decrypted on this phone) are remembered
 * locally so notifications can say which phone asked.
 */
@Singleton
class KidDirectory @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kid_directory", Context.MODE_PRIVATE)
    private val _kids = MutableStateFlow<Map<String, KidLink?>>(emptyMap())

    /** childId -> its kid (null = known to be on its own). */
    val kids: StateFlow<Map<String, KidLink?>> = _kids.asStateFlow()

    /** Records what a child document says about its kid. */
    fun record(childId: String, kid: KidLink?) {
        _kids.update { if (it.containsKey(childId) && it[childId] == kid) it else it + (childId to kid) }
    }

    /** Remembers a phone's label (its model) for notifications. */
    fun rememberLabel(childId: String, label: String) {
        if (label.isBlank() || prefs.getString(childId, null) == label) return
        prefs.edit().putString(childId, label.take(60)).apply()
    }

    fun labelOf(childId: String): String = prefs.getString(childId, null).orEmpty()

    /** The kid [childId] belongs to, if cached. */
    fun kidOf(childId: String): KidLink? = _kids.value[childId]

    /**
     * Every phone a shared rule for [childId] goes to: [childId] first, then the
     * kid's other phones. Looks the phone up once when it hasn't been seen.
     */
    suspend fun phonesOf(childId: String): List<String> {
        val kid = if (_kids.value.containsKey(childId)) {
            _kids.value[childId]
        } else {
            runCatching {
                val snap = firestore.collection("children").document(childId).get().await()
                parseKid(snap).also { record(childId, it) }
            }.getOrNull()
        }
        val others = kid?.phones.orEmpty().filter { it != childId && it.isNotBlank() }
        return listOf(childId) + others.distinct()
    }

    /** The kid's other phones (not [childId]). */
    suspend fun siblingsOf(childId: String): List<String> = phonesOf(childId).drop(1)

    /**
     * Runs a shared-rule write [op] on [childId], then (when [shared]) on the kid's
     * other phones. The result is [childId]'s; a sibling that fails is only logged
     * (Watch Dog's repair check brings it back in line later).
     */
    suspend fun <T> fanOut(childId: String, shared: Boolean, op: suspend (String) -> AppResult<T>): AppResult<T> {
        val first = op(childId)
        if (!shared || first !is AppResult.Success) return first
        siblingsOf(childId).forEach { id ->
            val r = runCatching { op(id) }.getOrElse { AppResult.Failure(it) }
            if (r is AppResult.Failure) Diag.warn("kid", "shared write to $id failed", r.error)
        }
        return first
    }

    companion object {
        /** Reads the server-written `kid` map from a child document. */
        fun parseKid(snap: DocumentSnapshot): KidLink? {
            @Suppress("UNCHECKED_CAST")
            val m = snap.get("kid") as? Map<String, Any?> ?: return null
            val id = m["id"] as? String ?: return null
            val phones = (m["phones"] as? List<*>).orEmpty().filterIsInstance<String>()
            if (id.isBlank() || phones.size < 2) return null
            return KidLink(id = id, name = m["name"] as? String ?: "", phones = phones)
        }
    }
}
