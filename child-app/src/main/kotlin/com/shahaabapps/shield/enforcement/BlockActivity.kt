package com.shahaabapps.shield.enforcement

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.request.RequestRepository
import com.shahaabapps.core.model.RequestKind
import com.shahaabapps.core.model.RequestStatus
import com.shahaabapps.core.model.TimeRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Full-screen block overlay shown when a ruled app is opened and must be blocked. */
@AndroidEntryPoint
class BlockActivity : ComponentActivity() {

    @Inject lateinit var requestRepository: RequestRepository
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = intent.getStringExtra(EXTRA_APP_LABEL) ?: "This app"
        val pkg = intent.getStringExtra(EXTRA_APP_PACKAGE) ?: ""
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "It's off-limits right now."
        val variant = variantFor(reason)

        setContent {
            com.shahaabapps.core.ui.theme.LighthouseType {
                BlockContent(
                    variant = variant,
                    label = label,
                    reason = reason,
                    onAsk = { askParent(variant, pkg, label) },
                    onDismiss = ::goHome,
                )
            }
        }

        // If the parent approves while this screen is up, dismiss it and reopen the
        // app — no need for the child to back out and relaunch.
        val childId = authRepository.currentUserIdOrNull()
        if (childId != null) {
            val shownAt = System.currentTimeMillis()
            lifecycleScope.launch {
                requestRepository.observe(childId, 20).collectLatest { reqs ->
                    val approved = reqs.any { r ->
                        r.status == RequestStatus.APPROVED && r.respondedAtMillis >= shownAt &&
                            (r.appPackage == pkg && pkg.isNotBlank() || r.kind == RequestKind.EXTEND_BEDTIME)
                    }
                    if (approved) openAppAndFinish(pkg)
                }
            }
        }
    }

    /** Relaunch the just-approved app (the grant is now active) and close the block. */
    private fun openAppAndFinish(pkg: String) {
        val launch = pkg.takeIf { it.isNotBlank() }?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(launch) }
        }
        finishAndRemoveTask()
    }

    private fun askParent(variant: BlockVariant, pkg: String, label: String) {
        val childId = authRepository.currentUserIdOrNull() ?: return
        val request = askRequestFor(variant, pkg, label)
        lifecycleScope.launch { runCatching { requestRepository.create(childId, request) } }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finishAndRemoveTask()
    }

    override fun onBackPressed() = goHome()

    companion object {
        const val EXTRA_APP_LABEL = "extra_app_label"
        const val EXTRA_APP_PACKAGE = "extra_app_package"
        const val EXTRA_REASON = "extra_reason"
    }
}

// Shared with the pop-up / split-screen cover (WindowGuard) so both say the same.
internal enum class BlockVariant { TIMESUP, BEDTIME, SCHOOL, BLOCKED }

internal fun variantFor(reason: String): BlockVariant {
    val r = reason.lowercase()
    return when {
        "bedtime" in r -> BlockVariant.BEDTIME
        "school" in r -> BlockVariant.SCHOOL
        "limit" in r || "time" in r || "up" in r -> BlockVariant.TIMESUP
        else -> BlockVariant.BLOCKED
    }
}

internal fun blockTitle(variant: BlockVariant, label: String): String = when (variant) {
    BlockVariant.TIMESUP -> "Time's up for $label"
    BlockVariant.BEDTIME -> "Bedtime. Time to wind down"
    BlockVariant.SCHOOL -> "School time. Stay focused"
    BlockVariant.BLOCKED -> "$label is off-limits"
}

internal fun askLabelFor(variant: BlockVariant): String = when (variant) {
    BlockVariant.TIMESUP -> "Ask for 15 more minutes"
    BlockVariant.BEDTIME -> "Ask to stay up 15 minutes"
    BlockVariant.SCHOOL -> "Ask to use this app"
    BlockVariant.BLOCKED -> "Ask to allow it"
}

/** The request the "Ask…" button sends to the parent for this kind of block. */
internal fun askRequestFor(variant: BlockVariant, pkg: String, label: String): TimeRequest = when (variant) {
    BlockVariant.TIMESUP -> TimeRequest(kind = RequestKind.MORE_TIME, appPackage = pkg, appLabel = label, minutes = 15)
    BlockVariant.BEDTIME -> TimeRequest(kind = RequestKind.EXTEND_BEDTIME, minutes = 15)
    BlockVariant.SCHOOL -> TimeRequest(kind = RequestKind.USE_IN_SCHOOL, appPackage = pkg, appLabel = label)
    BlockVariant.BLOCKED -> TimeRequest(kind = RequestKind.UNLOCK_APP, appPackage = pkg, appLabel = label)
}

@Composable
private fun BlockContent(
    variant: BlockVariant,
    label: String,
    reason: String,
    onAsk: () -> Unit,
    onDismiss: () -> Unit,
) {
    val navy = Color(0xFF00133F)
    val gold = Color(0xFFF5B83D)
    val bedtime = variant == BlockVariant.BEDTIME
    val bg = if (bedtime) navy else Color(0xFFF4F6FB)
    val fg = if (bedtime) Color.White else navy
    val sub = if (bedtime) Color(0xFFC7D3F0) else Color(0xFF4A5878)
    val halo = if (bedtime) Color(0xFF0A2E80) else Color(0xFFDCE6FF)
    val iconTint = if (bedtime) gold else Color(0xFF012169)
    val icon: ImageVector = when (variant) {
        BlockVariant.TIMESUP -> Icons.Filled.Schedule
        BlockVariant.BEDTIME -> Icons.Filled.Bedtime
        BlockVariant.SCHOOL -> Icons.Filled.School
        BlockVariant.BLOCKED -> Icons.Filled.Block
    }
    val title = blockTitle(variant, label)
    val askLabel = askLabelFor(variant)
    var asked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(128.dp).clip(CircleShape).background(halo),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(60.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(title, color = fg, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 30.sp)
            Spacer(Modifier.height(10.dp))
            Text("$label is paused. $reason", color = sub, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
        }
        if (asked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (bedtime) Color(0xFF0A2E80) else Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text("Sent to your parent. Waiting…", color = fg, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (bedtime) gold else Color(0xFF012169))
                    .clickable { asked = true; onAsk() },
                contentAlignment = Alignment.Center,
            ) {
                Text(askLabel, color = if (bedtime) navy else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.5.dp, if (bedtime) Color(0xFF3A5AA8) else Color(0xFFD4DDF0), RoundedCornerShape(18.dp))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("Back to Shield", color = fg, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
    }
}
