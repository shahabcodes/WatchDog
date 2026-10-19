package com.shahaabapps.shield.feature.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.firebase.usage.UsageRepository
import com.shahaabapps.shield.enforcement.ChildPermissions
import com.shahaabapps.shield.enforcement.UsageTracker
import com.shahaabapps.shield.usage.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ProtectedViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val authRepository: AuthRepository,
    private val appRulesRepository: AppRulesRepository,
    private val usageRepository: UsageRepository,
    private val childRepository: ChildRepository,
    private val installedAppsProvider: InstalledAppsProvider,
    private val usageTracker: UsageTracker,
) : ViewModel() {

    /** Reports the device's launchable apps, screen-time usage and granted permissions. */
    fun reportState() {
        val childId = authRepository.currentUserIdOrNull() ?: return
        viewModelScope.launch {
            runCatching { childRepository.heartbeat(childId, android.os.Build.MODEL) }
            runCatching { childRepository.updatePermissions(childId, ChildPermissions.snapshot(context)) }

            val apps = withContext(Dispatchers.IO) { installedAppsProvider.launchableApps() }
            appRulesRepository.reportInstalledApps(childId, apps)

            val usage = withContext(Dispatchers.IO) { usageTracker.todayUsageByPackage() }
            if (usage.isNotEmpty()) {
                usageRepository.reportUsage(childId, com.shahaabapps.shield.enforcement.ChildZone.today().toString(), usage)
            }
        }
    }
}
