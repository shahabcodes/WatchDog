package com.shahaabapps.shield.enforcement

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.shahaabapps.core.common.Diag
import com.shahaabapps.core.model.BlockAttemptReason
import com.shahaabapps.core.model.TimeRequest

/**
 * Finds apps open in a pop-up (Samsung "pop-up view" / freeform), split screen or
 * picture-in-picture. Those are extra windows that are often not focused, so the
 * window-state events and rootInActiveWindow miss them, and a full-screen
 * BlockActivity would open UNDER a floating pop-up. A blocked one is covered in
 * place with an accessibility overlay, then closed via its caption / PiP controls
 * when one can be found; otherwise the cover stays until the window goes away or
 * the app is allowed. Main thread only.
 */
internal class WindowGuard(
    private val service: AccessibilityService,
    /** What to cover [pkg]'s window with, or null when it may be used there. */
    private val coverFor: (pkg: String) -> Cover?,
    /** Shield, the launcher, keyboards and system UI are never covered. */
    private val neverCover: (pkg: String) -> Boolean,
    private val onAsk: (Cover) -> Unit,
    /** A new cover went up: the child tried to use a blocked app in this window. */
    private val onCovered: (Cover) -> Unit,
) {
    enum class Mode(val diag: String) { FULLSCREEN("large window"), SPLIT("split"), POPUP("popup"), PIP("pip") }

    /** The block shown over one window. [ask] is null where no "Ask…" is offered. */
    data class Cover(
        val pkg: String,
        val title: String,
        val message: String,
        /** For diagnostics only (never the app's name). */
        val reason: String,
        /** The app's name and why it's blocked, for the parent's (sealed) alert only. */
        val label: String,
        val kind: BlockAttemptReason,
        val dark: Boolean,
        val askLabel: String?,
        val ask: TimeRequest?,
    )

    private class AppWindow(val window: AccessibilityWindowInfo, val pkg: String, val bounds: Rect, val mode: Mode)

    private class Shown(
        val view: View,
        val params: WindowManager.LayoutParams,
        val cover: Cover,
        val mode: Mode,
        var closeAttempts: Int = 0,
        var lastCloseAt: Long = 0L,
    )

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val shown = HashMap<Int, Shown>()
    /** Per-package decisions, briefly cached: a check can query usage stats. */
    private val decisions = HashMap<String, Pair<Cover?, Long>>()

    /** Packages currently in a pop-up / split / PiP window (covered here, not by BlockActivity). */
    @Volatile var partialPackages: Set<String> = emptySet()
        private set

    /** Drops cached decisions (rules, grants or settings changed). */
    fun invalidate() = decisions.clear()

    fun scan() {
        val windows = runCatching { service.windows }.getOrNull() ?: return
        try {
            val appWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            // One app window is the normal full-screen case: skip the rest (battery).
            if (appWindows.size <= 1) {
                partialPackages = emptySet()
                removeAll()
                return
            }
            val display = displaySize()
            if (display.x <= 0 || display.y <= 0) return
            val apps = classify(appWindows, windows, display)
            // With several app windows on screen, every blocked one is covered,
            // whatever its size: a large pop-up can be almost as big as the screen
            // (and then looked "full screen"), and the full-screen block page would
            // open underneath it. Covering a truly full-screen app is harmless.
            partialPackages = apps.map { it.pkg }.toSet()

            val keep = HashSet<Int>()
            for (app in apps) {
                if (neverCover(app.pkg)) continue
                val cover = decide(app.pkg) ?: continue
                keep += app.window.id
                show(app, cover)
                maybeClose(app, windows)
            }
            shown.keys.filter { it !in keep }.forEach(::remove)
        } finally {
            windows.forEach { it.release() }
        }
    }

    /** Removes every cover (service going away / no longer supervised). */
    fun clear() {
        removeAll()
        decisions.clear()
        partialPackages = emptySet()
    }

    /**
     * Full screen: covers (nearly) the whole display. PiP: the window says so.
     * Split: partial windows that span the display's width or height, when the
     * split divider is showing or two of them sit side by side. Anything else
     * smaller than the display is a pop-up (freeform).
     */
    private fun classify(
        appWindows: List<AccessibilityWindowInfo>,
        all: List<AccessibilityWindowInfo>,
        display: Point,
    ): List<AppWindow> {
        val raw = appWindows.mapNotNull { w ->
            val pkg = packageOf(w) ?: return@mapNotNull null
            Triple(w, pkg, Rect().also(w::getBoundsInScreen))
        }
        fun spansWidth(r: Rect) = r.width() >= display.x * FULL_FRACTION_W
        fun spansHeight(r: Rect) = r.height() >= display.y * FULL_FRACTION_H
        fun isPip(w: AccessibilityWindowInfo) = w.isInPictureInPictureMode
        val partial = raw.filter { (w, _, r) -> !isPip(w) && !(spansWidth(r) && spansHeight(r)) }
        val spanning = partial.filter { (_, _, r) -> spansWidth(r) || spansHeight(r) }
        val splitOn = all.any { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER } || spanning.size >= 2
        return raw.map { (w, pkg, r) ->
            val mode = when {
                isPip(w) -> Mode.PIP
                spansWidth(r) && spansHeight(r) -> Mode.FULLSCREEN
                splitOn && (spansWidth(r) || spansHeight(r)) -> Mode.SPLIT
                else -> Mode.POPUP
            }
            AppWindow(w, pkg, r, mode)
        }
    }

    private fun decide(pkg: String): Cover? {
        val now = SystemClock.uptimeMillis()
        decisions[pkg]?.let { (cover, at) -> if (now - at < DECISION_TTL_MS) return cover }
        val cover = runCatching { coverFor(pkg) }.getOrNull()
        decisions[pkg] = cover to now
        return cover
    }

    private fun show(app: AppWindow, cover: Cover) {
        val existing = shown[app.window.id]
        if (existing != null && existing.cover == cover) {
            // Same block: follow the window if it was moved or resized.
            val p = existing.params
            if (p.x != app.bounds.left || p.y != app.bounds.top || p.width != app.bounds.width() || p.height != app.bounds.height()) {
                p.x = app.bounds.left; p.y = app.bounds.top
                p.width = app.bounds.width(); p.height = app.bounds.height()
                runCatching { windowManager.updateViewLayout(existing.view, p) }
            }
            return
        }
        if (existing != null) remove(app.window.id)
        val view = buildView(cover, app.bounds)
        val params = paramsFor(app.bounds)
        runCatching { windowManager.addView(view, params) }
            .onFailure { Diag.warn("enforce", "cover failed in ${app.mode.diag}", it); return }
        shown[app.window.id] = Shown(view, params, cover, app.mode)
        // The app itself is not named: which apps a child uses is end-to-end encrypted.
        Diag.info("enforce", "blocked in ${app.mode.diag} reason=${cover.reason}")
        onCovered(cover)
    }

    private fun remove(windowId: Int) {
        val s = shown.remove(windowId) ?: return
        runCatching { windowManager.removeView(s.view) }
    }

    private fun removeAll() = shown.keys.toList().forEach(::remove)

    /** Tries to close the window a few times (controls may appear late), then leaves the cover. */
    private fun maybeClose(app: AppWindow, all: List<AccessibilityWindowInfo>) {
        val s = shown[app.window.id] ?: return
        val now = SystemClock.uptimeMillis()
        if (s.closeAttempts >= MAX_CLOSE_ATTEMPTS || now - s.lastCloseAt < CLOSE_RETRY_MS) return
        s.closeAttempts++
        s.lastCloseAt = now
        val clicked = runCatching { close(app, all) }.getOrNull()
        when {
            clicked != null -> Diag.info("enforce", "${app.mode.diag} closed via $clicked")
            s.closeAttempts == 1 -> Diag.info("enforce", "${app.mode.diag} close button not found, keeping cover")
        }
    }

    /**
     * PiP: SystemUI's PiP controls put "Dismiss" on the window's root. Pop-up:
     * the caption's close button, either drawn in the app's window (an android /
     * Samsung view id) or in a SystemUI / Samsung window over the same area.
     * Returns what was clicked (class / view id), or null.
     */
    private fun close(app: AppWindow, all: List<AccessibilityWindowInfo>): String? {
        val area = Rect(app.bounds).apply { inset(-captionSlop, -captionSlop) }
        app.window.root?.let { root ->
            try {
                if (root.actionList.any { it.id == AccessibilityNodeInfo.ACTION_DISMISS } &&
                    root.performAction(AccessibilityNodeInfo.ACTION_DISMISS)
                ) {
                    return "dismiss ${describe(root)}"
                }
                findAndClick(root, area, inAppWindow = true)?.let { return it }
            } finally {
                root.release()
            }
        }
        for (w in all) {
            if (w.id == app.window.id) continue
            if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
                w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
            ) continue
            val b = Rect().also(w::getBoundsInScreen)
            if (!Rect.intersects(b, area)) continue
            val root = w.root ?: continue
            try {
                findAndClick(root, area, inAppWindow = false)?.let { return it }
            } finally {
                root.release()
            }
        }
        return null
    }

    /** Bounded BFS for a close control; every node but [root] is released here. */
    private fun findAndClick(root: AccessibilityNodeInfo, area: Rect, inAppWindow: Boolean): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var result: String? = null
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (result == null && visited < MAX_NODES) {
                visited++
                if (isCloseControl(node, area, inAppWindow)) result = click(node)
                if (result == null) for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            }
            if (node !== root) node.release()
        }
        return result
    }

    private fun isCloseControl(node: AccessibilityNodeInfo, area: Rect, inAppWindow: Boolean): Boolean {
        if (!node.isVisibleToUser) return false
        val id = node.viewIdResourceName.orEmpty()
        val label = (node.contentDescription ?: node.text)?.toString()?.trim()?.lowercase().orEmpty()
        val looksClose = label in CLOSE_LABELS || id.substringAfter(":id/").contains("close", ignoreCase = true)
        if (!looksClose) return false
        // Inside the app's own window only the system-drawn caption counts, never
        // the app's own "Close" buttons (those could do anything).
        val systemOwned = SYSTEM_ID_PREFIXES.any { id.startsWith(it) } ||
            (!inAppWindow && isSystemPackage(node.packageName?.toString()))
        if (!systemOwned) return false
        val b = Rect().also(node::getBoundsInScreen)
        return Rect.intersects(b, area)
    }

    /** Clicks [node], or its nearest clickable ancestor (icons are often inside the button). */
    private fun click(node: AccessibilityNodeInfo): String? {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return describe(node)
        var parent = node.parent
        repeat(3) {
            val p = parent ?: return null
            if (p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return describe(p).also { p.release() }
            }
            parent = p.parent
            p.release()
        }
        parent?.release()
        return null
    }

    private fun describe(node: AccessibilityNodeInfo): String =
        "${node.className?.toString().orEmpty().substringAfterLast('.')}/${node.viewIdResourceName ?: "-"}"

    private fun packageOf(w: AccessibilityWindowInfo): String? {
        val root = w.root ?: return null
        return try {
            root.packageName?.toString()
        } finally {
            root.release()
        }
    }

    @Suppress("DEPRECATION")
    private fun displaySize(): Point = Point().also { p ->
        runCatching {
            service.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(p)
        }
    }

    private fun paramsFor(bounds: Rect) = WindowManager.LayoutParams(
        bounds.width(),
        bounds.height(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Not focusable: touches outside the cover still reach other windows,
        // touches on it are swallowed.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds.left
        y = bounds.top
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    /** A compact BlockActivity: same colours and wording, sized to the window. */
    private fun buildView(cover: Cover, bounds: Rect): View {
        val navy = 0xFF00133F.toInt()
        val gold = 0xFFF5B83D.toInt()
        val bg = if (cover.dark) navy else 0xFFF4F6FB.toInt()
        val fg = if (cover.dark) 0xFFFFFFFF.toInt() else navy
        val sub = if (cover.dark) 0xFFC7D3F0.toInt() else 0xFF4A5878.toInt()
        // A PiP window is tiny: just the title.
        val compact = bounds.height() < dp(160) || bounds.width() < dp(180)

        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            // Swallow every touch so the app underneath can't be used.
            isClickable = true
        }
        root.addView(
            TextView(service).apply {
                text = cover.title
                setTextColor(fg)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 13f else 18f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = if (compact) 2 else 3
                ellipsize = TextUtils.TruncateAt.END
            },
        )
        if (compact) return root
        root.addView(
            TextView(service).apply {
                text = cover.message
                setTextColor(sub)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(6), 0, 0)
            },
        )
        val askLabel = cover.askLabel
        if (cover.ask != null && askLabel != null && bounds.height() >= dp(220)) {
            root.addView(
                TextView(service).apply {
                    text = askLabel
                    setTextColor(if (cover.dark) navy else 0xFFFFFFFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(dp(18), dp(12), dp(18), dp(12))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(18).toFloat()
                        setColor(if (cover.dark) gold else 0xFF012169.toInt())
                    }
                    setOnClickListener {
                        isEnabled = false
                        text = "Sent to your parent. Waiting…"
                        onAsk(cover)
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(14) },
            )
        }
        return root
    }

    private fun dp(v: Int): Int = (v * service.resources.displayMetrics.density).toInt()

    private val captionSlop get() = dp(48)

    companion object {
        /** A window this close to the display's size counts as full screen (bars aside). */
        private const val FULL_FRACTION_W = 0.9f
        private const val FULL_FRACTION_H = 0.8f
        private const val DECISION_TTL_MS = 3_000L
        private const val MAX_CLOSE_ATTEMPTS = 3
        private const val CLOSE_RETRY_MS = 4_000L
        private const val MAX_NODES = 300

        private val CLOSE_LABELS = setOf("close", "close app", "close window", "dismiss")
        /** View ids of system-drawn captions / controls (AOSP decor caption, SystemUI, Samsung). */
        private val SYSTEM_ID_PREFIXES = listOf(
            "android:id/", "com.android.internal:id/", "com.android.systemui:id/", "com.samsung.android.",
        )

        private fun isSystemPackage(pkg: String?): Boolean = pkg != null && (
            pkg == "android" || pkg == "com.android.systemui" || pkg.startsWith("com.samsung.android.")
            )

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
