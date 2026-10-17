package com.shahaabapps.watchdog.ui

/**
 * A pending navigation into a specific child request, parsed from a notification
 * tap or an Approve/Reject action button.
 *
 * [action] is one of `VIEW`, `APPROVE` or `REJECT`; APPROVE/REJECT pre-open the
 * confirmation dialog for that request so the parent still confirms in-app.
 */
data class RequestDeepLink(
    val childId: String,
    val requestId: String,
    val action: String,
)
