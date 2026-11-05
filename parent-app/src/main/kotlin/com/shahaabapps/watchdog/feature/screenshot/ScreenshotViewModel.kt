package com.shahaabapps.watchdog.feature.screenshot

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.screenshot.ScreenshotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

/** Progress of a capture the parent just requested. */
sealed interface CaptureState {
    data object Idle : CaptureState
    data class Working(val message: String) : CaptureState
    data class Error(val message: String) : CaptureState
}

@HiltViewModel
class ScreenshotViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: ScreenshotRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    /** This parent account; local screenshots are stored per-account. */
    private val parentUid: String = authRepository.currentUserIdOrNull() ?: "unknown"

    private val _shots = MutableStateFlow<List<LocalShot>>(emptyList())
    val shots: StateFlow<List<LocalShot>> = _shots.asStateFlow()

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    // Whether the parent is waiting on a capture. A capture is matched by the
    // CHILD's own timestamps (newer than the child's last status / screenshot seen
    // before the request), never against this phone's clock: the two phones'
    // clocks can be half a minute apart, which left the spinner running after the
    // screenshot had already arrived.
    @Volatile
    private var waiting: Boolean = false

    /** The child's latest status time and screenshot time seen so far (child clock). */
    @Volatile
    private var lastStatusAt: Long = 0L
    @Volatile
    private var statusAtRequest: Long = 0L
    @Volatile
    private var newestShotAtRequest: Long = 0L

    private var timeoutJob: Job? = null

    /** When the current request was sent (this phone's clock; only for "done in Ns"). */
    private var requestedAt: Long = 0L

    // Guards against downloading the same screenshot twice while in flight.
    private val inFlight = Collections.synchronizedSet(mutableSetOf<Long>())

    init {
        // Surface the child's reported capture status (and failures) so a capture
        // that goes wrong shows an error instead of spinning forever.
        viewModelScope.launch {
            repository.observeStatus(childId).collect { status ->
                if (status == null) return@collect
                lastStatusAt = maxOf(lastStatusAt, status.atMillis)
                if (!waiting || status.atMillis <= statusAtRequest) return@collect
                val s = status.status.lowercase()
                _captureState.value = when {
                    s.startsWith("capturing") -> { armTimeout(PROGRESS_TIMEOUT_MS); CaptureState.Working("Capturing the screen…") }
                    s.startsWith("ok") -> { armTimeout(PROGRESS_TIMEOUT_MS); CaptureState.Working("Receiving…") }
                    s.startsWith("hidden") -> fail("The app on screen hides its content from screenshots (for example banking or video apps).")
                    s.contains("upload failed") -> fail("Couldn't upload the screenshot. Check the child's connection.")
                    s.contains("decode") -> fail("The device captured the screen but couldn't process it.")
                    s.contains("takescreenshot") -> fail("The device couldn't capture the screen right now.")
                    else -> _captureState.value
                }
            }
        }
        // Move any pre-namespacing downloads into this account's folder, auto-delete
        // old local screenshots, then show what remains.
        LocalScreenshotStore.migrateLegacyLayout(context, parentUid, childId)
        LocalScreenshotStore.pruneOlderThan(context, parentUid, childId, LOCAL_RETENTION_MS)
        refresh()
        // Pull any new backend screenshots down to local storage, then clear them
        // from the backend so nothing is kept server-side.
        viewModelScope.launch {
            repository.observeScreenshots(childId).collect { pending ->
                pending.forEach { shot ->
                    val millis = shot.capturedAtMillis
                    if (millis <= 0L) return@forEach
                    if (LocalScreenshotStore.hasShot(context, parentUid, childId, millis)) return@forEach
                    if (!inFlight.add(millis)) return@forEach
                    launch {
                        when (val r = repository.fetchAndClear(childId, shot)) {
                            is AppResult.Success -> {
                                LocalScreenshotStore.save(context, parentUid, childId, millis, r.data)
                                refresh()
                                // This is the capture the parent was waiting on — done.
                                if (waiting && millis > newestShotAtRequest) {
                                    com.shahaabapps.core.common.Diag.info(
                                        "screenshot",
                                        "arrived in ${(System.currentTimeMillis() - requestedAt) / 1000}s",
                                    )
                                    waiting = false
                                    timeoutJob?.cancel()
                                    _captureState.value = CaptureState.Idle
                                }
                            }
                            is AppResult.Failure -> Unit
                        }
                        inFlight.remove(millis)
                    }
                }
            }
        }
    }

    fun request() {
        // One capture at a time: a second tap while one is on its way would only
        // wake the child's phone again for the same screen.
        if (waiting) return
        requestedAt = System.currentTimeMillis()
        statusAtRequest = lastStatusAt
        newestShotAtRequest = _shots.value.maxOfOrNull { it.capturedAtMillis } ?: 0L
        waiting = true
        _captureState.value = CaptureState.Working("Waking the device…")
        viewModelScope.launch {
            when (val r = repository.requestCapture(childId)) {
                is AppResult.Failure -> fail("Couldn't send the request. Check your connection.")
                is AppResult.Success -> Unit
            }
        }
        armTimeout(CAPTURE_TIMEOUT_MS)
    }

    /**
     * Give up after a while instead of spinning forever (device offline, Shield not
     * running, accessibility off, etc.). Restarted on each sign of progress, since the
     * wake-up push alone can take half a minute to reach the child's phone.
     */
    private fun armTimeout(ms: Long) {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(ms)
            if (waiting && _captureState.value is CaptureState.Working) {
                fail("Couldn't reach the device. Make sure it's online and Shield is running.")
            }
        }
    }

    /** Dismiss a capture error so the control returns to its normal state. */
    fun clearCaptureState() {
        waiting = false
        timeoutJob?.cancel()
        _captureState.value = CaptureState.Idle
    }

    private fun fail(message: String): CaptureState.Error {
        if (waiting) {
            com.shahaabapps.core.common.Diag.warn(
                "screenshot",
                "failed after ${(System.currentTimeMillis() - requestedAt) / 1000}s: $message",
            )
        }
        waiting = false
        timeoutJob?.cancel()
        val state = CaptureState.Error(message)
        _captureState.value = state
        return state
    }

    fun delete(shot: LocalShot) {
        LocalScreenshotStore.delete(shot)
        refresh()
    }

    /** Deletes every local screenshot for this child. */
    fun deleteAll() {
        _shots.value.forEach { LocalScreenshotStore.delete(it) }
        refresh()
    }

    private fun refresh() {
        _shots.value = LocalScreenshotStore.list(context, parentUid, childId)
    }

    private companion object {
        // Keep local screenshots for two weeks, then auto-delete.
        const val LOCAL_RETENTION_MS = 14L * 24 * 60 * 60 * 1000
        const val CAPTURE_TIMEOUT_MS = 45_000L
        // After the child reports progress, how long to wait for the image itself.
        const val PROGRESS_TIMEOUT_MS = 30_000L
    }
}
