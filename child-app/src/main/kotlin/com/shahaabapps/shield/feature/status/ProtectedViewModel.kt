package com.shahaabapps.shield.feature.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.shield.usage.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ProtectedViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appRulesRepository: AppRulesRepository,
    private val installedAppsProvider: InstalledAppsProvider,
) : ViewModel() {

    /** Reports the device's launchable apps so the parent can set rules. */
    fun reportInstalledApps() {
        val childId = authRepository.currentUserIdOrNull() ?: return
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { installedAppsProvider.launchableApps() }
            appRulesRepository.reportInstalledApps(childId, apps)
        }
    }
}
