package com.shahaabapps.core.model

/**
 * A user profile, shared by both parent and child roles.
 *
 * @param id the Firebase Auth uid (parent) or child document id.
 * @param name display name.
 * @param photoUrl remote URL of the avatar in Firebase Storage, or null.
 * @param dateOfBirthMillis date of birth as epoch millis (UTC), or null if unset.
 * @param role whether this profile belongs to a parent or a child.
 */
data class UserProfile(
    val id: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val dateOfBirthMillis: Long? = null,
    val role: UserRole = UserRole.CHILD,
)

enum class UserRole {
    PARENT,
    CHILD,
}
