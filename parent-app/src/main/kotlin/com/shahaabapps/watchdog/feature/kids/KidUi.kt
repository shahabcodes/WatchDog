package com.shahaabapps.watchdog.feature.kids

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shahaabapps.core.ui.components.AppDialog
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

/** The phone chips over the bottom bar, for a child with more than one phone. */
@Composable
fun PhoneSwitcherBar(
    phones: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.card)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        phones.forEach { (id, label) ->
            val selected = id == selectedId
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) AppTheme.colors.accent else AppTheme.colors.groupedBackground,
                border = if (selected) null else BorderStroke(1.dp, AppTheme.colors.separator),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onSelect(id) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = if (selected) androidx.compose.ui.graphics.Color.White else AppTheme.colors.secondaryLabel,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(text: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) AppTheme.colors.accent else AppTheme.colors.secondaryLabel,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
        }
    }
}

private fun KidView.describe(): String =
    if (merged) "${phones.size} phones" else phoneLabel(phones.first(), phones)

/**
 * "Merge phones": pick the children that are really one child, whose rules to
 * keep, and the name. [preview] counts what the others lose (null while counting).
 */
@Composable
fun MergePhonesDialog(
    kids: List<KidView>,
    preview: MergePreview?,
    onPreview: (keepFrom: String, others: List<String>) -> Unit,
    onMerge: (keepFrom: String, others: List<String>, name: String) -> Unit,
    onDismiss: () -> Unit,
    preselected: Set<String> = emptySet(),
) {
    var picked by remember { mutableStateOf(preselected) }
    var keep by remember { mutableStateOf<String?>(preselected.firstOrNull()) }
    var name by remember { mutableStateOf(kids.firstOrNull { it.key in preselected }?.name.orEmpty()) }
    val chosen = kids.filter { it.key in picked }
    val keepKid = chosen.firstOrNull { it.key == keep } ?: chosen.firstOrNull()
    val ready = chosen.size >= 2 && keepKid != null && name.isNotBlank()
    val others = chosen.filter { it.key != keepKid?.key }.flatMap { it.phoneIds }
    LaunchedEffect(keepKid?.key, others) {
        if (keepKid != null && others.isNotEmpty()) onPreview(keepKid.phoneIds.first(), others)
    }

    AppDialog(
        onDismiss = onDismiss,
        title = "Merge phones",
        confirmText = "Merge",
        confirmEnabled = ready,
        onConfirm = { if (keepKid != null) onMerge(keepKid.phoneIds.first(), others, name.trim()) },
    ) {
        Column(
            modifier = Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "Pick the phones that belong to the same child. They show as one child, and the rules you set reach every phone. Time limits count on each phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
            )
            Text("Phones", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
            kids.forEach { kid ->
                ChoiceRow(kid.name, kid.describe(), kid.key in picked) {
                    picked = if (kid.key in picked) picked - kid.key else picked + kid.key
                    if (keep == null || keep !in picked) keep = picked.firstOrNull()
                    if (name.isBlank()) name = kid.name
                }
            }
            if (chosen.size >= 2) {
                Text("Keep the rules from", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
                chosen.forEach { kid ->
                    ChoiceRow(kid.name, kid.describe(), kid.key == keepKid?.key) {
                        keep = kid.key
                        name = kid.name
                    }
                }
                val p = preview
                Text(
                    when {
                        p == null -> "Checking what changes…"
                        p.rulesChanged == 0 && p.placesChanged == 0 -> "The other phones' rules and places stay as they are."
                        else -> "On the other phones, ${count(p.rulesChanged, "app rule")} and ${count(p.placesChanged, "place")} " +
                            "will be replaced to match. Bedtime, school and app installs are copied too."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (p != null && (p.rulesChanged > 0 || p.placesChanged > 0)) AppTheme.colors.destructive else AppTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text("Name", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun count(n: Int, what: String) = if (n == 1) "1 $what" else "$n ${what}s"

/** After pairing: is the new phone a new child, or another phone of one already here? */
@Composable
fun AnotherPhoneDialog(
    kids: List<KidView>,
    onPick: (KidView) -> Unit,
    onNewChild: () -> Unit,
) {
    var picked by remember { mutableStateOf<String?>(null) }
    AppDialog(
        onDismiss = onNewChild,
        title = "Whose phone is this?",
        icon = Icons.Filled.PhoneAndroid,
        confirmText = "Continue",
        onConfirm = {
            val kid = kids.firstOrNull { it.key == picked }
            if (kid != null) onPick(kid) else onNewChild()
        },
        dismissText = null,
        dismissOnOutsideTouch = false,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "If it's another phone of a child you already supervise, it gets that child's rules and shows under them.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
            )
            ChoiceRow("A new child", null, picked == null) { picked = null }
            kids.forEach { kid ->
                ChoiceRow("Another phone for ${kid.name}", kid.describe(), picked == kid.key) { picked = kid.key }
            }
        }
    }
}

/** Progress and outcome of a merge. */
@Composable
fun MergeStateDialog(state: com.shahaabapps.watchdog.feature.dashboard.MergeState, onDismiss: () -> Unit) {
    when (state) {
        com.shahaabapps.watchdog.feature.dashboard.MergeState.Working -> AppDialog(
            onDismiss = { },
            title = "Merging…",
            dismissText = null,
            dismissOnOutsideTouch = false,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.shahaabapps.core.ui.components.AppLoader(size = 22.dp)
                Text("  Copying the rules to every phone.")
            }
        }
        is com.shahaabapps.watchdog.feature.dashboard.MergeState.Done -> AppDialog(
            onDismiss = onDismiss,
            title = "Phones merged",
            icon = Icons.Filled.CheckCircle,
            dismissText = "Done",
        ) {
            Text(
                "${state.name} now shows as one child. Rules you set reach every phone, and you can switch phones at the bottom of the screen.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is com.shahaabapps.watchdog.feature.dashboard.MergeState.Error -> AppDialog(
            onDismiss = onDismiss,
            title = "Couldn't merge",
            dismissText = "OK",
        ) {
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
        }
        com.shahaabapps.watchdog.feature.dashboard.MergeState.Idle -> Unit
    }
}

/** A heading for a merged child's phones on the Children screen. */
@Composable
fun PhoneLine(label: String, online: Boolean, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = AppTheme.colors.secondaryLabel, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        Text(
            if (online) "  · Online" else "  · Offline",
            style = MaterialTheme.typography.bodySmall,
            color = if (online) AppTheme.colors.success else AppTheme.colors.secondaryLabel,
        )
    }
}
