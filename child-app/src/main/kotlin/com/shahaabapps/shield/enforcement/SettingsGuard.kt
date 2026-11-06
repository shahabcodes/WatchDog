package com.shahaabapps.shield.enforcement

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.shahaabapps.core.common.Diag

/**
 * While the parent's "Lock Shield's settings" is on (and no PIN unlock window is
 * open), keeps the child off the Settings pages that switch Shield off without
 * uninstalling it: its accessibility toggle and "Stop Shield?" confirm, Usage
 * Access, and its App info page.
 *
 * Pressing Home alone wasn't enough: the page stays open behind, comes straight
 * back from Recents, and was only re-checked when a new page opened or every 20 s,
 * so the switch could be flipped in between. Now, while Settings is on screen:
 * - Shield also listens for on-screen changes there (only then, for battery), and
 *   re-checks every [FAST_CHECK_MS], so the page is caught as Shield's name appears;
 * - a found page is covered at once with a full-screen overlay that takes every
 *   tap, then Home is pressed. The cover stays until the page is gone.
 * Main thread only.
 */
internal class SettingsGuard(
    private val service: AccessibilityService,
    private val ownLabel: () -> String,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(WindowManager::class.java)

    private var cover: View? = null
    private var coveredAt = 0L
    private var lastHomeAt = 0L
    /** Numbers each covered attempt, so repeated log lines aren't dropped as repeats. */
    private var attempts = 0
    private var lastCheckAt = 0L
    private var contentEvents = false
    private var fastLoop = false

    private val fastRunnable = object : Runnable {
        override fun run() {
            val settingsShowing = check("loop")
            if (settingsShowing || cover != null) {
                handler.postDelayed(this, FAST_CHECK_MS)
            } else {
                fastLoop = false
                setContentEvents(false)
            }
        }
    }

    /** A window opened or changed (any app). */
    fun onWindowState(packageName: String) {
        if (!ProtectionLocalStore.isSettingsPackage(packageName)) {
            // Left Settings (Home, another app): drop the cover once no page shows.
            if (cover != null || fastLoop) check("left")
            return
        }
        if (!active()) return
        setContentEvents(true)
        startFastLoop()
        check("open")
    }

    /** Something on screen changed (only delivered while Settings is open). */
    fun onContentChanged(packageName: String) {
        if (!ProtectionLocalStore.isSettingsPackage(packageName)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastCheckAt < CONTENT_CHECK_MIN_MS) return
        check("content")
    }

    /** The slow 20-second re-check, for anything the events missed. */
    fun recheck() {
        if (check("recheck")) startFastLoop()
    }

    /** Service going away. */
    fun clear() {
        handler.removeCallbacksAndMessages(null)
        fastLoop = false
        removeCover("service stopped")
        setContentEvents(false)
    }

    private fun active(): Boolean =
        ProtectionLocalStore.isLockSettings(service) && !ProtectionLocalStore.isUnlocked(service)

    private fun startFastLoop() {
        if (fastLoop) return
        fastLoop = true
        handler.postDelayed(fastRunnable, FAST_CHECK_MS)
    }

    /**
     * Covers a protected page if one shows, removes the cover if none does.
     * Returns whether any Settings window is on screen (keeps the fast loop going).
     */
    private fun check(via: String): Boolean {
        lastCheckAt = SystemClock.uptimeMillis()
        if (!active()) {
            removeCover("unlocked")
            return false
        }
        val found = runCatching { findProtectedPage() }.getOrNull() ?: Found(false, null)
        if (found.page != null) showCover(via, found.page) else removeCover("page gone")
        return found.settingsShowing
    }

    private class Found(val settingsShowing: Boolean, val page: String?)

    /**
     * Looks at every app window (full screen, split, pop-up) and the active window
     * (the "Stop Shield?" dialog) for a Settings page of Shield's. [Found.page] is
     * the page's class, for the log.
     */
    private fun findProtectedPage(): Found {
        val label = ownLabel()
        var settingsShowing = false
        val roots = ArrayList<AccessibilityNodeInfo>()
        runCatching { service.rootInActiveWindow }.getOrNull()?.let(roots::add)
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        try {
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                w.root?.let(roots::add)
            }
            for (root in roots) {
                val pkg = root.packageName?.toString() ?: continue
                if (!ProtectionLocalStore.isSettingsPackage(pkg)) continue
                settingsShowing = true
                val cls = root.className?.toString()
                if (ProtectionLocalStore.isProtectedSettingsScreen(cls, textOf(root), label)) {
                    return Found(true, cls.orEmpty().substringAfterLast('.').ifBlank { pkg })
                }
            }
            return Found(settingsShowing, null)
        } finally {
            roots.forEach { it.release() }
            windows.forEach { it.release() }
        }
    }

    private fun showCover(via: String, page: String) {
        val now = SystemClock.uptimeMillis()
        if (cover == null) {
            val view = buildView()
            runCatching { windowManager.addView(view, params()) }
                .onFailure { Diag.warn("guard", "settings cover failed ($page)", it) }
                .onSuccess {
                    cover = view
                    coveredAt = now
                }
            attempts++
            Diag.info("guard", "settings page covered #$attempts ($page) via=$via")
        }
        // Home takes the page away; repeat it while the page is still on screen.
        if (now - lastHomeAt >= HOME_REPEAT_MS) {
            lastHomeAt = now
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }

    private fun removeCover(why: String) {
        val view = cover ?: return
        cover = null
        runCatching { windowManager.removeView(view) }
        val secs = (SystemClock.uptimeMillis() - coveredAt) / 1000.0
        Diag.info("guard", "settings cover #$attempts removed after ${"%.1f".format(secs)}s ($why)")
    }

    /** Content-change events only while Settings is open: they're frequent everywhere else. */
    private fun setContentEvents(on: Boolean) {
        if (on == contentEvents) return
        val info = runCatching { service.serviceInfo }.getOrNull() ?: return
        info.eventTypes = if (on) {
            info.eventTypes or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        } else {
            info.eventTypes and AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED.inv()
        }
        runCatching { service.serviceInfo = info }
            .onSuccess { contentEvents = on }
    }

    /** Visible text of a window (bounded). */
    private fun textOf(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (visited < MAX_NODES) {
                visited++
                node.text?.let { sb.append(it).append('\n') }
                node.contentDescription?.let { sb.append(it).append('\n') }
                for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            }
            if (node !== root) node.release()
        }
        return sb.toString()
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Not focusable (keys and system gestures still work); touches on it are
        // swallowed, so nothing on the page underneath can be tapped.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    /** Same look as the block page. */
    private fun buildView(): View {
        val navy = 0xFF00133F.toInt()
        val gold = 0xFFF5B83D.toInt()
        val label = ownLabel().ifBlank { "Shield" }
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(navy)
            setPadding(dp(24), dp(24), dp(24), dp(24))
            isClickable = true
        }
        root.addView(
            TextView(service).apply {
                text = "$label settings are locked"
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            },
        )
        root.addView(
            TextView(service).apply {
                text = "Ask a parent to unlock them."
                setTextColor(0xFFC7D3F0.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            },
        )
        root.addView(
            TextView(service).apply {
                text = "Go to Home screen"
                setTextColor(navy)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(12), dp(20), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(gold)
                }
                setOnClickListener { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(20) },
        )
        return root
    }

    private fun dp(v: Int): Int = (v * service.resources.displayMetrics.density).toInt()

    companion object {
        /** Re-check interval while Settings is on screen or the cover is up. */
        private const val FAST_CHECK_MS = 500L
        /** On-screen changes come in bursts: check at most this often. */
        private const val CONTENT_CHECK_MIN_MS = 150L
        /** Press Home again this often while the page is still showing. */
        private const val HOME_REPEAT_MS = 2_000L
        private const val MAX_NODES = 400

        @Suppress("DEPRECATION")
        private fun AccessibilityNodeInfo.release() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) runCatching { recycle() }
        }

        @Suppress("DEPRECATION")
        private fun AccessibilityWindowInfo.release() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) runCatching { recycle() }
        }
    }
}
