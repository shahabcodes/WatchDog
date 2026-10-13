package com.shahaabapps.shield.feature.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.request.RequestRepository
import com.shahaabapps.core.model.RequestKind
import com.shahaabapps.core.model.RequestStatus
import com.shahaabapps.core.model.TimeRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The child's "ask my parent" requests (create, observe, withdraw). */
@HiltViewModel
class AskViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val requestRepository: RequestRepository,
) : ViewModel() {

    private val childId: String? = authRepository.currentUserIdOrNull()

    val requests: StateFlow<List<TimeRequest>> =
        (childId?.let { requestRepository.observe(it, 30) } ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The newest still-pending ask, if any (drives the "waiting" state). */
    val pending: StateFlow<TimeRequest?> = MutableStateFlow<TimeRequest?>(null).also { flow ->
        viewModelScope.launch {
            requests.collect { list -> flow.value = list.firstOrNull { it.status == RequestStatus.PENDING } }
        }
    }

    fun askMoreTime(minutes: Int = 30) {
        val id = childId ?: return
        viewModelScope.launch {
            requestRepository.create(id, TimeRequest(kind = RequestKind.MORE_TIME, minutes = minutes))
        }
    }

    fun cancel(requestId: String) {
        val id = childId ?: return
        viewModelScope.launch { requestRepository.cancel(id, requestId) }
    }
}
