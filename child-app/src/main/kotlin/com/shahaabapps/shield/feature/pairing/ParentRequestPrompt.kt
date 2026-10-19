package com.shahaabapps.shield.feature.pairing

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.child.ParentRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Parents who redeemed this phone's pairing code wait here for approval. Nothing
 * is linked (and no data key is shared) until the person holding this phone taps
 * Approve, so a leaked or overheard code alone can't connect a stranger.
 */
@HiltViewModel
class ParentRequestViewModel @Inject constructor(
    private val childRepository: ChildRepository,
    authRepository: com.shahaabapps.core.firebase.auth.AuthRepository,
) : ViewModel() {

    private val childId = authRepository.currentUserIdOrNull()

    /** The oldest unexpired request, or null. */
    val pending: StateFlow<ParentRequest?> =
        (if (childId == null) flowOf(emptyList()) else childRepository.observeParentRequests(childId))
            .map { list -> list.sortedBy { it.expiresAtMillis }.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun respond(request: ParentRequest, approve: Boolean) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            when (val r = childRepository.respondToParentRequest(request.parentId, approve)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> _error.value = r.error.message ?: "Couldn't answer. Try again."
            }
            _busy.value = false
        }
    }
}

/** Shows "Link <email> as a parent?" while a parent request is waiting. */
@Composable
fun ParentRequestPrompt(viewModel: ParentRequestViewModel = hiltViewModel()) {
    val request by viewModel.pending.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val r = request ?: return
    val who = r.email.ifBlank { "A parent account" }
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Connect a parent?") },
        text = {
            Text(
                "$who wants to supervise this phone. They will see its location and activity " +
                    "and can set limits.\n\nOnly approve if you know this is your parent or guardian." +
                    (error?.let { "\n\n$it" } ?: ""),
            )
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { viewModel.respond(r, approve = true) }) { Text("Approve") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { viewModel.respond(r, approve = false) }) { Text("Deny") }
        },
    )
}
