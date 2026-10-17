package com.shahaabapps.shield.enforcement

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.telecom.TelecomManager
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.focus.FocusRepository
import com.shahaabapps.core.firebase.protection.PendingAppsRepository
import com.shahaabapps.core.firebase.protection.ProtectionRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.firebase.screenshot.ScreenshotRepository
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
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.concurrent.Executors
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
    @Inject lateinit var screenshotRepository: ScreenshotRepository
    @Inject lateinit var installGuardRepository: com.shahaabapps.core.firebase.installguard.InstallGuardRepository

    @Volatile private var installGuard: com.shahaabapps.core.model.InstallGuard = com.shahaabapps.core.model.InstallGuard()
    /** Cache of whether a package was sideloaded (not from Play Store). */
    private val sideloadedCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private var lastScreenshotNonce = 0L

    @Volatile
    private var focus: FocusSettings = FocusSettings()

    /** Apps that must keep working during a focus window (home, phone, system UI). */
    @Volatile
    private var focusExempt: Set<String> = emptySet()

    /** Detects newly installed apps so they can be auto-blocked (when enabled). */
    private val packageAddedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
            val pkg = intent.data?.schemeSpecificPart ?: return
            if (pkg == this@EnforcementService.packageName) return
            // Re-evaluate the install source on (re)install so the install guard's
            // launch-time block reflects where this copy actually came from.
            sideloadedCache.remove(pkg)
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            if (!ProtectionLocalStore.isBlockNewApps(this@EnforcementService)) return
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
        instance = this
        // Watchdog: the accessibility service is the component the OS keeps alive
        // most reliably, so use it to keep location flowing — (re)schedule the
        // backstop workers and best-effort restart the (OEM-killable) location
        // foreground service.
        runCatching { com.shahaabapps.shield.location.LocationSyncWorker.schedule(this) }
        runCatching { com.shahaabapps.shield.location.LocationFlushWorker.schedulePeriodic(this) }
        runCatching { com.shahaabapps.shield.location.GeofenceSyncService.start(this) }
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
        serviceScope.launch {
            installGuardRepository.observe(childId).collectLatest { installGuard = it }
        }
        // Parent screenshot requests — captured silently via the accessibility
        // screenshot API (no MediaProjection, no on-screen indicator). API 30+.
        // Act on any request not yet handled (persisted) that is recent, so the
        // first tap works even if it arrived before this listener attached, while
        // stale requests aren't replayed on restart.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lastScreenshotNonce = getSharedPreferences("screenshot_state", Context.MODE_PRIVATE)
                .getLong("last_nonce", 0L)
            // The instant path is an FCM data message (see ShieldMessagingService);
            // this Firestore listener is the fallback when a push is delayed/undelivered.
            serviceScope.launch {
                screenshotRepository.observeCaptureRequests(childId).collectLatest { nonce ->
                    maybeCapture(nonce)
                }
            }
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
        val eventText = event.text.joinToString(" ").ifBlank { null }
        guardUninstall(packageName, event.className?.toString(), eventText)

        if (!supervised) return // device no longer supervised — no restrictions
        if (packageName == this.packageName) return

        // Focus windows (Bedtime / School) block everything except essentials
        // and the apps the parent allowlisted for the active window.
        val focusReason = activeFocusReason()
        if (focusReason != null && packageName !in focusExempt && packageName !in activeFocusAllowed()) {
            blockApp(packageName, appLabelFor(packageName), focusReason)
            return
        }

        // Install guard: block a sideloaded app the parent's policy disallows, even
        // if it has no app-rule and the APK quarantine didn't catch the download.
        if (installGuard.blocks(packageName) && isSideloaded(packageName)) {
            blockApp(
                packageName,
                appLabelFor(packageName),
                "This app isn't allowed to be installed from outside the Play Store.",
            )
            return
        }

        val rule = rulesByPackage[packageName] ?: return
        val usedMinutes = usageTracker.todayForegroundMinutes(packageName)
        val decision = RuleEvaluator.evaluate(rule, usedMinutes)
        if (decision.block) {
            blockApp(packageName, rule.appLabel.ifBlank { packageName }, decision.reason)
        }
    }

    /** True when [pkg] was installed from somewhere other than the Play Store. */
    private fun isSideloaded(pkg: String): Boolean = sideloadedCache.getOrPut(pkg) {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(pkg).installingPackageName
            } else {
                @Suppress("DEPRECATION") packageManager.getInstallerPackageName(pkg)
            }
        }.getOrNull()
        installer != "com.android.vending"
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

    /** Packages the parent allowlisted for whichever focus window is active now. */
    private fun activeFocusAllowed(): Set<String> {
        val now = LocalDateTime.now()
        val minute = now.hour * 60 + now.minute
        val iso = now.dayOfWeek.value
        val allowed = mutableSetOf<String>()
        if (focus.bedtime.activeAt(minute, iso)) allowed += focus.bedtime.allowedPackages
        if (focus.school.activeAt(minute, iso)) allowed += focus.school.allowedPackages
        return allowed
    }

    private fun appLabelFor(packageName: String): String {
        rulesByPackage[packageName]?.appLabel?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
        }.getOrDefault(packageName)
    }

    /**
     * Triggered by a parent screenshot request — from an FCM data push (instant, even
     * when dozing) or the Firestore listener (fallback). De-dupes on the request nonce
     * (persisted) so the two paths never double-capture the same request, and ignores
     * stale requests. Safe to call from any thread.
     */
    @Synchronized
    fun onRemoteCaptureCommand(nonce: Long) {
        maybeCapture(nonce)
    }

    @Synchronized
    private fun maybeCapture(nonce: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val childId = authRepository.currentUserIdOrNull() ?: return
        val recent = System.currentTimeMillis() - nonce < REQUEST_FRESH_MS
        if (nonce > lastScreenshotNonce && recent) {
            lastScreenshotNonce = nonce
            getSharedPreferences("screenshot_state", Context.MODE_PRIVATE)
                .edit().putLong("last_nonce", nonce).apply()
            captureScreenshot(childId)
        }
    }

    /**
     * Captures the screen via the accessibility screenshot API (API 30+) and uploads
     * it for the parent. No MediaProjection, no consent dialog, no capture indicator —
     * it reuses the already-granted accessibility permission.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureScreenshot(childId: String) {
        serviceScope.launch { screenshotRepository.reportStatus(childId, "capturing") }
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val bytes = runCatching { result.toImageBytes() }.getOrNull()
                        result.hardwareBuffer.close()
                        serviceScope.launch {
                            if (bytes == null) {
                                screenshotRepository.reportStatus(childId, "decode failed")
                            } else {
                                when (val r = screenshotRepository.uploadScreenshot(childId, bytes)) {
                                    is com.shahaabapps.core.common.AppResult.Success ->
                                        screenshotRepository.reportStatus(childId, "ok ${bytes.size}b")
                                    is com.shahaabapps.core.common.AppResult.Failure ->
                                        screenshotRepository.reportStatus(childId, "upload failed: ${r.error.message}")
                                }
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        serviceScope.launch { screenshotRepository.reportStatus(childId, "takeScreenshot failed: $errorCode") }
                    }
                },
            )
        }.onFailure {
            serviceScope.launch { screenshotRepository.reportStatus(childId, "takeScreenshot threw: ${it.message}") }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ScreenshotResult.toImageBytes(): ByteArray? {
        val hardware = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace) ?: return null
        val software = hardware.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        val maxW = 1000
        val scaled = if (software.width > maxW) {
            val ratio = maxW.toFloat() / software.width
            Bitmap.createScaledBitmap(software, maxW, (software.height * ratio).toInt(), true)
        } else {
            software
        }
        // JPEG (not PNG) keeps the upload/download small (~100KB vs ~600KB) so the
        // screenshot reaches the parent quickly.
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
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
            putExtra(BlockActivity.EXTRA_APP_PACKAGE, packageName)
            putExtra(BlockActivity.EXTRA_REASON, reason)
        }
        startActivity(intent)
    }

    /**
     * When uninstall protection is on and device admin is active, bounce the user
     * out of the uninstall dialog or the "deactivate device admin" screen — unless
     * a parent has unlocked a removal window with the PIN.
     */
    private fun guardUninstall(packageName: String, className: String?, text: String?) {
        if (!ProtectionLocalStore.isBlocked(this)) return
        if (!DeviceAdmin.isActive(this)) return
        if (ProtectionLocalStore.isUnlocked(this)) return
        if (ProtectionLocalStore.isUninstallScreen(packageName, className, text, ownAppLabel) ||
            ProtectionLocalStore.isAdminRemovalScreen(packageName, className, text)
        ) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    /** Shield's own launcher label — used to scope the uninstall guard to this app. */
    private val ownAppLabel: String by lazy {
        runCatching { applicationInfo.loadLabel(packageManager).toString() }.getOrDefault("")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        runCatching { unregisterReceiver(packageAddedReceiver) }
        runCatching { screenshotExecutor.shutdown() }
        serviceScope.cancel()
    }

    companion object {
        /** The running accessibility service, so the FCM push can trigger a capture. */
        @Volatile
        var instance: EnforcementService? = null
            private set

        // Only honour a screenshot request this fresh, so stale requests aren't
        // replayed when the service (re)starts.
        private const val REQUEST_FRESH_MS = 2 * 60_000L
    }
}
