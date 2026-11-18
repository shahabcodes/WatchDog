package com.shahaabapps.watchdog.feature.kids

import com.shahaabapps.core.model.ChildAccount

/** One child as Watch Dog shows it: a name over one or more phones. */
data class KidView(
    /** The kid id, or the phone's id for a child with one phone. */
    val key: String,
    val name: String,
    val phones: List<ChildAccount>,
) {
    val merged: Boolean get() = phones.size > 1
    val phoneIds: List<String> get() = phones.map { it.childId }
    val photoUrl: String? get() = phones.firstNotNullOfOrNull { it.profile.photoUrl }
}

/** Groups phones of the same child (in list order); a phone on its own is its own child. */
fun groupKids(children: List<ChildAccount>): List<KidView> =
    children.groupBy { it.kidKey }.map { (key, phones) ->
        val name = phones.first().kid?.name?.ifBlank { null }
            ?: phones.first().profile.name.ifBlank { "Child" }
        KidView(key = key, name = name, phones = phones)
    }

/** A short name for one of a child's phones: its model, else "Phone 2". */
fun phoneLabel(phone: ChildAccount, siblings: List<ChildAccount>): String {
    val model = phone.device.model.trim()
    if (model.isNotEmpty()) {
        // Two of the same model: number them.
        val same = siblings.filter { it.device.model.trim() == model }
        return if (same.size > 1) "$model ${same.indexOf(phone) + 1}" else model
    }
    return "Phone ${siblings.indexOf(phone) + 1}"
}
