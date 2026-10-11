package com.shahaabapps.watchdog.feature.screenshot

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.screenshot.ScreenshotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

@HiltViewModel
class ScreenshotViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: ScreenshotRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    private val _shots = MutableStateFlow(LocalScreenshotStore.list(context, childId))
    val shots: StateFlow<List<LocalShot>> = _shots.asStateFlow()

    // Guards against downloading the same screenshot twice while in flight.
    private val inFlight = Collections.synchronizedSet(mutableSetOf<Long>())

    init {
        // Pull any new backend screenshots down to local storage, then clear them
        // from the backend so nothing is kept server-side.
        viewModelScope.launch {
            repository.observeScreenshots(childId).collect { pending ->
                pending.forEach { shot ->
                    val millis = shot.capturedAtMillis
                    if (millis <= 0L) return@forEach
                    if (LocalScreenshotStore.hasShot(context, childId, millis)) return@forEach
                    if (!inFlight.add(millis)) return@forEach
                    launch {
                        when (val r = repository.fetchAndClear(childId, shot)) {
                            is AppResult.Success -> {
                                LocalScreenshotStore.save(context, childId, millis, r.data)
                                refresh()
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
        viewModelScope.launch { repository.requestCapture(childId) }
    }

    fun delete(shot: LocalShot) {
        LocalScreenshotStore.delete(shot)
        refresh()
    }

    private fun refresh() {
        _shots.value = LocalScreenshotStore.list(context, childId)
    }
}
