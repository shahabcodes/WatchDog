package com.shahaabapps.shield.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.focus.FocusRepository
import com.shahaabapps.core.firebase.protection.PendingAppsRepository
import com.shahaabapps.core.firebase.protection.ProtectionRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.model.AppRule
import com.shahaabapps.core.model.FocusSettings
import com.shahaabapps.core.model.PendingApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Watches foreground app changes and blocks apps that violate their rule
 * (hard block, daily limit reached, or outside allowed hours).
 */
@AndroidEntryPoint
class EnforcementService : AccessibilityService() {

    @Inject lateinit var appRulesRepository: AppRulesRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var childRepository: ChildRepository
    @Inject lateinit var usageTracker: UsageTracker
    @Inject lateinit var protectionRepository: ProtectionRepository
    @Inject lateinit var pendingAppsRepository: PendingAppsRepository
    @Inject lateinit var focusRepository: FocusRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var focus: FocusSettings = FocusSettings()

    /** Apps that must keep working during a focus window (home, phone, system UI). */
    @Volatile
    private var focusExempt: Set<String> = emptySet()

    /** Detects newly installed apps so they can be auto-blocked (when enabled). */
    private val packageAddedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            if (!ProtectionLocalStore.isBlockNewApps(this@EnforcementService)) return
            val pkg = intent.data?.schemeSpecificPart ?: return
            if (pkg == this@EnforcementService.packageName) return
            handleNewApp(pkg)
        }
    }

    @Volatile
    private var rulesByPackage: Map<String, AppRule> = emptyMap()

    // Only enforce while the device is supervised (linked to a parent).
    @Volatile
    private var supervised: Boolean = false

    private var lastBlockedPackage: String? = null
    private var lastBlockAtMillis: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val childId = authRepository.currentUserIdOrNull() ?: return
        serviceScope.launch {
            appRulesRepository.observeAppRules(childId).collectLatest { rules ->
                rulesByPackage = rules.associateBy { it.packageName }
            }
        }
        serviceScope.launch {
            childRepository.observeChild(childId).collectLatest { child ->
                supervised = child?.isLinked == true
            }
        }
        serviceScope.launch {
            focusRepository.observe(childId).collectLatest { focus = it }
        }
        focusExempt = computeFocusExempt()
        serviceScope.launch {
            protectionRepository.observe(childId).collectLatest { settings ->
                ProtectionLocalStore.setBlocked(this@EnforcementService, settings.uninstallBlocked)
                ProtectionLocalStore.setBlockNewApps(this@EnforcementService, settings.blockNewApps)
                // Parent turned protection off → release device admin so the app
                // can be uninstalled again.
                if (!settings.uninstallBlocked && DeviceAdmin.isActive(this@EnforcementService)) {
                    DeviceAdmin.remove(this@EnforcementService)
                }
            }
        }

        runCatching {
            registerReceiver(
                packageAddedReceiver,
                IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") },
            )
        }
    }

    private fun handleNewApp(pkg: String) {
        val childId = authRepository.currentUserIdOrNull() ?: return
        val pm = packageManager
        // Only user-facing (launchable) apps.
        pm.getLaunchIntentForPackage(pkg) ?: return
        val label = runCatching {
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        }.getOrDefault(pkg)
        serviceScope.launch {
            appRulesRepository.saveAppRule(
                childId,
                AppRule(packageName = pkg, appLabel = label, blocked = true),
            )
            pendingAppsRepository.add(childId, PendingApp(pkg, label, System.currentTimeMillis()))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        // Uninstall protection runs independently of app-limit enforcement.
        guardUninstall(packageName, event.className?.toString())

        if (!supervised) return // device no longer supervised — no restrictions
        if (packageName == this.packageName) return

        // Focus windows (Bedtime / School) block everything except essentials.
        val focusReason = activeFocusReason()
        if (focusReason != null && packageName !in focusExempt) {
            blockApp(packageName, appLabelFor(packageName), focusReason)
            return
        }

        val rule = rulesByPackage[packageName] ?: return
        val usedMinutes = usageTracker.todayForegroundMinutes(packageName)
        val decision = RuleEvaluator.evaluate(rule, usedMinutes)
        if (decision.block) {
            blockApp(packageName, rule.appLabel.ifBlank { packageName }, decision.reason)
        }
    }

    /** Returns a child-facing reason when a focus window is active now, else null. */
    private fun activeFocusReason(): String? {
        val now = LocalDateTime.now()
        val minute = now.hour * 60 + now.minute
        val iso = now.dayOfWeek.value // 1=Mon..7=Sun
        return when {
            focus.bedtime.activeAt(minute, iso) -> "Bedtime — time to wind down 😴"
            focus.school.activeAt(minute, iso) -> "School time — stay focused 📚"
            else -> null
        }
    }

    private fun appLabelFor(packageName: String): String {
        rulesByPackage[packageName]?.appLabel?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
        }.getOrDefault(packageName)
    }

    private fun computeFocusExempt(): Set<String> {
        val exempt = mutableSetOf("android", "com.android.systemui", "com.android.phone")
        // Home launcher — the child can always reach the home screen.
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { exempt += it }
        }
        // Default dialer — emergency and normal calls must still work.
        runCatching {
            getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { exempt += it }
        }
        return exempt
    }

    private fun blockApp(packageName: String, label: String, reason: String?) {
        // Debounce repeated events for the same app.
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && now - lastBlockAtMillis < 1500) return
        lastBlockedPackage = packageName
        lastBlockAtMillis = now

        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(BlockActivity.EXTRA_APP_LABEL, label)
            putExtra(BlockActivity.EXTRA_REASON, reason)
        }
        startActivity(intent)
    }

    /**
     * When uninstall protection is on and device admin is active, bounce the user
     * out of the uninstall dialog or the "deactivate device admin" screen — unless
     * a parent has unlocked a removal window with the PIN.
     */
    private fun guardUninstall(packageName: String, className: String?) {
        if (!ProtectionLocalStore.isBlocked(this)) return
        if (!DeviceAdmin.isActive(this)) return
        if (ProtectionLocalStore.isUnlocked(this)) return
        if (ProtectionLocalStore.isUninstallScreen(packageName, className) ||
            ProtectionLocalStore.isAdminRemovalScreen(packageName, className)
        ) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(packageAddedReceiver) }
        serviceScope.cancel()
    }
}
