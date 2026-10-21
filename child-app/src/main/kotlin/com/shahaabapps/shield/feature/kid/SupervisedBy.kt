package com.shahaabapps.shield.feature.kid

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.child.ChildRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The parent accounts supervising this phone, by email, so the child (and anyone
 * checking the phone) can always see exactly who is linked. Fetched from the
 * server and cached for offline display.
 */
@HiltViewModel
class SupervisedByViewModel @Inject constructor(
    private val childRepository: ChildRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val prefs = context.getSharedPreferences("supervised_by", Context.MODE_PRIVATE)

    private val _emails = MutableStateFlow(prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotBlank() })
    /** Null until known. */
    val emails: StateFlow<List<String>?> = _emails.asStateFlow()

    /** Re-reads the list (call when the linked parents change). */
    fun refresh() {
        viewModelScope.launch {
            val r = childRepository.linkedParentInfo()
            if (r is AppResult.Success) {
                val list = r.data.map { it.email.ifBlank { "A parent account (no email)" } }
                _emails.value = list
                prefs.edit().putString(KEY, list.joinToString("\n")).apply()
            }
        }
    }

    private companion object { const val KEY = "emails" }
}

/** "Supervised by" card: one line per linked parent account. */
@Composable
fun SupervisedByCard(
    linkedParentIds: List<String>,
    cardColor: Color,
    titleColor: Color,
    mutedColor: Color,
    viewModel: SupervisedByViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(linkedParentIds) { viewModel.refresh() }
    val emails by viewModel.emails.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cardColor).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("SUPERVISED BY", color = mutedColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        val list = emails
        when {
            list == null -> Text("Checking…", color = mutedColor, fontSize = 14.sp)
            list.isEmpty() -> Text("No parent is linked.", color = mutedColor, fontSize = 14.sp)
            else -> list.forEach { Text(it, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
        Text(
            "If you don't recognise an account here, tell a parent.",
            color = mutedColor,
            fontSize = 12.sp,
        )
    }
}
