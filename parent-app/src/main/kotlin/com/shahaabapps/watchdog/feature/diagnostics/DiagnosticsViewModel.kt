package com.shahaabapps.watchdog.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.callSafely
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.diagnostics.DiagLevel
import com.shahaabapps.core.firebase.diagnostics.DiagnosticsLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Whose diagnostics are shown: this phone's own log, or an account's from the server. */
data class DiagSource(val key: String, val label: String, val uid: String?)

/** One row: when, how bad, which phone/app, and what. */
data class DiagRow(
    val atMillis: Long,
    val level: DiagLevel,
    val where: String,
    val tag: String,
    val message: String,
)

data class DiagnosticsUiState(
    val enabled: Boolean,
    val sources: List<DiagSource> = emptyList(),
    val selected: String = THIS_PHONE,
    val rows: List<DiagRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

const val THIS_PHONE = "this"

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val logger: DiagnosticsLogger,
    private val authRepository: AuthRepository,
    private val childRepository: ChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState(enabled = logger.enabled, rows = localRows()))
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val me = authRepository.currentUserIdOrNull() ?: return@launch
            val children = childRepository.observeLinkedChildren(me).first()
            _state.value = _state.value.copy(
                sources = listOf(
                    DiagSource(THIS_PHONE, "This phone", null),
                    DiagSource("me", "My account (all my phones)", me),
                ) + children.map { DiagSource(it.childId, "${it.profile.name.ifBlank { "Child" }}'s phone", it.childId) },
            )
        }
    }

    private fun localRows() = logger.recent().asReversed().map {
        DiagRow(it.atMillis, it.level, "this phone", it.tag, it.message)
    }

    fun select(key: String) {
        _state.value = _state.value.copy(selected = key, error = null)
        refresh()
    }

    fun refresh() {
        val s = _state.value
        val source = s.sources.firstOrNull { it.key == s.selected }
        if (source?.uid == null) {
            _state.value = s.copy(enabled = logger.enabled, rows = localRows(), loading = false)
            return
        }
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = runCatching {
                val since = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                val data = FirebaseFunctions.getInstance().getHttpsCallable("readDiagnostics")
                    .callSafely(name = "readDiagnostics", data = hashMapOf("uid" to source.uid, "sinceMillis" to since, "limit" to 500))
                    .getData() as? Map<*, *>
                (data?.get("events") as? List<*>).orEmpty().mapNotNull { e ->
                    val m = e as? Map<*, *> ?: return@mapNotNull null
                    val app = (m["app"] as? String).orEmpty().substringAfterLast('.')
                    DiagRow(
                        atMillis = (m["atMillis"] as? Number)?.toLong() ?: 0L,
                        level = runCatching { DiagLevel.valueOf(m["level"] as String) }.getOrDefault(DiagLevel.INFO),
                        where = if (app == "server") "server ${(m["model"] as? String).orEmpty()}"
                        else "$app ${(m["version"] as? String).orEmpty()} ${(m["model"] as? String).orEmpty()}",
                        tag = (m["tag"] as? String).orEmpty(),
                        message = (m["message"] as? String).orEmpty() +
                            ((m["exception"] as? String)?.takeIf { it.isNotBlank() }?.let { "  !! $it" } ?: ""),
                    )
                }
            }
            _state.value = _state.value.copy(
                loading = false,
                rows = result.getOrDefault(emptyList()),
                error = result.exceptionOrNull()?.let { it.message ?: "Couldn't load diagnostics." },
            )
        }
    }

    fun setEnabled(value: Boolean) {
        logger.setEnabled(value)
        _state.value = _state.value.copy(enabled = value)
    }

    /** The list on screen as text, with full date and time, for Copy / Share. */
    fun exportText(): String {
        val s = _state.value
        val label = s.sources.firstOrNull { it.key == s.selected }?.label ?: "This phone"
        val clock = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
        return buildString {
            appendLine("Watch Dog diagnostics: $label")
            appendLine("exported ${clock.format(Date())}, ${s.rows.size} events (newest first)")
            appendLine("----")
            s.rows.forEach { appendLine("${clock.format(Date(it.atMillis))}  ${it.level}  ${it.where}  [${it.tag}] ${it.message}") }
        }
    }
}
