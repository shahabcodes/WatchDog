package com.shahaabapps.core.ui.tour

import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One stop on a coach-mark tour: the control [target] (a [tourTarget] id) on
 * [screen] (a key the host's [TourNavigator] understands; null = wherever the
 * tour already is), with a short [title] and a 1–2 sentence [text].
 * [scene] names a UI state the screen opens for this step (a sheet, a mode),
 * see [TourScene].
 */
@Immutable
data class TourStep(
    val id: String,
    val screen: String?,
    val target: String,
    val title: String,
    val text: String,
    val scene: String? = null,
)

/** How a tour ended. [FinalLink] is the extra link on the last step (e.g. "Read the full guide"). */
enum class TourExit { Done, Skipped, FinalLink }

/** Lets the tour move between screens. Keys are the host's own (e.g. "overview", "settings"). */
interface TourNavigator {
    fun isOn(screen: String): Boolean
    fun open(screen: String)
}

/** Scrolls a screen page by page until [found] is true (registered by [TourScroller]). */
typealias TourSeeker = suspend (found: () -> Boolean) -> Unit

/** The tour in progress, if any. Null outside a [TourHost]'s provider (tour modifiers then do nothing). */
val LocalTour = staticCompositionLocalOf<TourController?> { null }

/**
 * Holds the ordered steps, the current one, and where every tagged control is
 * on screen. Buttons call [next] / [back] / [skip]; [TourHost] does the moving
 * (navigate, open the scene, scroll the target into view) in between.
 */
@Stable
class TourController {
    var steps by mutableStateOf<List<TourStep>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set

    /** True once the current step's target is on screen; false while moving between steps. */
    var ready by mutableStateOf(false)
        private set

    /** The scene screens should show for the current step (see [TourScene]). */
    var scene by mutableStateOf<String?>(null)
        private set

    /** Optional link on the last step's bubble (e.g. "Read the full guide"). */
    var finalLink by mutableStateOf<String?>(null)
        private set

    val active: Boolean get() = steps.isNotEmpty()
    val current: TourStep? get() = steps.getOrNull(index)

    // Screen-space bounds of every tagged control that's currently laid out.
    internal val bounds = mutableStateMapOf<String, Rect>()
    private val targets = HashMap<String, TargetEntry>()
    internal val seekers = ArrayList<TourSeeker>()

    // Full-screen layers (sheets, menus) stacked over the shell; the top one draws.
    internal val layers = mutableStateListOf<Any>()

    private data class Request(val index: Int, val direction: Int, val serial: Long)
    private var request by mutableStateOf<Request?>(null)
    private var serial = 0L
    private var onEnd: ((TourExit) -> Unit)? = null

    fun start(steps: List<TourStep>, finalLink: String? = null, onEnd: (TourExit) -> Unit = {}) {
        if (steps.isEmpty()) return
        this.steps = steps
        this.finalLink = finalLink
        this.onEnd = onEnd
        index = 0
        ready = false
        move(0, 1)
    }

    fun next() {
        if (!active) return
        if (index >= steps.lastIndex) end(TourExit.Done) else move(index + 1, 1)
    }

    /** Back a step; on the first step it leaves the tour. */
    fun back() {
        if (!active) return
        if (index == 0) end(TourExit.Skipped) else move(index - 1, -1)
    }

    fun skip() = end(TourExit.Skipped)

    fun end(exit: TourExit) {
        if (!active) return
        val callback = onEnd
        onEnd = null
        request = null
        ready = false
        scene = null
        steps = emptyList()
        callback?.invoke(exit)
    }

    private fun move(to: Int, direction: Int) {
        ready = false
        request = Request(to, direction, ++serial)
    }

    /** Runs moves as they're requested; a newer request (fast taps) cancels the one in flight. */
    internal suspend fun drive(navigator: () -> TourNavigator) {
        snapshotFlow { request }.collectLatest { req -> if (req != null) settle(req, navigator()) }
    }

    // Finds the requested step's target, or skips (in the same direction) any
    // step whose control never shows up — e.g. a card that needs data the child
    // hasn't sent yet.
    private suspend fun settle(req: Request, nav: TourNavigator) {
        var i = req.index
        var direction = req.direction
        while (active) {
            if (i < 0) { i = 0; direction = 1 }
            val step = steps.getOrNull(i) ?: run { end(TourExit.Done); return }
            index = i
            scene = step.scene
            step.screen?.let { if (!nav.isOn(it)) nav.open(it) }
            if (withTimeoutOrNull(LOCATE_TIMEOUT_MS) { locate(step.target) } == true) {
                ready = true
                return
            }
            i += direction
        }
    }

    private suspend fun locate(id: String): Boolean {
        var waited = 0L
        var revealed = false
        var sought = false
        var last: Rect? = null
        while (true) {
            val entry = targets[id]
            if (entry != null) {
                if (!revealed) {
                    revealed = true
                    entry.reveal()
                    delay(FRAME_MS)
                }
                // Wait for it to stop moving (a sheet sliding in, a list settling).
                val now = bounds[id]?.takeIf { it.width > 1f && it.height > 1f }
                if (now != null && now == last) return true
                last = now
            } else if (!sought && waited >= SEEK_AFTER_MS) {
                // Not composed: probably further down a lazy list.
                sought = true
                seekers.lastOrNull()?.invoke { id in targets }
                continue
            }
            delay(FRAME_MS)
            waited += FRAME_MS
        }
    }

    internal fun attach(id: String, entry: TargetEntry) {
        targets[id] = entry
    }

    internal fun detach(id: String, entry: TargetEntry) {
        if (targets[id] === entry) {
            targets.remove(id)
            bounds.remove(id)
        }
    }

    internal fun report(id: String, entry: TargetEntry, rect: Rect) {
        if (targets[id] === entry && bounds[id] != rect) bounds[id] = rect
    }

    private companion object {
        const val LOCATE_TIMEOUT_MS = 1_500L
        const val SEEK_AFTER_MS = 300L
        const val FRAME_MS = 32L
    }
}

internal class TargetEntry(val reveal: suspend () -> Unit)

/** Top-left of this view's window on screen, so bounds from any window (sheet, menu) line up. */
internal fun View.windowOrigin(): Offset {
    val loc = IntArray(2)
    rootView.getLocationOnScreen(loc)
    return Offset(loc[0].toFloat(), loc[1].toFloat())
}

/**
 * Marks a control the tour can spotlight: records its on-screen bounds and lets
 * the tour scroll it into view. Does nothing outside a [LocalTour] provider.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tourTarget(id: String): Modifier = composed {
    val tour = LocalTour.current ?: return@composed Modifier
    val requester = remember { BringIntoViewRequester() }
    val view = LocalView.current
    val entry = remember(id) { TargetEntry { requester.bringIntoView() } }
    DisposableEffect(tour, id, entry) {
        tour.attach(id, entry)
        onDispose { tour.detach(id, entry) }
    }
    Modifier
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { tour.report(id, entry, it.boundsInWindow().translate(view.windowOrigin())) }
}

/**
 * Opens a piece of UI (a sheet, a map mode) while the tour's current step asks
 * for [name], and closes it again when the tour moves on — but only if the
 * tour opened it.
 */
@Composable
fun TourScene(name: String, show: (Boolean) -> Unit) {
    val tour = LocalTour.current ?: return
    val wanted = tour.active && tour.scene == name
    val currentShow by rememberUpdatedState(show)
    val opened = remember { booleanArrayOf(false) }
    LaunchedEffect(wanted) {
        if (wanted) {
            opened[0] = true
            currentShow(true)
        } else if (opened[0]) {
            opened[0] = false
            currentShow(false)
        }
    }
}

/** Lets the tour page through a lazy list to find a target that isn't composed yet. */
@Composable
fun TourScroller(state: LazyListState) {
    val tour = LocalTour.current ?: return
    DisposableEffect(tour, state) {
        val seeker: TourSeeker = { found ->
            // Further down first, then from the top.
            pageThrough(state, found)
            if (!found()) {
                state.scrollToItem(0)
                delay(48)
                pageThrough(state, found)
            }
        }
        tour.seekers += seeker
        onDispose { tour.seekers -= seeker }
    }
}

private suspend fun pageThrough(state: LazyListState, found: () -> Boolean) {
    while (!found() && state.canScrollForward) {
        state.scrollBy(state.layoutInfo.viewportSize.height * 0.75f)
        delay(48)
    }
}
