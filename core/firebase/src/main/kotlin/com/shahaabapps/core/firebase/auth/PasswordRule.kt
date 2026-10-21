package com.shahaabapps.core.firebase.auth

/**
 * The password rule for new and changed passwords. It matches the project's
 * server password policy (Identity Platform), so the app can say what's missing
 * before the server refuses. Existing passwords still sign in.
 */
object PasswordRule {
    const val MIN_LENGTH = 10

    /** Shown under password fields. */
    const val HINT = "At least $MIN_LENGTH characters, with a lowercase letter and a number."

    /** What [password] still needs, or null when it meets the rule. */
    fun problem(password: String): String? = when {
        password.length < MIN_LENGTH -> "Use at least $MIN_LENGTH characters."
        password.none { it.isLowerCase() } -> "Add a lowercase letter."
        password.none { it.isDigit() } -> "Add a number."
        else -> null
    }

    /** A clear message when the server refused a password for not meeting the policy. */
    fun friendlyError(error: Throwable): String? {
        val text = generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        return when {
            "PASSWORD_DOES_NOT_MEET_REQUIREMENTS" in text || "WEAK_PASSWORD" in text ->
                "That password is too weak. $HINT"
            else -> null
        }
    }
}
