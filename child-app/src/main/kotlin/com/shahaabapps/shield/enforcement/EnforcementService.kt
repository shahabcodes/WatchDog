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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.focus.FocusRepository
import com.shahaabapps.core.firebase.protection.PendingAppsRepository
import com.shahaabapps.core.firebase.protection.ProtectionRepository
import com.shahaabapps.core.firebase.request.RequestRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.firebase.screenshot.ScreenshotRepository
import com.shahaabapps.core.model.AppRule
import com.shahaabapps.core.model.BlockAttemptReason
import com.shahaabapps.core.model.FocusSettings
import com.shahaabapps.core.model.MultiWindowBlock
import com.shahaabapps.core.model.PendingApp
import com.shahaabapps.core.model.RequestKind
import com.shahaabapps.core.model.RequestStatus
import com.shahaabapps.core.model.TimeRequest
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
 * (hard block, daily limit reached, or outside allowed hours). Apps in a pop-up,
 * split screen or picture-in-picture are covered in place by [WindowGuard].
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
    @Inject lateinit var timeZoneRepository: com.shahaabapps.core.firebase.tracking.ChildTimeZoneRepository
    @Inject lateinit var screenshotRepository: ScreenshotRepository
    @Inject lateinit var requestRepository: RequestRepository
    @Inject lateinit var installGuardRepository: com.shahaabapps.core.firebase.installguard.InstallGuardRepository
    @Inject lateinit var blockAttemptRepository: com.shahaabapps.core.firebase.blockattempt.BlockAttemptRepository
    @Inject lateinit var watchShotRepository: com.shahaabapps.core.firebase.watch.WatchShotRepository

    /**
     * Temporary parent-approved exceptions, recomputed from the requests feed.
     * [appGrantUntil]: per-package "allow until" (approved unlock / more-time /
     * use-in-school). [focusGrantUntil]: suspend ALL focus-window blocking until
     * this time (approved bedtime extension / general more-time).
     */
    @Volatile private var appGrantUntil: Map<String, Long> = emptyMap()
    /** When each app's active grant was approved (its `respondedAtMillis` anchor). */
    @Volatile private var appGrantSince: Map<String, Long> = emptyMap()
    @Volatile private var focusGrantUntil: Long = 0L

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

    @Volatile private var pendingPackages: Set<String> = emptySet()

    // Only enforce while the device is supervised (linked to a parent).
    @Volatile
    private var supervised: Boolean = false

    private var lastBlockedPackage: String? = null
    private var lastBlockAtMillis: Long = 0L
    @Volatile private var lastForegroundPackage: String? = null
    private var recheckJob: kotlinx.coroutines.Job? = null

    /** Parent's "Block pop-up and split-screen view" (cached locally for offline use). */
    @Volatile private var multiWindowBlock: MultiWindowBlock = MultiWindowBlock.OFF

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastWindowScanAt = 0L
    private var windowScanPosted = false
    private val windowScanRunnable = Runnable {
        windowScanPosted = false
        scanWindows()
    }

    private val windowGuard by lazy {
        WindowGuard(
            service = this,
            coverFor = ::coverFor,
            neverCover = ::neverCover,
            onAsk = ::askFromCover,
            onCovered = { cover -> blockAttempts.record(cover.pkg, cover.label, cover.kind) },
        )
    }

    /** Keeps the child off Shield's own Settings pages while they're locked. */
    private val settingsGuard by lazy { SettingsGuard(this) { ownAppLabel } }

    /** "Supervised" apps: random screenshots while one is in use (API 30+). */
    private val watchCapturer by lazy {
        WatchCapturer(
            context = this,
            scope = serviceScope,
            repository = watchShotRepository,
            childId = authRepository::currentUserIdOrNull,
            front = { lastForegroundPackage },
            isShowing = { pkg ->
                val root = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
                (root == null || root == pkg) && supervised && blockDecision(pkg) == null
            },
            intervalFor = { pkg ->
                val rule = rulesByPackage[pkg]
                when {
                    !supervised -> 0
                    rule != null && rule.watched -> rule.watchEverySeconds
                    // A new app awaiting review is supervised until the parent decides
                    // (unless the parent chose to block new apps instead).
                    rule == null && pkg in pendingPackages && !newAppsBlocked() -> NEW_APP_WATCH_SECONDS
                    else -> 0
                }
            },
            labelFor = ::appLabelFor,
            grab = { onResult -> grabScreen(WATCH_MAX_WIDTH, WATCH_QUALITY, onResult = onResult) },
        )
    }

    /** Tells the parents (throttled, sealed) when the child tries a blocked app. */
    private val blockAttempts by lazy {
        BlockAttemptRecorder(this, blockAttemptRepository, authRepository::currentUserIdOrNull, serviceScope)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        com.shahaabapps.core.common.Diag.info("service", "enforcement (accessibility) connected")
        // Watchdog: the accessibility service is the component the OS keeps alive
        // most reliably, so use it to keep location flowing — (re)schedule the
        // backstop workers and best-effort restart the (OEM-killable) location
        // foreground service.
        runCatching { com.shahaabapps.shield.location.LocationSyncWorker.schedule(this) }
        runCatching { com.shahaabapps.shield.location.LocationFlushWorker.schedulePeriodic(this) }
        runCatching { com.shahaabapps.shield.location.GeofenceSyncService.start(this) }
        multiWindowBlock = ProtectionLocalStore.multiWindowBlock(this)
        val childId = authRepository.currentUserIdOrNull() ?: return
        serviceScope.launch {
            appRulesRepository.observeAppRules(childId).collectLatest { rules ->
                rulesByPackage = rules.associateBy { it.packageName }
                onBlockInputsChanged()
            }
        }
        serviceScope.launch {
            childRepository.observeChild(childId).collectLatest { child ->
                supervised = child?.isLinked == true
                onBlockInputsChanged()
            }
        }
        // New apps awaiting a parent's review stay blocked until a parent allows
        // them (only a parent can clear this list; the child can't).
        serviceScope.launch {
            pendingAppsRepository.observe(childId).collectLatest { list ->
                pendingPackages = list.map { it.packageName }.toSet()
            }
        }
        serviceScope.launch {
            focusRepository.observe(childId).collectLatest {
                focus = it
                onBlockInputsChanged()
            }
        }
        serviceScope.launch {
            installGuardRepository.observe(childId).collectLatest { installGuard = it }
        }
        // When the parent approves an ask, grant a time-boxed exception so the
        // app actually opens (otherwise it stays "off limits" despite approval).
        serviceScope.launch {
            requestRepository.observe(childId, 20).collectLatest {
                recomputeGrants(it)
                onBlockInputsChanged()
            }
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
        startRecheckLoop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) watchCapturer.start()
        serviceScope.launch { runCatching { TrustedClock.syncIfStale() } }
        // Schedules follow the child's home time zone (set by the parent), not the
        // phone's zone setting.
        serviceScope.launch {
            timeZoneRepository.observe(childId).collectLatest { ChildZone.setParentZone(it) }
        }
        serviceScope.launch {
            // Server-confirmed only: an error or a missing/cached doc must not turn
            // protection off (that used to drop device admin on a glitch).
            protectionRepository.observeConfirmed(childId).collectLatest { settings ->
                ProtectionLocalStore.setBlocked(this@EnforcementService, settings.uninstallBlocked)
                ProtectionLocalStore.setBlockNewApps(this@EnforcementService, settings.blockNewApps)
                ProtectionLocalStore.setNewAppsBlocked(this@EnforcementService, settings.newAppsBlocked)
                ProtectionLocalStore.setLockSettings(this@EnforcementService, settings.lockSettings)
                ProtectionLocalStore.setMultiWindowBlock(this@EnforcementService, settings.multiWindowBlock)
                multiWindowBlock = settings.multiWindowBlock
                onBlockInputsChanged()
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

    /**
     * Rebuilds the grant windows from the current requests. Windows are anchored to
     * each approval's `respondedAtMillis`, so applying is idempotent and naturally
     * stale-safe (an old approval whose window has passed grants nothing), and it
     * survives a service restart since the feed simply re-emits.
     */
    private fun recomputeGrants(requests: List<TimeRequest>) {
        val apps = HashMap<String, Long>()
        val since = HashMap<String, Long>()
        var focusUntil = 0L
        for (r in requests) {
            if (r.status != RequestStatus.APPROVED) continue
            // Approving supervised access changes the app's rule instead (Supervised).
            if (r.kind == RequestKind.SUPERVISED_ACCESS) continue
            val base = if (r.respondedAtMillis > 0) r.respondedAtMillis else r.createdAtMillis
            if (base <= 0L) continue
            // The parent can set an explicit end when approving; otherwise use the
            // default window for this kind.
            val until = if (r.grantUntilMillis > 0L) r.grantUntilMillis else base + grantDurationMs(r)
            val pkg = r.appPackage
            when {
                // Bedtime extension (and general "more time" with no app) lifts the
                // whole focus window for everyone.
                r.kind == RequestKind.EXTEND_BEDTIME || pkg.isBlank() ->
                    focusUntil = maxOf(focusUntil, until)
                // Keep the longest-running grant for the app, remembering when it
                // was approved so a later rule change can override it.
                until > (apps[pkg] ?: 0L) -> { apps[pkg] = until; since[pkg] = base }
            }
        }
        appGrantUntil = apps
        appGrantSince = since
        focusGrantUntil = focusUntil
    }

    /** How long an approved ask stays in effect, from when the parent approved it. */
    private fun grantDurationMs(r: TimeRequest): Long {
        val minutes = when (r.kind) {
            RequestKind.MORE_TIME, RequestKind.EXTEND_BEDTIME -> if (r.minutes > 0) r.minutes else 15
            RequestKind.UNLOCK_APP, RequestKind.USE_IN_SCHOOL, RequestKind.SUPERVISED_ACCESS -> if (r.minutes > 0) r.minutes else 60
        }
        return minutes * 60_000L
    }

    /** Held-for-review apps are blocked, not supervised (parent's choice). */
    private fun newAppsBlocked(): Boolean =
        ProtectionLocalStore.isBlockNewApps(this) && ProtectionLocalStore.isNewAppsBlocked(this)

    private fun handleNewApp(pkg: String) {
        val childId = authRepository.currentUserIdOrNull() ?: return
        val pm = packageManager
        // Only user-facing (launchable) apps.
        pm.getLaunchIntentForPackage(pkg) ?: return
        val label = runCatching {
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        }.getOrDefault(pkg)
        serviceScope.launch {
            // Supervised via the pending list (see pendingPackages) until a parent
            // reviews it; app rules are parent-only, so the parent's choice writes
            // the lasting rule.
            pendingPackages = pendingPackages + pkg
            pendingAppsRepository.add(childId, PendingApp(pkg, label, System.currentTimeMillis()))
            // Blocked mode: if the new app is already in front, block it now.
            if (newAppsBlocked() && lastForegroundPackage == pkg) enforce(pkg)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            // A pop-up / split / PiP window appeared, moved or closed.
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> { requestWindowScan(); return }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> Unit
            // Only subscribed while Settings is open (SettingsGuard).
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                event.packageName?.toString()?.let(settingsGuard::onContentChanged)
                return
            }
            else -> return
        }
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString()
        if (packageName !in TRANSIENT_PACKAGES) lastForegroundPackage = packageName

        // Uninstall protection runs independently of app-limit enforcement.
        val eventText = event.text.joinToString(" ").ifBlank { null }
        guardUninstall(packageName, className, eventText)
        settingsGuard.onWindowState(packageName, className, eventText)

        // Refresh which apps sit in a pop-up / split / PiP first, so an app opened
        // straight into one is covered in place, not blocked full-screen beneath it.
        scanWindows()
        enforce(packageName)
    }

    /** Scans app windows at most every [WINDOW_SCAN_MIN_MS]; a burst ends with one trailing scan. */
    private fun requestWindowScan() {
        if (windowScanPosted) return
        val wait = lastWindowScanAt + WINDOW_SCAN_MIN_MS - SystemClock.uptimeMillis()
        if (wait <= 0) {
            scanWindows()
        } else {
            windowScanPosted = true
            mainHandler.postDelayed(windowScanRunnable, wait)
        }
    }

    private fun scanWindows() {
        lastWindowScanAt = SystemClock.uptimeMillis()
        runCatching { windowGuard.scan() }
            .onFailure { com.shahaabapps.core.common.Diag.warn("enforce", "window scan failed", it) }
    }

    /** Rules, grants, focus or settings changed: re-decide covered windows now. */
    private fun onBlockInputsChanged() {
        mainHandler.post {
            windowGuard.invalidate()
            requestWindowScan()
        }
    }

    /**
     * Why an app must be blocked right now: the name to show, the child-facing
     * reason, and the [kind] the parent's blocked-app alert reports.
     */
    private data class BlockDecision(val label: String, val reason: String?, val kind: BlockAttemptReason)

    /**
     * Applies focus windows, install guard, new-app review and app rules to the app
     * in front. Runs on each window change AND every [RECHECK_MS] while an app stays
     * open, so a limit or bedtime that starts mid-use still applies.
     */
    private fun enforce(packageName: String) {
        // In a pop-up / split / PiP window the app is covered in place (WindowGuard);
        // a full-screen BlockActivity would open underneath a floating pop-up.
        if (packageName in windowGuard.partialPackages) return
        val decision = blockDecision(packageName) ?: return
        blockApp(packageName, decision.label, decision.reason, decision.kind)
    }

    /**
     * Whether [packageName] must be blocked now, and why (null = allowed). No side
     * effects, so it serves the app in front and any other visible window alike.
     */
    private fun blockDecision(packageName: String): BlockDecision? {
        if (!supervised) return null // device no longer supervised — no restrictions
        if (packageName == this.packageName) return null

        val now = TrustedClock.now()
        // Parent-approved temporary exceptions. A grant only counts while it is at
        // least as new as the app's last rule change, so re-blocking an app (which
        // bumps updatedAtMillis) immediately voids a lingering approval.
        val ruleUpdatedAt = rulesByPackage[packageName]?.updatedAtMillis ?: 0L
        val appAllowed = (appGrantUntil[packageName] ?: 0L) > now &&
            (appGrantSince[packageName] ?: 0L) >= ruleUpdatedAt
        val focusSuppressed = focusGrantUntil > now

        // Focus windows (Bedtime / School) block everything except essentials
        // and the apps the parent allowlisted for the active window. A parent
        // approval (this app, or a bedtime extension) lifts it temporarily.
        val focusReason = activeFocusReason()
        if (focusReason != null && !focusSuppressed && !appAllowed &&
            packageName !in focusExempt && packageName !in activeFocusAllowed()
        ) {
            val kind = if (variantFor(focusReason) == BlockVariant.BEDTIME) BlockAttemptReason.BEDTIME else BlockAttemptReason.SCHOOL
            return BlockDecision(appLabelFor(packageName), focusReason, kind)
        }

        // Install guard — launch-time block ONLY in blocklist mode (explicitly
        // named apps the parent wants dead). Allowlist mode controls *installs*
        // via the download quarantine and must NOT disable already-installed apps
        // (doing so would block the launcher/other apps across the device).
        if (!installGuard.allowlist && installGuard.blocks(packageName) && isSideloaded(packageName)) {
            return BlockDecision(
                appLabelFor(packageName),
                "This app isn't allowed to be installed from outside the Play Store.",
                BlockAttemptReason.INSTALL_GUARD,
            )
        }

        val rule = rulesByPackage[packageName]
        if (rule == null) {
            // A new app awaiting review, while the parent chose to block new apps:
            // blocked until they decide (an approved ask lets it through meanwhile).
            if (packageName in pendingPackages && newAppsBlocked() && !appAllowed) {
                return BlockDecision(
                    appLabelFor(packageName),
                    "This new app is waiting for your parent to allow it.",
                    BlockAttemptReason.REVIEW,
                )
            }
            return null
        }
        // A parent-approved unlock / more-time lets this app through its rule
        // (hard block or daily limit) until the grant expires.
        if (appAllowed) return null
        val usedMinutes = usageTracker.todayForegroundMinutes(packageName)
        val decision = RuleEvaluator.evaluate(
            rule,
            usedMinutes,
            java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(ChildZone.zone()))
                .apply { timeInMillis = TrustedClock.now() },
        )
        return if (decision.block) {
            BlockDecision(rule.appLabel.ifBlank { packageName }, decision.reason, decision.kind ?: BlockAttemptReason.BLOCKED)
        } else {
            null
        }
    }

    /**
     * What covers [pkg]'s pop-up / split / PiP window: its normal block (same
     * wording and "Ask…" as BlockActivity), else the parent's "Block pop-up and
     * split-screen view" when it applies now. Null = may stay open.
     */
    private fun coverFor(pkg: String): WindowGuard.Cover? {
        blockDecision(pkg)?.let { d ->
            val reason = d.reason ?: "It's off-limits right now."
            val variant = variantFor(reason)
            return WindowGuard.Cover(
                pkg = pkg,
                title = blockTitle(variant, d.label),
                message = "${d.label} is paused. $reason",
                reason = reason,
                label = d.label,
                kind = d.kind,
                dark = variant == BlockVariant.BEDTIME,
                askLabel = askLabelFor(variant),
                ask = askRequestFor(variant, pkg, d.label),
            )
        }
        if (!multiWindowBlockedNow()) return null
        // Calls (and the essentials kept during focus windows) always stay usable,
        // and only real apps count, not system components without a launcher entry.
        if (pkg in focusExempt || pkg in CALL_PACKAGES || packageManager.getLaunchIntentForPackage(pkg) == null) return null
        return WindowGuard.Cover(
            pkg = pkg,
            title = "${appLabelFor(pkg)} can't open here",
            message = MULTI_WINDOW_REASON,
            reason = "multi-window off",
            label = appLabelFor(pkg),
            kind = BlockAttemptReason.MULTI_WINDOW,
            dark = false,
            askLabel = null,
            ask = null,
        )
    }

    /** The parent's pop-up / split-screen block applies right now. */
    private fun multiWindowBlockedNow(): Boolean {
        if (!supervised) return false
        return when (multiWindowBlock) {
            MultiWindowBlock.OFF -> false
            MultiWindowBlock.ALWAYS -> true
            // A bedtime extension lifts it along with the rest of the focus window.
            MultiWindowBlock.FOCUS -> activeFocusReason() != null && focusGrantUntil <= TrustedClock.now()
        }
    }

    private fun neverCover(pkg: String): Boolean =
        pkg == packageName || pkg in TRANSIENT_PACKAGES || pkg == "android" || pkg == homePackage

    private val homePackage: String? by lazy {
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
    }

    /** "Ask…" tapped on a cover: the same request BlockActivity sends. */
    private fun askFromCover(cover: WindowGuard.Cover) {
        val request = cover.ask ?: return
        val childId = authRepository.currentUserIdOrNull() ?: return
        serviceScope.launch { runCatching { requestRepository.create(childId, request) } }
    }

    /**
     * True when [pkg] is a user-installed app that did NOT come from the Play
     * Store. System / pre-installed apps (launcher, dialer, settings…) are never
     * treated as sideloaded, so allowlist mode can't block them and brick the
     * device. A non-system app with a null/other installer (incl. adb) counts as
     * sideloaded.
     */
    private fun isSideloaded(pkg: String): Boolean = sideloadedCache.getOrPut(pkg) {
        val appInfo = runCatching { packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
        val systemFlags = android.content.pm.ApplicationInfo.FLAG_SYSTEM or
            android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        if (appInfo == null || (appInfo.flags and systemFlags) != 0) return@getOrPut false
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
        val now = ChildZone.now()
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
        val now = ChildZone.now()
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
        val recent = TrustedClock.now() - nonce < REQUEST_FRESH_MS
        if (nonce > lastScreenshotNonce && recent) {
            // A repeat request seconds after the last one shares that capture.
            val repeat = nonce - lastScreenshotNonce < SCREENSHOT_REPEAT_MS
            lastScreenshotNonce = nonce
            getSharedPreferences("screenshot_state", Context.MODE_PRIVATE)
                .edit().putLong("last_nonce", nonce).apply()
            if (repeat) {
                com.shahaabapps.core.common.Diag.info("screenshot", "repeat request shares the last capture")
                return
            }
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
        val started = android.os.SystemClock.elapsedRealtime()
        fun took() = "${(android.os.SystemClock.elapsedRealtime() - started) / 1000}s"
        serviceScope.launch { screenshotRepository.reportStatus(childId, "capturing") }
        grabScreen(SHOT_MAX_WIDTH, SHOT_QUALITY, onError = { error ->
            com.shahaabapps.core.common.Diag.warn("screenshot", "capture failed after ${took()}: $error")
            serviceScope.launch { screenshotRepository.reportStatus(childId, error) }
        }) { bytes, hidden ->
            serviceScope.launch {
                if (bytes == null) {
                    if (hidden) {
                        // A secure window (banking, DRM video): say so instead of "failed".
                        com.shahaabapps.core.common.Diag.info("screenshot", "app on screen hides it")
                        screenshotRepository.reportStatus(childId, "hidden")
                    }
                    return@launch
                }
                when (val r = screenshotRepository.uploadScreenshot(childId, bytes)) {
                    is com.shahaabapps.core.common.AppResult.Success -> {
                        com.shahaabapps.core.common.Diag.info("screenshot", "sent ${bytes.size / 1024}KB in ${took()}")
                        screenshotRepository.reportStatus(childId, "ok ${bytes.size}b")
                    }
                    is com.shahaabapps.core.common.AppResult.Failure -> {
                        com.shahaabapps.core.common.Diag.warn("screenshot", "upload failed after ${took()}: ${r.error.message}")
                        screenshotRepository.reportStatus(childId, "upload failed: ${r.error.message}")
                    }
                }
            }
        }
    }

    /**
     * One screenshot of the whole screen as JPEG. [onResult] gets the bytes, or null
     * with hidden = true when a secure window (banking, DRM video) blocks capture.
     * Other failures go to [onError] as a status line ("decode failed", …).
     */
    @android.annotation.SuppressLint("NewApi") // checked at the top
    private fun grabScreen(
        maxWidth: Int,
        quality: Int,
        onError: (String) -> Unit = {},
        onResult: (bytes: ByteArray?, hidden: Boolean) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val bytes = runCatching { result.toImageBytes(maxWidth, quality) }.getOrNull()
                        result.hardwareBuffer.close()
                        if (bytes == null) onError("decode failed") else mainHandler.post { onResult(bytes, false) }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (errorCode == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
                            mainHandler.post { onResult(null, true) }
                        } else {
                            onError("takeScreenshot failed: $errorCode")
                        }
                    }
                },
            )
        }.onFailure { onError("takeScreenshot threw: ${it.message}") }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ScreenshotResult.toImageBytes(maxW: Int, quality: Int): ByteArray? {
        val hardware = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace) ?: return null
        val software = hardware.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        val scaled = if (software.width > maxW) {
            val ratio = maxW.toFloat() / software.width
            Bitmap.createScaledBitmap(software, maxW, (software.height * ratio).toInt(), true)
        } else {
            software
        }
        // JPEG (not PNG) keeps the upload/download small (~100KB vs ~600KB) so the
        // screenshot reaches the parent quickly.
        return try {
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
        } finally {
            // Watched apps capture often: free the pixels now, not at the next GC.
            if (scaled !== software) scaled.recycle()
            software.recycle()
            hardware.recycle()
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

    private fun blockApp(packageName: String, label: String, reason: String?, kind: BlockAttemptReason) {
        // Debounce repeated events for the same app.
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && now - lastBlockAtMillis < 1500) return
        lastBlockedPackage = packageName
        lastBlockAtMillis = now
        // The app itself is not named: which apps a child uses is end-to-end encrypted.
        com.shahaabapps.core.common.Diag.info("enforce", "app blocked reason=${reason ?: "rule"}")
        blockAttempts.record(packageName, label, kind)

        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(BlockActivity.EXTRA_APP_LABEL, label)
            putExtra(BlockActivity.EXTRA_APP_PACKAGE, packageName)
            putExtra(BlockActivity.EXTRA_REASON, reason)
        }
        startActivity(intent)
    }

    /** Re-checks the app in front every [RECHECK_MS] (limits/bedtime mid-use, Settings pages). */
    private fun startRecheckLoop() {
        recheckJob?.cancel()
        recheckJob = serviceScope.launch(Dispatchers.Main) {
            while (true) {
                kotlinx.coroutines.delay(RECHECK_MS)
                runCatching { TrustedClock.syncIfStale() }
                val front = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
                    ?: lastForegroundPackage ?: continue
                runCatching {
                    scanWindows()
                    settingsGuard.recheck()
                    enforce(front)
                }
            }
        }
    }

    /**
     * When uninstall protection is on, bounce the user out of Shield's uninstall
     * dialog (and, while device admin is active, the "deactivate device admin"
     * screen) — unless a parent has unlocked a removal window with the PIN. The
     * uninstall bounce doesn't depend on device admin: after a PIN unlock that
     * didn't end in an uninstall, admin stays off until it is turned on again
     * (ProtectionReminder asks), and the uninstall dialog must stay guarded.
     */
    private fun guardUninstall(packageName: String, className: String?, text: String?) {
        if (!ProtectionLocalStore.isBlocked(this)) return
        if (ProtectionLocalStore.isUnlocked(this)) return
        if (ProtectionLocalStore.isUninstallScreen(packageName, className, text, ownAppLabel) ||
            (DeviceAdmin.isActive(this) && ProtectionLocalStore.isAdminRemovalScreen(packageName, className, text))
        ) {
            com.shahaabapps.core.common.Diag.warn("guard", "uninstall/admin-removal screen bounced pkg=$packageName class=${className.orEmpty().substringAfterLast('.')}")
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    /** Shield's own launcher label — used to scope the uninstall guard to this app. */
    private val ownAppLabel: String by lazy {
        runCatching { applicationInfo.loadLabel(packageManager).toString() }.getOrDefault("")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        com.shahaabapps.core.common.Diag.warn("service", "enforcement (accessibility) destroyed")
        recheckJob?.cancel()
        runCatching { watchCapturer.stop() }
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { windowGuard.clear() }
        runCatching { settingsGuard.clear() }
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
        /** Requests this close together share one capture (matches the server). */
        private const val SCREENSHOT_REPEAT_MS = 10_000L

        // On-demand screenshots: sharp enough to read. Watched-app shots are smaller
        // (sent often): about 50 KB each.
        private const val SHOT_MAX_WIDTH = 1000
        private const val SHOT_QUALITY = 80
        private const val WATCH_MAX_WIDTH = 720
        private const val WATCH_QUALITY = 60
        /** A new app awaiting review: one screenshot a minute. */
        private const val NEW_APP_WATCH_SECONDS = 60

        /** How often the app in front is re-checked while it stays open. */
        private const val RECHECK_MS = 20_000L
        /** Window changes are scanned at most this often (~3 a second). */
        private const val WINDOW_SCAN_MIN_MS = 333L
        private const val MULTI_WINDOW_REASON = "Pop-up and split-screen view are turned off by your parent."
        /** Phone, dialer and emergency apps are never blocked for being in a pop-up. */
        private val CALL_PACKAGES = setOf(
            "com.android.phone",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.google.android.apps.safetyhub",
            "com.samsung.android.emergency",
        )
        /** Overlays that aren't "the app in front" (shade, keyboards). */
        private val TRANSIENT_PACKAGES = setOf(
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey",
        )
    }
}
