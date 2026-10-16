package com.shahaabapps.shield.install

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.installguard.InstallGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Whether the parent has turned the install guard on (child needs storage access then). */
@HiltViewModel
class InstallGuardStatusViewModel @Inject constructor(
    authRepository: AuthRepository,
    installGuardRepository: InstallGuardRepository,
) : ViewModel() {
    val enabled: StateFlow<Boolean> =
        (authRepository.currentUserIdOrNull()
            ?.let { installGuardRepository.observe(it).map { g -> g.enabled } }
            ?: flowOf(false))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
